package com.brunorozendo.mcpclientgui.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the MCP (Model Context Protocol) configuration structure.
 * 
 * This configuration is typically loaded from an mcp.json file and defines:
 * - MCP servers to connect to
 * - Global settings for the application
 * 
 * Example configuration:
 * <pre>
 * {
 *   "mcpServers": {
 *     "example-server": {
 *       "command": "npx",
 *       "args": ["-y", "@modelcontextprotocol/server-example"],
 *       "env": {
 *         "API_KEY": "secret"
 *       }
 *     }
 *   },
 *   "globalSettings": {
 *     "defaultTimeout": 60,
 *     "enableDebugLogging": false,
 *     "maxConcurrentConnections": 5
 *   }
 * }
 * </pre>
 */
public class McpConfig {

    @JsonProperty("mcpServers")
    private Map<String, McpServerEntry> mcpServers;

    @JsonProperty("globalSettings")
    private GlobalSettings globalSettings;

    /**
     * Gets the map of MCP server configurations.
     * The key is the server's logical name, and the value contains the server details.
     * 
     * @return Map of server name to server configuration, or null if not set
     */
    public Map<String, McpServerEntry> getMcpServers() {
        return mcpServers;
    }

    /**
     * Sets the map of MCP server configurations.
     * 
     * @param mcpServers Map of server configurations to set
     */
    public void setMcpServers(Map<String, McpServerEntry> mcpServers) {
        this.mcpServers = mcpServers;
    }

    /**
     * Gets the global settings for the application.
     * 
     * @return Global settings, or null if not set
     */
    public GlobalSettings getGlobalSettings() {
        return globalSettings;
    }

    /**
     * Sets the global settings for the application.
     * 
     * @param globalSettings Global settings to set
     */
    public void setGlobalSettings(GlobalSettings globalSettings) {
        this.globalSettings = globalSettings;
    }

    /**
     * Validates if this configuration has at least one server defined.
     * 
     * @return True if at least one server is configured
     */
    public boolean hasServers() {
        return mcpServers != null && !mcpServers.isEmpty();
    }

    /**
     * Gets the number of configured servers.
     * 
     * @return The count of configured servers
     */
    public int getServerCount() {
        return mcpServers != null ? mcpServers.size() : 0;
    }

    @Override
    public String toString() {
        return String.format("McpConfig{servers=%d, globalSettings=%s}",
                           getServerCount(),
                           globalSettings != null ? "present" : "absent");
    }

    /**
     * Represents a single MCP server configuration entry.
     * 
     * Each server entry defines how to launch and connect to an MCP server process.
     */
    public static class McpServerEntry {
        @JsonProperty("command")
        private String command;

        @JsonProperty("args")
        private List<String> args;

        @JsonProperty("env")
        private Map<String, String> env;

        /**
         * Gets the command to execute for starting the MCP server.
         * This is typically the executable name or path.
         * 
         * @return The command string (e.g., "node", "python", "npx")
         */
        public String getCommand() { 
            return command; 
        }

        /**
         * Sets the command to execute for starting the MCP server.
         * 
         * @param command The command to set (cannot be null or empty)
         */
        public void setCommand(String command) { 
            this.command = command; 
        }

        /**
         * Gets the command-line arguments to pass to the server command.
         * 
         * @return List of arguments, or null if none specified
         */
        public List<String> getArgs() { 
            return args; 
        }

        /**
         * Sets the command-line arguments for the server command.
         * 
         * @param args List of arguments to set
         */
        public void setArgs(List<String> args) { 
            this.args = args; 
        }

        /**
         * Gets the environment variables to set for the server process.
         * 
         * @return Map of environment variable names to values, or null if none
         */
        public Map<String, String> getEnv() { 
            return env; 
        }

        /**
         * Sets the environment variables for the server process.
         * 
         * @param env Map of environment variables to set
         */
        public void setEnv(Map<String, String> env) { 
            this.env = env; 
        }

