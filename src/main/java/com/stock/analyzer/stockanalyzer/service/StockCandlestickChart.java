package com.stock.analyzer.stockanalyzer.service;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.stage.WindowEvent;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// 支持多任务的股票K线图生成器（修复渲染检测问题）
public class StockCandlestickChart extends Application {

    // 任务队列
    private static final BlockingQueue<ChartTask> taskQueue = new LinkedBlockingQueue<>();
    private static Stage mainStage;
    private static boolean isAppRunning = false;
    private static final Object lock = new Object(); // 用于同步启动

    // 图表组件
    private XYChart<String, Number> chart;
    private VBox chartContainer;
    private Scene scene;

    // 渲染状态
    private boolean isChartRendered = false;
    private AtomicInteger layoutCount = new AtomicInteger(0);
    private static final int REQUIRED_LAYOUTS = 10; // 增加布局脉冲计数要求
    private int expectedCandleCount = 0; // 预期的蜡烛柱数量
    private long lastLayoutTime = 0; // 上次布局时间
    private static final long MIN_RENDER_TIME = 50; // 最小渲染时间（毫秒）
    // 任务统计
    private static AtomicInteger totalTasks = new AtomicInteger(0);
    private static AtomicInteger completedTasks = new AtomicInteger(0);
    // 当前任务
    private ChartTask currentTask;

    // 股票数据类（公开，可从外部访问）
    public static class StockData {
        private final String stockCode;
        private final String stockName;
        private final String date;  // 日期
        private final double open;  // 开盘价
        private final double close; // 收盘价
        private final double high;  // 最高价
        private final double low;   // 最低价

        public StockData(String stockCode, String stockName, String date, double open, double close, double high, double low) {
            this.stockCode = stockCode;
            this.stockName = stockName;
            this.date = date;
            this.open = open;
            this.close = close;
            this.high = high;
            this.low = low;
        }

        public StockData(String date, double open, double close, double high, double low) {
            this.stockCode = "";
            this.stockName = "";
            this.date = date;
            this.open = open;
            this.close = close;
            this.high = high;
            this.low = low;
        }


        public String getStockCode() {
            return stockCode;
        }

        public String getDate() {
            return date;
        }

        public double getOpen() {
            return open;
        }

        public double getClose() {
            return close;
        }

        public double getHigh() {
            return high;
        }

        public double getLow() {
            return low;
        }

        // 判断当天是上涨还是下跌
        public boolean isUp() {
            return close > open;
        }

        public String getStockName() {
            return stockName;
        }
    }

    // 图表任务类（带完成标记）
    static class ChartTask {
        private final List<StockData> stockData;
        private final File saveFile;
        private final Consumer<File> successCallback;
        private final Consumer<Exception> errorCallback;
        private final CountDownLatch completionLatch = new CountDownLatch(1); // 任务完成标记

        public ChartTask(List<StockData> stockData, File saveFile,
                         Consumer<File> successCallback,
                         Consumer<Exception> errorCallback) {
            this.stockData = stockData;
            this.saveFile = saveFile;
            this.successCallback = successCallback;
            this.errorCallback = errorCallback;
        }

        public List<StockData> getStockData() {
            return stockData;
        }

        public File getSaveFile() {
            return saveFile;
        }

        // 标记任务完成
        public void markCompleted() {
            completionLatch.countDown();
        }

        // 等待任务完成
        public void awaitCompletion() throws InterruptedException {
            completionLatch.await();
        }

        public void complete(File file) {
            if (successCallback != null) {
                successCallback.accept(file);
            }
            completedTasks.incrementAndGet(); // 增加完成任务计数
            markCompleted(); // 完成后标记
            System.out.println("任务完成: " + completedTasks.get() + "/" + totalTasks.get());
            checkAllTasksCompleted();
        }

        public void fail(Exception ex) {
            if (errorCallback != null) {
                errorCallback.accept(ex);
            } else {
                System.err.println("处理任务时出错: " + ex.getMessage());
                ex.printStackTrace();
            }
            completedTasks.incrementAndGet(); // 增加完成任务计数
            markCompleted(); // 失败也标记完成
            System.out.println("任务失败: " + completedTasks.get() + "/" + totalTasks.get());
            checkAllTasksCompleted();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        mainStage = primaryStage;
        mainStage.setTitle("股票K线图生成器");
        isAppRunning = true;

        // 唤醒等待的线程
        synchronized (lock) {
            lock.notifyAll();
        }

        // 创建图表容器
        chartContainer = new VBox(10);
        chartContainer.setPadding(new Insets(10));
        scene = new Scene(chartContainer, 800, 600);
        primaryStage.setScene(scene);

        // 监听场景的布局脉冲
        scene.addPostLayoutPulseListener(() -> {
            if (chart != null) {
                int count = layoutCount.incrementAndGet();
                // 检查渲染完成状态
                checkRenderCompletion();
            }
        });
        // 检查渲染完成状态
        checkRenderCompletion();
        // 窗口关闭时的处理
        primaryStage.setOnCloseRequest(this::handleWindowClose);

        primaryStage.show();

        // 启动任务处理线程（确保串行执行）
        startTaskProcessor();
    }

