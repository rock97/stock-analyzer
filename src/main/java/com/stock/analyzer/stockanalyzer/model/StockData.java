package com.stock.analyzer.stockanalyzer.model;

public class StockData {
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
