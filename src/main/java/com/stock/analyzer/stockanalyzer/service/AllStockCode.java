package com.stock.analyzer.stockanalyzer.service;
import com.stock.analyzer.stockanalyzer.model.StockInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 获取全部A股股票代码工具类
 * 支持分页获取，每页100条数据
 */
public class AllStockCode {

    // 东方财富API接口
    private static final String EASTMONEY_API = "http://80.push2.eastmoney.com/api/qt/clist/get";

    // 分页设置
    private static final int PAGE_SIZE = 100; // 每页数据量

    /**
     * 获取全部A股股票代码（分页获取）
     *
     * @return 股票代码列表
     */
    public List<String> getAllStockCodes() {
        List<String> allCodes = new ArrayList<>();

        System.out.println("开始分页获取A股股票代码...");

        // 获取上证A股 (包括主板和科创板)
        List<String> shCodes = getStockCodesByMarketWithPaging("sh");
        allCodes.addAll(shCodes);
        System.out.println("上证股票: " + shCodes.size() + " 只");

        // 获取深证A股 (包括主板、中小板和创业板)
        List<String> szCodes = getStockCodesByMarketWithPaging("sz");
        allCodes.addAll(szCodes);
        System.out.println("深证股票: " + szCodes.size() + " 只");

        System.out.println("总计: " + allCodes.size() + " 只股票");
        return allCodes;
    }

    /**
     * 分页获取指定市场的股票代码
     *
     * @param market 市场代码 (sh/sz)
     * @return 股票代码列表
     */
    private List<String> getStockCodesByMarketWithPaging(String market) {
        List<String> allCodes = new ArrayList<>();
        int pageNum = 1;

        System.out.println("开始获取 " + market + " 市场股票代码...");

        while (true) {
            try {
                System.out.printf("正在获取 %s 市场第 %d 页数据...", market, pageNum);

                String url = buildRequestUrl(market, pageNum, PAGE_SIZE);
                String response = sendHttpRequest(url);
                List<String> pageCodes = parseStockCodes(response, market);

                if (pageCodes.isEmpty()) {
                    System.out.println(" 无数据，停止获取");
                    break;
                }

                allCodes.addAll(pageCodes);
                System.out.printf(" 获取到 %d 条数据\n", pageCodes.size());

                // 如果返回的数据少于页面大小，说明已经是最后一页
                if (pageCodes.size() < PAGE_SIZE) {
                    System.out.println(market + " 市场数据获取完成");
                    break;
                }

                pageNum++;

                // 添加延时避免请求过于频繁
                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("获取" + market + "股票代码第" + pageNum + "页失败: " + e.getMessage());
                break;
            }
        }

        return allCodes;
    }

    /**
     * 构建分页请求URL
     *
     * @param market 市场代码
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 请求URL
     */
    private String buildRequestUrl(String market, int pageNum, int pageSize) {
        String fs = "";

        if ("sh".equals(market)) {
            // 上证A股: m:1+t:2 (主板) + m:1+t:23 (科创板)
            fs = "m:1+t:2,m:1+t:23";
        } else if ("sz".equals(market)) {
            // 深证A股: m:0+t:6 (主板) + m:0+t:13 (中小板) + m:0+t:80 (创业板)
            fs = "m:0+t:6,m:0+t:13,m:0+t:80";
        }

        return EASTMONEY_API +
                "?pn=" + pageNum +  // 页码
                "&pz=" + pageSize + // 每页数量
                "&po=1" +           // 排序
                "&np=1" +           // 分页
                "&ut=bd1d9ddb04089700cf9c27f6f7426281" +
                "&fltt=2" +
                "&invt=2" +
                "&fid=f3" +         // 排序字段
                "&fs=" + fs +       // 过滤条件
                "&fields=f12,f14";  // 返回字段: f12=股票代码, f14=股票名称
    }