    private void startTaskProcessor() {
        Thread taskThread = new Thread(() -> {
            try {
                while (isAppRunning) {
                    // 从队列中获取任务
                    currentTask = taskQueue.take();
                    System.out.println("开始处理任务: " + currentTask.getSaveFile().getName());

                    // 在JavaFX线程中处理任务
                    Platform.runLater(() -> processTask(currentTask));

                    // 等待当前任务完全完成（保存成功或失败）
                    currentTask.awaitCompletion();
                    System.out.println("任务已完成: " + currentTask.getSaveFile().getName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("任务处理线程被中断: " + e.getMessage());
            }
        });

        taskThread.setDaemon(true);
        taskThread.start();
    }

    private void processTask(ChartTask task) {
        try {
            System.out.println("正在渲染图表: " + task.getSaveFile().getName());

            // 重置渲染状态
            isChartRendered = false;
            layoutCount.set(0);
            lastLayoutTime = System.currentTimeMillis();

            // 清除现有图表
            chartContainer.getChildren().clear();

            // 创建新图表
            chart = createCandlestickChart(task.getStockData());
            chartContainer.getChildren().add(chart);

            // 调整窗口大小
            mainStage.sizeToScene();
        } catch (Exception e) {
            System.err.println("处理任务时出错: " + e.getMessage());
            task.fail(new Exception("渲染图表时出错", e));
        }
    }

    // 检查渲染是否完成（改进版，不使用getPlotChildren）
    private synchronized void checkRenderCompletion() {
        if (chart == null || currentTask == null) return;

        // 通过遍历图表容器查找蜡烛节点
        int renderedCandleCount = countCandleNodes(chartContainer);

        long currentTime = System.currentTimeMillis();
        boolean hasMinimumRenderTime = (currentTime - lastLayoutTime) >= MIN_RENDER_TIME;
        // 条件1: 达到足够的布局脉冲次数
        // 条件2: 渲染的蜡烛柱数量与预期一致或接近（允许10%误差）
        // 条件3: 至少经过了最小渲染时间
        boolean isRenderComplete = (layoutCount.get() >= REQUIRED_LAYOUTS) &&
                (renderedCandleCount >= expectedCandleCount * 0.9) && hasMinimumRenderTime;

        if (isRenderComplete) {
            Platform.runLater(() -> {
                try {
                    File savedFile = saveChartToFile(currentTask.getSaveFile());
                    currentTask.complete(savedFile);
                    System.out.println("任务保存成功: " + savedFile.getName());
                    layoutCount.set(0);
                } catch (Exception e) {
                    currentTask.fail(new Exception("保存图表时出错", e));
                }
            });
        }
    }

    // 递归计算蜡烛节点数量
    private int countCandleNodes(Node parent) {
        int count = 0;

        if (parent instanceof Group && parent.getUserData() instanceof StockData) {
            return 1; // 找到一个蜡烛节点
        }

        if (parent instanceof javafx.scene.Parent) {
            javafx.scene.Parent p = (javafx.scene.Parent) parent;
            for (Node child : p.getChildrenUnmodifiable()) {
                count += countCandleNodes(child);
            }
        }

        return count;
    }

    // 保存图表到文件（添加渲染延迟）
    private File saveChartToFile(File file) throws IOException {
        // 创建保存目录（如果不存在）
        File parentDir = file.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("无法创建目录: " + parentDir.getAbsolutePath());
        }

        // 强制延迟，确保渲染完成（特别是对于大数据集）
        try {
            Thread.sleep(500); // 等待500毫秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 创建快照
        WritableImage image = chart.snapshot(new SnapshotParameters(), null);

        // 保存图片
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);

        return file;
    }

    private void handleWindowClose(WindowEvent event) {
        System.out.println("窗口关闭，清理资源...");
        isAppRunning = false;
    }

    /**
     * 生成并保存K线图（带成功和错误回调）
     *
     * @param stockData       股票数据
     * @param savePath        保存路径
     * @param successCallback 成功回调
     * @param errorCallback   错误回调
     */
    public static void generateChart(List<StockData> stockData, String savePath,
                                     Consumer<File> successCallback,
                                     Consumer<Exception> errorCallback) {
        if (stockData == null || stockData.isEmpty()) {
            if (errorCallback != null) {
                errorCallback.accept(new IllegalArgumentException("股票数据不能为空"));
            }
            return;
        }
        // 增加总任务计数
        int i = totalTasks.incrementAndGet();
        System.out.println("generateChart: " + i);
        // 创建保存文件
        File saveFile;
        try {
            if (savePath == null || savePath.isEmpty()) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                saveFile = new File(System.getProperty("user.home"), "stock_chart_" + timestamp + ".png");
            } else {
                saveFile = new File(savePath);
            }
        } catch (Exception e) {
            if (errorCallback != null) {
                errorCallback.accept(new Exception("无效的保存路径", e));
            }
            // 任务失败也需要增加完成计数
            completedTasks.incrementAndGet();
            checkAllTasksCompleted();
            return;
        }

        // 创建任务
        ChartTask task = new ChartTask(stockData, saveFile, successCallback, errorCallback);

        // 提交任务
        submitTask(task);
    }

