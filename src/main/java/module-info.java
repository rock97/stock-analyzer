module com.stock.analyzer.stockanalyzer {
    // 导出包含主应用类的包
    exports com.stock.analyzer.stockanalyzer.service;

    // 依赖JavaFX模块
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires javafx.swing;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.jfree.jfreechart;

    // 如果使用了其他JavaFX模块，也需要添加
    // requires javafx.web;
    // requires javafx.media;

    // 声明这是一个JavaFX应用
    opens com.stock.analyzer.stockanalyzer.service to javafx.graphics;
}