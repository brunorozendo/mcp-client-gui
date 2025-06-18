module com.brunorozendo.mcpclientgui {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.controlsfx.controls;
    requires java.net.http;
    requires io.modelcontextprotocol.sdk.mcp;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires org.commonmark;

    opens com.brunorozendo.mcpclientgui to javafx.fxml;
    opens com.brunorozendo.mcpclientgui.controller to javafx.fxml;
    opens com.brunorozendo.mcpclientgui.model to com.fasterxml.jackson.databind;

    exports com.brunorozendo.mcpclientgui;
    exports com.brunorozendo.mcpclientgui.controller;
    exports com.brunorozendo.mcpclientgui.model;
    exports com.brunorozendo.mcpclientgui.core;
    exports com.brunorozendo.mcpclientgui.core.chat;
    exports com.brunorozendo.mcpclientgui.ui.components;
    exports com.brunorozendo.mcpclientgui.ui.handlers;
}
