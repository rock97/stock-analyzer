package com.stock.analyzer.stockanalyzer.service;

import com.stock.analyzer.stockanalyzer.model.StockDailyData;
import com.stock.analyzer.stockanalyzer.model.StockData;
import com.stock.analyzer.stockanalyzer.model.StockInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 新浪财经股票日线数据获取工具
 * 可以获取指定股票的历史日线数据
 */
public class SinaStockDataFetcher {

    private static final String SINA_STOCK_HISTORY_API = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%%20_%s_day_data=%%20/CN_MarketDataService.getKLineData";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final StockCandlestickChartV2 chartV2 = new StockCandlestickChartV2();


    // private static final  StockCandlestickChart chartGenerator = new StockCandlestickChart();

    /**
     * 获取股票历史日线数据
     *
     * @param stockCode 股票代码（如：sh600000或sz000001）
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate   结束日期（格式：yyyy-MM-dd）
     * @return 股票历史日线数据列表
     */
    public List<StockDailyData> fetchStockDailyData(String stockCode, LocalDate startDate, LocalDate endDate) {
        List<StockDailyData> result = new ArrayList<>();
        try {
            // 构建请求URL
            String requestUrl = buildRequestUrl(stockCode, startDate, endDate);

            // 发送HTTP请求
            String responseData = sendHttpRequest(requestUrl);

            // 解析响应数据
            result = parseResponseData(responseData);

        } catch (Exception e) {
            System.err.println("获取股票日线数据失败: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 构建请求URL
     */
    private String buildRequestUrl(String stockCode, LocalDate startDate, LocalDate endDate) {
        String formattedStartDate = startDate.format(DATE_FORMATTER);
        String formattedEndDate = endDate.format(DATE_FORMATTER);

        // 新浪财经API需要的股票代码格式转换
        String sinaStockCode = convertToSinaStockCode(stockCode);

        return String.format(SINA_STOCK_HISTORY_API, sinaStockCode) +
                "?symbol=" + sinaStockCode +
                "&scale=240" + // 日K线
                "&ma=5" + // 5日均线
                "&datalen=60" + // 最大数据长度
                "&from=" + formattedStartDate +
                "&to=" + formattedEndDate;
    }

    /**
     * 转换股票代码为新浪财经API格式
     */
    private String convertToSinaStockCode(String stockCode) {
        // 如果已经是新浪格式，直接返回
        if (stockCode.startsWith("sh") || stockCode.startsWith("sz")) {
            return stockCode;
        }

        // 上证股票以6开头，深证股票以0或3开头
        if (stockCode.startsWith("6")) {
            return "sh" + stockCode;
        } else if (stockCode.startsWith("0") || stockCode.startsWith("3")) {
            return "sz" + stockCode;
        }

        // 默认返回原始代码
        return stockCode;
    }

    /**
     * 发送HTTP请求获取数据
     */
    private String sendHttpRequest(String requestUrl) throws IOException {
        URL url = new URL(requestUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    /**
     * 解析响应数据
     */
    private List<StockDailyData> parseResponseData(String responseData) {
        List<StockDailyData> result = new ArrayList<>();

        // 提取JSON数据部分
        int startIndex = responseData.indexOf("[");
        int endIndex = responseData.lastIndexOf("]");

        if (startIndex >= 0 && endIndex > startIndex) {
            String jsonData = responseData.substring(startIndex, endIndex + 1);

            // 简单解析JSON数组（避免引入额外的JSON库依赖）
            // 格式:
            // [{"day":"2023-01-01","open":"10.00","high":"10.50","low":"9.80","close":"10.20","volume":"12345678"},
            // ...]
            String[] items = jsonData.split("\\},\\{");

            for (String item : items) {
                // 清理JSON格式字符
                item = item.replace("[", "").replace("]", "").replace("{", "").replace("}", "");

                // 解析每个字段
                String day = extractValue(item, "day");
                String open = extractValue(item, "open");
                String high = extractValue(item, "high");
                String low = extractValue(item, "low");
                String close = extractValue(item, "close");
                String volume = extractValue(item, "volume");

                if (day != null && !day.isEmpty()) {
                    StockDailyData dailyData = new StockDailyData(
                            day,
                            parseDouble(open),
                            parseDouble(high),
                            parseDouble(low),
                            parseDouble(close),
                            parseLong(volume));
                    result.add(dailyData);
                }
            }
        }

        return result;
    }

    /**
     * 从JSON字符串中提取字段值
     */
    private String extractValue(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\"";
        int startIndex = json.indexOf(pattern);
        if (startIndex >= 0) {
            startIndex += pattern.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex > startIndex) {
                return json.substring(startIndex, endIndex);
            }
        }
        return "";
    }

    /**
     * 安全解析Double值
     */
    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 安全解析Long值
     */
    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 生成股票涨跌链接字符串
     *
     * @param dataList 股票数据列表
     * @return 涨跌链接字符串，1表示上涨，0表示下跌或平
     */
    public String generatePriceChangeString(List<StockDailyData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return "";
        }

        StringBuilder priceChangeBuilder = new StringBuilder();
        for (StockDailyData data : dataList) {
            priceChangeBuilder.append(data.getPriceChange());
            System.out.println(data);
        }

        return priceChangeBuilder.toString();
    }

    /**
     * 将股票代码和涨跌链接字符串保存到CSV文件
     *
     * @param stockCode 股票代码
     * @param dataList  股票数据列表
     * @param filePath  保存文件路径
     * @param append    是否追加到现有文件
     */
    public void saveToCSV(String stockCode, String stockName, List<StockDailyData> dataList, String filePath,
                          boolean append) {
        if (dataList == null || dataList.isEmpty()) {
            System.out.println("没有数据可保存");
            return;
        }

        try {
            List<String> lines = new ArrayList<>();

            // 如果是新文件或不追加，添加CSV头
            if (!append || !Files.exists(Paths.get(filePath))) {
                lines.add("股票代码,股票名称,涨跌字符串");
            }

            // 生成涨跌链接字符串
            String priceChangeString = generatePriceChangeString(dataList);

            // 添加数据行
            lines.add(String.format("%s,%s,%s", stockCode, stockName, priceChangeString));

            // 写入文件（追加模式或覆盖模式）
            if (append && Files.exists(Paths.get(filePath))) {
                Files.write(Paths.get(filePath), lines, java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(Paths.get(filePath), lines);
            }

            System.out.println("成功保存" + stockCode + "的涨跌链接字符串到文件: " + filePath);
        } catch (IOException e) {
            System.err.println("保存数据到CSV文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将完整的股票日线数据保存到CSV文件
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param dataList  股票日线数据列表
     * @param filePath  保存文件路径
     */
    public void saveFullDataToCSV(String stockCode, String stockName, List<StockDailyData> dataList, String filePath) {
        if (dataList == null || dataList.isEmpty()) {
            System.out.println("没有数据可保存");
            return;
        }

        try {
            List<String> lines = new ArrayList<>();

            // 添加CSV头
            lines.add("股票代码,股票名称,日期,开盘价,最高价,最低价,收盘价,成交量,涨跌");

            // 添加数据行
            for (StockDailyData data : dataList) {
                lines.add(String.format("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%d,%s",
                        stockCode, stockName, data.getDate(),
                        data.getOpen(), data.getHigh(), data.getLow(),
                        data.getClose(), data.getVolume(), data.getPriceChange()));
            }

            // 写入文件
            Files.write(Paths.get(filePath), lines);

            System.out.println("成功保存" + stockCode + "的完整日线数据到文件: " + filePath);
        } catch (IOException e) {
            System.err.println("保存数据到CSV文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 主方法，用于测试
     */
    public static void main(String[] args) {
        SinaStockDataFetcher fetcher = new SinaStockDataFetcher();

        AllStockCode allStockCode = new AllStockCode();

        // 定义要获取的股票列表
        List<StockInfo> stockInfos = allStockCode.getAllStockInfo();

        // 设置日期范围
        LocalDate endDate = LocalDate.now(); // 今天
        LocalDate startDate = endDate.minusMonths(1); // 3个月前

        System.out.println("获取时间范围: " + startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                " 至 " + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        String dataStr = endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 所有股票数据保存到同一个汇总Excel
        String summaryExcelPath = "./file/" + dataStr + "_all_stocks_summary.xlsx";
        List<List<StockDailyData>> allDataLists = new ArrayList<>();
        List<List<StockData>> allStockDataList = new ArrayList<>();

        // 创建线程池，使用可用处理器数量作为线程数
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = new ArrayList<>();
        // 为每只股票创建并行任务
        for (int i = 0; i < stockInfos.size(); i++) {
            final int index = i; // 用于lambda表达式中的索引访问
            StockInfo stockInfo = stockInfos.get(i);
            Callable<Void> task = () -> {
                String stockCode = stockInfo.getCode();
                String stockName = stockInfo.getName();
                System.out.println("正在获取 " + index + " " + stockName + " 的日线数据...");

                // 获取股票日线数据
                List<StockDailyData> dataList = fetcher.fetchStockDailyData(stockCode, startDate, endDate);
                synchronized (allDataLists) {
                    allDataLists.add(dataList);
                }

                List<StockData> stockDataList = dataList.stream().map(v -> {
                    return new StockData(stockCode, stockName, v.getDate(), v.getOpen(), v.getClose(), v.getHigh(), v.getLow());
                }).collect(Collectors.toList());
                if (stockDataList.size() > 0) {
                    genJPG(stockDataList, index);
                }
                TimeUnit.MILLISECONDS.sleep(50);
                synchronized (allStockDataList) {
                    allStockDataList.add(stockDataList);
                }
                return null;
            };
            tasks.add(task); // 添加任务到列表
        }

        try {
            // 提交所有任务并等待完成
            List<Future<Void>> futures = executor.invokeAll(tasks);
            // 检查是否有异常
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }


        for (List<StockData> stockDataList : allStockDataList) {
            if (stockDataList.size() <= 1) {
                continue;
            }

        }
        // 生成汇总Excel

        /*Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (!StockCandlestickChart.checkCompleted()) {
                    System.out.println("图片生成未完成");
                }
                fetcher.generateAllStocksSummaryExcel(stockInfos, allDataLists, summaryExcelPath);
                System.out.println("所有图表生成完成");
            }
        };
        runnable.run();*/
    }

    private static void genJPG(List<StockData> stockDataList, int i) {

        StringBuilder priceChangeSummary = new StringBuilder();
        Double min = Double.MAX_VALUE;
        int zhangfu = 0;
        for (StockData data : stockDataList) {
            priceChangeSummary.append(data.isUp() ? "1" : "0");
            min = Math.min(min, data.getClose());
            zhangfu = Double.valueOf(((data.getClose() - min) / min) * 100).intValue();
        }
        String fileName = String.format("./file/%d-%s-%s.png", zhangfu, stockDataList.get(0).getStockCode(), priceChangeSummary);
        StockCandlestickChart.generateChart(stockDataList, fileName);
    }

    /**
     * 测试涨跌判断逻辑
     */
    private static List<StockDailyData> testPriceChangeLogic() {
        System.out.println("\n===== 测试涨跌判断逻辑 =====");

        // 创建测试数据
        StockDailyData data1 = new StockDailyData("2023-01-01", 100.0, 110.0, 95.0, 105.0, 1000000); // 上涨5%
        StockDailyData data2 = new StockDailyData("2023-01-02", 100.0, 90.0, 85.0, 90.0, 1000000); // 下跌10%
        StockDailyData data3 = new StockDailyData("2023-01-03", 100.0, 115.0, 100.0, 110.0, 1000000); // 上涨10%（涨停）
        StockDailyData data4 = new StockDailyData("2023-01-04", 100.0, 100.0, 100.0, 100.0, 1000000); // 平
        List<StockDailyData> list = List.of(data1, data2, data3, data4);

        // 打印测试结果
        System.out.println("上涨5%: " + data1.getPriceChange() + " (期望值: 1)");
        System.out.println("下跌10%: " + data2.getPriceChange() + " (期望值: 0)");
        System.out.println("上涨10%（涨停）: " + data3.getPriceChange() + " (期望值: 2)");
        System.out.println("平: " + data4.getPriceChange() + " (期望值: 0)");
        System.out.println("===== 测试结束 =====\n");
        return list;
    }


    /**
     * 将股票数据和图表保存到Excel文件
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param dataList  股票日线数据列表
     * @param filePath  保存文件路径
     */
    public void saveToExcelWithChart(String stockCode, String stockName, List<StockDailyData> dataList, String filePath) {
        if (dataList == null || dataList.isEmpty()) {
            System.out.println("没有数据可保存");
            return;
        }

        // 创建工作簿和工作表
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(stockName + "数据");
            int rowNum = 0;

            // 创建表头
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"日期", "开盘价", "最高价", "最低价", "收盘价", "成交量", "涨跌"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 填充数据
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (StockDailyData data : dataList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(data.getDate());
                row.createCell(1).setCellValue(data.getOpen());
                row.createCell(2).setCellValue(data.getHigh());
                row.createCell(3).setCellValue(data.getLow());
                row.createCell(4).setCellValue(data.getClose());
                row.createCell(5).setCellValue(data.getVolume());
                row.createCell(6).setCellValue(data.getPriceChange());

                // 添加收盘价数据到图表数据集
                dataset.addValue(data.getClose(), "收盘价", data.getDate());
            }

            // 生成折线图
            JFreeChart chart = ChartFactory.createLineChart(
                    stockName + "股价走势", // 图表标题
                    "日期", // X轴标签
                    "价格", // Y轴标签
                    dataset // 数据集
            );

            // 保存图表为临时文件
            String tempDir = "file";
            Files.createDirectories(Paths.get(tempDir)); // 确保目录存在
            String tempImagePath = tempDir + File.separator + "chart-" + stockCode + ".png";
            ChartUtils.saveChartAsPNG(new File(tempImagePath), chart, 800, 400);

            // 将图表插入Excel
            int pictureIdx = workbook.addPicture(Files.readAllBytes(Paths.get(tempImagePath)), Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = workbook.getCreationHelper();
            Drawing drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE); // 设置图片随单元格移动和调整大小

            // 设置图片位置 (紧接数据行下方，与表格列对齐)
            anchor.setRow1(rowNum); // 数据行下方
            anchor.setCol1(0);
            anchor.setRow2(rowNum + 20); // 跨越20行
            anchor.setCol2(7); // 跨越7列，与表格宽度匹配

            drawing.createPicture(anchor, pictureIdx);

            // 调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入Excel文件
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

            // 删除临时图片文件
            Files.deleteIfExists(Paths.get(tempImagePath));

            System.out.println("成功保存" + stockCode + "的带图表数据到文件: " + filePath);
        } catch (IOException e) {
            System.err.println("保存Excel文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 生成包含股票代码、名称、涨跌汇总和图片的Excel文件
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param dataList 股票日线数据列表
     * @param filePath 保存文件路径
     */
    /**
     * 为单个股票添加汇总数据行到Excel工作表
     */
    private void addStockSummaryRow(Sheet sheet, int rowNum, String stockCode, String stockName, List<StockDailyData> dataList) throws IOException {
        // 创建数据行
        Row dataRow = sheet.createRow(rowNum);

        // 股票代码
        dataRow.createCell(0).setCellValue(stockCode);

        // 股票名称
        dataRow.createCell(1).setCellValue(stockName);

        // 股票涨跌汇总字符串
        StringBuilder priceChangeSummary = new StringBuilder();
        for (StockDailyData data : dataList) {
            priceChangeSummary.append(data.getPriceChange());
        }
        dataRow.createCell(2).setCellValue(priceChangeSummary.toString());

        // 插入图片
        String imagePath = "./file/chart-" + stockCode + ".png";
        if (Files.exists(Paths.get(imagePath))) {
            Workbook workbook = sheet.getWorkbook();
            int pictureIdx = workbook.addPicture(Files.readAllBytes(Paths.get(imagePath)), Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = workbook.getCreationHelper();
            Drawing drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

            // 设置图片位置 (当前行，第3列)
            anchor.setRow1(rowNum);
            anchor.setCol1(3);
            anchor.setRow2(rowNum + 15); // 跨越15行
            anchor.setCol2(4); // 跨越1列

            drawing.createPicture(anchor, pictureIdx);
        } else {
            dataRow.createCell(3).setCellValue("图片不存在");
            System.err.println("图片文件不存在: " + imagePath);
        }
    }

    /**
     * 生成包含所有股票汇总数据的Excel文件
     */
    public void generateAllStocksSummaryExcel(List<StockInfo> stockInfos, List<List<StockDailyData>> dataLists, String filePath) {
        if (stockInfos == null || stockInfos.isEmpty() || dataLists == null) {
            System.out.println("股票信息或数据列表为空或不匹配");
            return;
        }

        // 创建工作簿和工作表
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("股票汇总");
            int rowNum = 0;

            // 创建表头
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"股票代码", "股票名称", "股票涨跌", "图片"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 为每个股票添加数据行
            for (int i = 0; i < dataLists.size(); i++) {
                StockInfo stockInfo = stockInfos.get(i);
                List<StockDailyData> dataList = dataLists.get(i);
                if (dataList != null && !dataList.isEmpty()) {
                    addStockSummaryRow(sheet, rowNum, stockInfo.getCode(), stockInfo.getName(), dataList);
                    rowNum += 16; // 为下一个股票预留15行图片空间
                }
            }

            // 调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            // 调整图片列宽度
            sheet.setColumnWidth(3, 70 * 256); // 设置图片列宽度为60个字符宽度

            // 写入Excel文件
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

            System.out.println("成功生成所有股票汇总Excel文件: " + filePath);

        } catch (IOException e) {
            System.err.println("生成Excel文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}