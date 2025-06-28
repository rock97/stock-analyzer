package com.stock.analyzer.stockanalyzer.model;

 public  class StockDailyData {
        private final String date; // 日期
        private final double open; // 开盘价
        private final double high; // 最高价
        private final double low; // 最低价
        private final double close; // 收盘价
        private final long volume; // 成交量
        private final String priceChange; // 收盘红绿（上涨1、下跌0）

        public StockDailyData(String date, double open, double high, double low, double close, long volume) {
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.priceChange = calculatePriceChange(open, close);
        }

        /**
         * 计算价格变化状态
         *
         * @param open  开盘价
         * @param close 收盘价
         * @return 价格变化状态：2（涨停）、1（上涨）、0（下跌或平）
         */
        private String calculatePriceChange(double open, double close) {
            // 计算涨幅百分比
            double changePercent = (close - open) / open * 100;

            // 涨停判断（一般A股涨停为10%，指数无涨跌幅限制）
            if (changePercent >= 9.9) { // 使用9.5%作为涨停判断阈值，考虑到可能有小数点误差
                return "1";
            } else if (close > open) {
                return "1";
            } else {
                return "0";
            }
        }

        public String getDate() {
            return date;
        }

        public double getOpen() {
            return open;
        }

        public double getHigh() {
            return high;
        }

        public double getLow() {
            return low;
        }

        public double getClose() {
            return close;
        }

        public long getVolume() {
            return volume;
        }

        /**
         * 获取收盘红绿状态
         *
         * @return 红（上涨）、绿（下跌）或平
         */
        public String getPriceChange() {
            return priceChange;
        }

        @Override
        public String toString() {
            return String.format("日期: %s, 开盘: %.2f, 最高: %.2f, 最低: %.2f, 收盘: %.2f, 成交量: %d, 涨跌: %s",
                    date, open, high, low, close, volume, priceChange);
        }
    }