        /**
         * Validates if this server entry has the minimum required configuration.
         * 
         * @return True if the command is specified
         */
        public boolean isValid() {
            return command != null && !command.trim().isEmpty();
        }

        /**
         * Gets the full command line as it would be executed.
         * 
         * @return String representation of command with arguments
         */
        public String getFullCommandLine() {
            if (command == null) {
                return "";
            }
            
            StringBuilder cmdLine = new StringBuilder(command);
            if (args != null && !args.isEmpty()) {
                for (String arg : args) {
                    cmdLine.append(" ").append(arg);
                }
            }
            return cmdLine.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            McpServerEntry that = (McpServerEntry) obj;
            return Objects.equals(command, that.command) &&
                   Objects.equals(args, that.args) &&
                   Objects.equals(env, that.env);
        }

        @Override
        public int hashCode() {
            return Objects.hash(command, args, env);
        }

        @Override
        public String toString() {
            return String.format("McpServerEntry{command='%s', args=%s, envVars=%d}",
                               command,
                               args != null ? args : "[]",
                               env != null ? env.size() : 0);
        }
    }

    /**
     * Represents global settings for the MCP client application.
     * 
     * These settings apply to all MCP connections and the overall behavior
     * of the application.
     */
    public static class GlobalSettings {
        // Default values
        private static final int DEFAULT_TIMEOUT = 60;
        private static final int DEFAULT_MAX_CONNECTIONS = 10;
        
        @JsonProperty("defaultTimeout")
        private int defaultTimeout = DEFAULT_TIMEOUT;

        @JsonProperty("enableDebugLogging")
        private boolean enableDebugLogging = false;

        @JsonProperty("maxConcurrentConnections")
        private int maxConcurrentConnections = DEFAULT_MAX_CONNECTIONS;

        /**
         * Gets the default timeout for MCP operations in seconds.
         * 
         * @return Timeout in seconds
         */
        public int getDefaultTimeout() { 
            return defaultTimeout; 
        }

        /**
         * Sets the default timeout for MCP operations.
         * 
         * @param defaultTimeout Timeout in seconds (must be positive)
         */
        public void setDefaultTimeout(int defaultTimeout) { 
            if (defaultTimeout <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.defaultTimeout = defaultTimeout; 
        }

        /**
         * Checks if debug logging is enabled.
         * 
         * @return True if debug logging should be enabled
         */
        public boolean isEnableDebugLogging() { 
            return enableDebugLogging; 
        }

        /**
         * Sets whether debug logging should be enabled.
         * 
         * @param enableDebugLogging True to enable debug logging
         */
        public void setEnableDebugLogging(boolean enableDebugLogging) { 
            this.enableDebugLogging = enableDebugLogging; 
        }

        /**
         * Gets the maximum number of concurrent MCP connections allowed.
         * 
         * @return Maximum concurrent connections
         */
        public int getMaxConcurrentConnections() { 
            return maxConcurrentConnections; 
        }

        /**
         * Sets the maximum number of concurrent MCP connections.
         * 
         * @param maxConcurrentConnections Maximum connections (must be positive)
         */
        public void setMaxConcurrentConnections(int maxConcurrentConnections) { 
            if (maxConcurrentConnections <= 0) {
                throw new IllegalArgumentException("Max connections must be positive");
            }
            this.maxConcurrentConnections = maxConcurrentConnections; 
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GlobalSettings that = (GlobalSettings) obj;
            return defaultTimeout == that.defaultTimeout &&
                   enableDebugLogging == that.enableDebugLogging &&
                   maxConcurrentConnections == that.maxConcurrentConnections;
        }

        @Override
        public int hashCode() {
            return Objects.hash(defaultTimeout, enableDebugLogging, maxConcurrentConnections);
        }

        @Override
        public String toString() {
            return String.format("GlobalSettings{timeout=%ds, debugLogging=%s, maxConnections=%d}",
                               defaultTimeout,
                               enableDebugLogging ? "enabled" : "disabled",
                               maxConcurrentConnections);
        }
    }
}
