package com.brunorozendo.mcpclientgui.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents the structure of the mcp.json configuration file.
 */
public class McpConfig {

    @JsonProperty("mcpServers")
    private Map<String, McpServerEntry> mcpServers;

    @JsonProperty("globalSettings")
    private GlobalSettings globalSettings;

    public Map<String, McpServerEntry> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(Map<String, McpServerEntry> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public GlobalSettings getGlobalSettings() {
        return globalSettings;
    }

    public void setGlobalSettings(GlobalSettings globalSettings) {
        this.globalSettings = globalSettings;
    }

    /**
     * Represents a single MCP server entry in the configuration.
     */
    public static class McpServerEntry {
        @JsonProperty("command")
        private String command;

        @JsonProperty("args")
        private List<String> args;

        @JsonProperty("env")
        private Map<String, String> env;

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args; }
        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }
    }

    /**
     * Represents global settings applicable to the application.
     */
    public static class GlobalSettings {
        @JsonProperty("defaultTimeout")
        private int defaultTimeout;

        @JsonProperty("enableDebugLogging")
        private boolean enableDebugLogging;

        @JsonProperty("maxConcurrentConnections")
        private int maxConcurrentConnections;

        public int getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(int defaultTimeout) { this.defaultTimeout = defaultTimeout; }
        public boolean isEnableDebugLogging() { return enableDebugLogging; }
        public void setEnableDebugLogging(boolean enableDebugLogging) { this.enableDebugLogging = enableDebugLogging; }
        public int getMaxConcurrentConnections() { return maxConcurrentConnections; }
        public void setMaxConcurrentConnections(int maxConcurrentConnections) { this.maxConcurrentConnections = maxConcurrentConnections; }
    }
}