    /**
     * 发送HTTP请求
     *
     * @param requestUrl 请求URL
     * @return 响应内容
     */
    private String sendHttpRequest(String requestUrl) throws IOException {
        URL url = new URL(requestUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // 设置请求属性
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setRequestProperty("Referer", "http://quote.eastmoney.com/");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        // 读取响应
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    /**
     * 解析股票代码
     *
     * @param response API响应内容
     * @param market 市场前缀
     * @return 股票代码列表
     */
    private List<String> parseStockCodes(String response, String market) {
        List<String> codes = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return codes;
        }

        // 使用正则表达式提取股票代码
        // JSON格式: "f12":"600000","f14":"浦发银行"
        Pattern pattern = Pattern.compile("\"f12\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            String code = matcher.group(1);
            if (code != null && !code.isEmpty()) {
                // 添加市场前缀
                codes.add(market + code);
            }
        }

        return codes;
    }

    /**
     * 获取股票详细信息（分页获取）
     *
     * @return 股票详细信息列表
     */
    public List<StockInfo> getAllStockInfo() {
        List<StockInfo> allStocks = new ArrayList<>();

        System.out.println("开始分页获取A股股票详细信息...");

        // 获取上证股票信息
        List<StockInfo> shStocks = getStockInfoByMarketWithPaging("sh");
        allStocks.addAll(shStocks);
        System.out.println("上证股票: " + shStocks.size() + " 只");

        // 获取深证股票信息
        List<StockInfo> szStocks = getStockInfoByMarketWithPaging("sz");
        allStocks.addAll(szStocks);
        System.out.println("深证股票: " + szStocks.size() + " 只");

        System.out.println("总计: " + allStocks.size() + " 只股票");
        return allStocks;
    }

    /**
     * 分页获取指定市场的股票详细信息
     */
    private List<StockInfo> getStockInfoByMarketWithPaging(String market) {
        List<StockInfo> allStocks = new ArrayList<>();
        int pageNum = 1;

        System.out.println("开始获取 " + market + " 市场股票详细信息...");

        while (true) {
            try {
                System.out.printf("正在获取 %s 市场第 %d 页详细信息...", market, pageNum);

                String url = buildRequestUrl(market, pageNum, PAGE_SIZE);
                String response = sendHttpRequest(url);
                List<StockInfo> pageStocks = parseStockInfo(response, market);

                if (pageStocks.isEmpty()) {
                    System.out.println(" 无数据，停止获取");
                    break;
                }

                allStocks.addAll(pageStocks);
                System.out.printf(" 获取到 %d 条数据\n", pageStocks.size());

                // 如果返回的数据少于页面大小，说明已经是最后一页
                if (pageStocks.size() < PAGE_SIZE) {
                    System.out.println(market + " 市场详细信息获取完成");
                    break;
                }

                pageNum++;

                // 添加延时避免请求过于频繁
                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("获取" + market + "股票详细信息第" + pageNum + "页失败: " + e.getMessage());
                break;
            }
        }

        return allStocks;
    }

    /**
     * 解析股票详细信息
     */
    private List<StockInfo> parseStockInfo(String response, String market) {
        List<StockInfo> stocks = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return stocks;
        }

        // 提取股票代码和名称
        Pattern pattern = Pattern.compile("\"f12\":\"([^\"]+)\",\"f14\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            String code = matcher.group(1);
            String name = matcher.group(2);

            if (code != null && !code.isEmpty() && name != null && !name.isEmpty()) {
                String fullCode = market + code;
                String marketName = getMarketName(code, market);
                stocks.add(new StockInfo(fullCode, name, marketName));
            }
        }

        return stocks;
    }

    /**
     * 根据股票代码判断所属市场
     */
    private String getMarketName(String code, String market) {
        if ("sh".equals(market)) {
            if (code.startsWith("68")) {
                return "科创板";
            } else {
                return "上证A股";
            }
        } else if ("sz".equals(market)) {
            if (code.startsWith("30")) {
                return "创业板";
            } else if (code.startsWith("00")) {
                return "深证A股";
            } else {
                return "深证A股";
            }
        }
        return "A股";
    }