    /**
     * 生成并保存K线图（仅带成功回调）
     *
     * @param stockData       股票数据
     * @param savePath        保存路径
     * @param successCallback 成功回调
     */
    public static void generateChart(List<StockData> stockData, String savePath,
                                     Consumer<File> successCallback) {
        generateChart(stockData, savePath, successCallback, null);
    }

    /**
     * 生成并保存K线图（无回调）
     *
     * @param stockData 股票数据
     * @param savePath  保存路径
     */
    public static void generateChart(List<StockData> stockData, String savePath) {
        generateChart(stockData, savePath, null, null);
    }

    private static void submitTask(ChartTask task) {
        try {
            // 如果应用尚未启动，则启动它
            if (!isAppRunning) {
                synchronized (lock) {
                    if (!isAppRunning) {
                        // 启动JavaFX应用
                        new Thread(() -> Application.launch(StockCandlestickChart.class)).start();

                        // 等待应用启动完成
                        lock.wait(5000); // 最多等待5秒

                        if (!isAppRunning) {
                            throw new Exception("JavaFX应用启动超时");
                        }
                    }
                }
            }

            // 将任务添加到队列
            taskQueue.put(task);
        } catch (Exception e) {
            task.fail(new Exception("提交任务时出错", e));
        }
    }

    // 创建改进版K线图
    private javafx.scene.chart.XYChart<String, Number> createCandlestickChart(List<StockData> stockDataList) {
        // 记录预期的蜡烛柱数量
        expectedCandleCount = stockDataList.size();
        StockData stockData = stockDataList.get(0);
        // 定义坐标轴
        CategoryAxis xAxis = new CategoryAxis();
        String title = String.format("%s-%s",stockData.getStockName(), stockData.getStockCode());
        xAxis.setLabel("日期");

        // 计算价格范围
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (StockData data : stockDataList) {
            minPrice = Math.min(minPrice, data.getLow());
            maxPrice = Math.max(maxPrice, data.getHigh());
        }

        // 添加一些边距
        double padding = (maxPrice - minPrice) * 0.1;
        minPrice -= padding;
        maxPrice += padding;

        NumberAxis yAxis = new NumberAxis(minPrice, maxPrice, (maxPrice - minPrice) / 10);
        yAxis.setLabel("价格");

        // 创建自定义图表
        XYChart<String, Number> chart = new LineChart<>(xAxis, yAxis) {
            @Override
            protected void layoutPlotChildren() {
                super.layoutPlotChildren();

                Platform.runLater(() -> {
                    // 清理旧的蜡烛图
                    List<Node> nodesToRemove = new ArrayList<>();
                    for (Node n : getPlotChildren()) {
                        if (n instanceof Group && n.getUserData() instanceof StockData) {
                            nodesToRemove.add(n);
                        }
                    }
                    getPlotChildren().removeAll(nodesToRemove);

                    // 绘制新的蜡烛图
                    for (Series<String, Number> series : getData()) {
                        for (XYChart.Data<String, Number> item : series.getData()) {
                            StockData stockData = (StockData) item.getExtraValue();
                            if (stockData != null) {
                                getPlotChildren().add(createCandlestickNode(stockData, xAxis, yAxis));
                            }
                        }
                    }
                });
            }
        };

        chart.setTitle(title);
        chart.setLegendVisible(false);

        // 创建系列
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        // 添加数据点
        for (StockData data : stockDataList) {
            XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(data.getDate(), data.getClose());
            dataPoint.setExtraValue(data); // 存储额外的股票数据
            series.getData().add(dataPoint);
        }

        chart.getData().add(series);

        return chart;
    }

