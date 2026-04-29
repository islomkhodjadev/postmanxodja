module uz.postbaby.desktop {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.net.http;
    requires java.desktop;
    requires java.prefs;
    requires jdk.httpserver;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.slf4j;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;

    exports uz.postbaby.desktop;
    exports uz.postbaby.desktop.ui;
    exports uz.postbaby.desktop.model;

    opens uz.postbaby.desktop to javafx.fxml;
    opens uz.postbaby.desktop.ui to javafx.fxml;
    opens uz.postbaby.desktop.model to com.fasterxml.jackson.databind;
    opens uz.postbaby.desktop.store to com.fasterxml.jackson.databind;
}