    /**
     * 保存股票代码到文件
     *
     * @param codes 股票代码列表
     * @param filename 文件名
     */
    public void saveToFile(List<String> codes, String filename) {
        try {
            Files.write(Paths.get(filename), codes);
            System.out.println("成功保存 " + codes.size() + " 个股票代码到文件: " + filename);
        } catch (IOException e) {
            System.err.println("保存文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存股票详细信息到CSV文件
     *
     * @param stocks 股票信息列表
     * @param filename 文件名
     */
    public void saveToCSV(List<StockInfo> stocks, String filename) {
        try {
            List<String> lines = new ArrayList<>();

            // 添加CSV头
            lines.add("股票代码,股票名称,所属市场,更新时间");

            // 添加数据
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            for (StockInfo stock : stocks) {
                lines.add(String.format("%s,%s,%s,%s",
                        stock.getCode(),
                        stock.getName(),
                        stock.getMarket(),
                        currentTime));
            }

            Files.write(Paths.get(filename), lines);
            System.out.println("成功保存 " + stocks.size() + " 只股票信息到CSV文件: " + filename);

        } catch (IOException e) {
            System.err.println("保存CSV文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定页的股票代码
     *
     * @param market 市场代码
     * @param pageNum 页码
     * @return 股票代码列表
     */
    public List<String> getStockCodesByPage(String market, int pageNum) {
        List<String> codes = new ArrayList<>();

        try {
            System.out.printf("获取 %s 市场第 %d 页股票代码...\n", market, pageNum);

            String url = buildRequestUrl(market, pageNum, PAGE_SIZE);
            String response = sendHttpRequest(url);
            codes = parseStockCodes(response, market);

            System.out.printf("第 %d 页获取到 %d 个股票代码\n", pageNum, codes.size());

        } catch (Exception e) {
            System.err.println("获取第" + pageNum + "页数据失败: " + e.getMessage());
        }

        return codes;
    }

    /**
     * 获取指定页的股票详细信息
     *
     * @param market 市场代码
     * @param pageNum 页码
     * @return 股票信息列表
     */
    public List<StockInfo> getStockInfoByPage(String market, int pageNum) {
        List<StockInfo> stocks = new ArrayList<>();

        try {
            System.out.printf("获取 %s 市场第 %d 页股票详细信息...\n", market, pageNum);

            String url = buildRequestUrl(market, pageNum, PAGE_SIZE);
            String response = sendHttpRequest(url);
            stocks = parseStockInfo(response, market);

            System.out.printf("第 %d 页获取到 %d 只股票详细信息\n", pageNum, stocks.size());

        } catch (Exception e) {
            System.err.println("获取第" + pageNum + "页详细信息失败: " + e.getMessage());
        }

        return stocks;
    }


    /**
     * 主方法 - 测试用例
     */
    public static void main(String[] args) {
        AllStockCode fetcher = new AllStockCode();

        System.out.println("=== A股股票代码分页获取工具 ===");
        System.out.println("每页数据量: " + PAGE_SIZE);
        System.out.println("开始时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println();

        // 测试单页获取
        System.out.println("【测试】获取上证市场第1页数据:");
        List<String> page1Codes = fetcher.getStockCodesByPage("sh", 1);
        if (!page1Codes.isEmpty()) {
            System.out.println("第1页前5个股票代码:");
            for (int i = 0; i < Math.min(5, page1Codes.size()); i++) {
                System.out.println("  " + (i + 1) + ". " + page1Codes.get(i));
            }
        }

        System.out.println("\n" + "=".repeat(50) + "\n");

        // 完整获取所有股票代码
        System.out.println("【完整获取】所有A股股票代码:");
        List<String> allCodes = fetcher.getAllStockCodes();

        if (!allCodes.isEmpty()) {
            System.out.println("\n前10个股票代码示例:");
            for (int i = 0; i < Math.min(10, allCodes.size()); i++) {
                System.out.println((i + 1) + ". " + allCodes.get(i));
            }

            // 保存到文件
            fetcher.saveToFile(allCodes, "all_stock_codes_paged.txt");
        }

        System.out.println("\n" + "=".repeat(50) + "\n");

        // 获取股票详细信息
        System.out.println("【完整获取】所有A股股票详细信息:");
        List<StockInfo> allStocks = fetcher.getAllStockInfo();

        if (!allStocks.isEmpty()) {
            System.out.println("\n前10只股票详细信息:");
            for (int i = 0; i < Math.min(10, allStocks.size()); i++) {
                System.out.println((i + 1) + ". " + allStocks.get(i));
            }

            // 保存到CSV文件
            fetcher.saveToCSV(allStocks, "all_stock_info_paged.csv");

            // 统计各市场股票数量
            System.out.println("\n【统计信息】:");
            long shCount = allStocks.stream().filter(s -> s.getMarket().contains("上证")).count();
            long szCount = allStocks.stream().filter(s -> s.getMarket().contains("深证")).count();
            long cybCount = allStocks.stream().filter(s -> s.getMarket().contains("创业")).count();
            long kcbCount = allStocks.stream().filter(s -> s.getMarket().contains("科创")).count();

            System.out.println("上证A股: " + shCount + " 只");
            System.out.println("深证A股: " + szCount + " 只");
            System.out.println("创业板: " + cybCount + " 只");
            System.out.println("科创板: " + kcbCount + " 只");
            System.out.println("总计: " + allStocks.size() + " 只");
        }

        System.out.println("\n=== 分页获取完成 ===");
        System.out.println("结束时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