    // 创建K线图中的单个蜡烛节点
    private Node createCandlestickNode(StockData data, CategoryAxis xAxis, NumberAxis yAxis) {
        Group candleGroup = new Group();

        // 获取数据对应的坐标位置
        double xCoord = xAxis.getDisplayPosition(data.getDate());
        double openY = yAxis.getDisplayPosition(data.getOpen());
        double closeY = yAxis.getDisplayPosition(data.getClose());
        double highY = yAxis.getDisplayPosition(data.getHigh());
        double lowY = yAxis.getDisplayPosition(data.getLow());

        // 改进的蜡烛宽度计算方式
        double availableWidth = xAxis.getWidth();
        int dataCount = xAxis.getCategories().size();
        double maxCandleWidth = 20;

        // 确保有数据点
        if (dataCount > 0) {
            double calculatedWidth = (availableWidth / dataCount) * 0.7; // 使用70%的可用宽度
            double candleWidth = Math.min(maxCandleWidth, calculatedWidth);

            // 创建实体矩形
            double bodyHeight = Math.abs(closeY - openY);
            double bodyY = Math.min(openY, closeY);

            Rectangle body = new Rectangle(xCoord - candleWidth / 2, bodyY, candleWidth, bodyHeight);
            body.setStroke(Color.BLACK);

            // 根据涨跌设置颜色（上涨为红色，下跌为绿色）
            if (data.isUp()) {
                body.setFill(Color.RED);
            } else {
                body.setFill(Color.GREEN);
            }

            // 创建上下影线
            Line upperWick = new Line(xCoord, highY, xCoord, Math.min(openY, closeY));
            upperWick.setStroke(Color.BLACK);

            Line lowerWick = new Line(xCoord, Math.max(openY, closeY), xCoord, lowY);
            lowerWick.setStroke(Color.BLACK);

            candleGroup.getChildren().addAll(body, upperWick, lowerWick);
            candleGroup.setUserData(data); // 存储股票数据用于悬停提示
        }

        return candleGroup;
    }

    // 检查所有任务是否完成
    private static boolean checkAllTasksCompleted() {
        int total = totalTasks.get();
        int completed = completedTasks.get();

        System.out.println("任务进度: " + completed + "/" + total);

        if (total > 0 && completed == total) {
            System.out.println("所有任务已完成，准备关闭窗口...");

            // 延迟关闭，确保所有资源释放
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // 等待1秒
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                Platform.runLater(() -> {
                    if (mainStage != null && mainStage.isShowing()) {
                        mainStage.close();
                        System.out.println("窗口已关闭");
                    }
                });
            }).start();
            return true;

        }
        return false;
    }
    public static boolean checkCompleted(){
        int total = totalTasks.get();
        int completed = completedTasks.get();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (total > 0 && completed == total) {
            return true;
        }
        return false;
    }

    public static void main(String[] args) {
        // 示例：生成多个图表
        List<List<StockData>> allStockData = new ArrayList<>();

        // 添加第一组数据（小数据集）
        List<StockData> data1 = new ArrayList<>();
        data1.add(new StockData("2023-05-01", 150.25, 152.75, 153.50, 149.80));
        data1.add(new StockData("2023-05-02", 152.70, 154.30, 155.00, 151.90));
        data1.add(new StockData("2023-05-03", 154.25, 153.10, 154.90, 152.50));
        allStockData.add(data1);

        // 添加第二组数据（中等数据集）
        List<StockData> data2 = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            double base = 150 + i;
            double open = base + Math.random() * 5;
            double close = open + (Math.random() * 10 - 5);
            double high = Math.max(open, close) + Math.random() * 3;
            double low = Math.min(open, close) - Math.random() * 3;
            data2.add(new StockData("2023-05-" + String.format("%02d", i), open, close, high, low));
        }
        allStockData.add(data2);

        // 添加第三组数据（大数据集）
        List<StockData> data3 = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            double base = 160 + i * 0.5;
            double open = base + Math.random() * 4;
            double close = open + (Math.random() * 8 - 4);
            double high = Math.max(open, close) + Math.random() * 2;
            double low = Math.min(open, close) - Math.random() * 2;
            data3.add(new StockData("2023-" + (i / 30 + 4) + "-" + String.format("%02d", i % 30 + 1), open, close, high, low));
        }
        allStockData.add(data3);

        // 循环生成多个图表
        for (int i = 0; i < allStockData.size(); i++) {
            final int index = i;
            String savePath = "C:/Users/YourName/Documents/stock_chart_" + index + ".png";

            System.out.println("提交图表生成任务 " + index + " (数据点数: " + allStockData.get(i).size() + ")");
            StockCandlestickChart.generateChart(
                    allStockData.get(i),
                    savePath,
                    file -> System.out.println("图表 " + index + " 保存成功: " + file.getAbsolutePath()),
                    ex -> System.err.println("图表 " + index + " 失败: " + ex.getMessage())
            );
        }

        System.out.println("所有图表生成请求已提交");
    }
}