package com.brunorozendo.mcpclientgui;

import java.lang.reflect.Method;

/**
 * Launcher class for the fat JAR to bypass module system issues
 */
public class McpClientGuiLauncher {
    public static void main(String[] args) throws Exception {
        // Use reflection to call the actual main class
        Class<?> mainClass = Class.forName("com.brunorozendo.mcpclientgui.McpClientGuiApp");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }
}
