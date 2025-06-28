package com.stock.analyzer.stockanalyzer.model;
/**
 * @author lizhihua03 <lizhihua03@kuaishou.com>
 * Created on 2025-06-27
 */
public class StockInfo {
    private final String code;    // 股票代码
    private final String name;    // 股票名称
    private final String market;  // 所属市场

    public StockInfo(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getMarket() {
        return market;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s)", code, name, market);
    }
}
