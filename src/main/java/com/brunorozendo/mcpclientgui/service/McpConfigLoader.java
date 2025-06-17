package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.McpConfig;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Service for loading MCP (Model Context Protocol) configuration from JSON files.
 * This loader handles:
 * - Reading and parsing MCP configuration files
 * - Validation of configuration structure
 * - Error handling for malformed or missing files
 * The expected configuration format follows the MCP specification,
 * typically containing server definitions with their commands and arguments.
 */
public class McpConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(McpConfigLoader.class);
    
    // Configuration constants
    private static final String JSON_FILE_EXTENSION = ".json";
    private static final long MAX_FILE_SIZE_MB = 10;
    private static final long MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;
    
    // Default configuration paths
    private static final String CONFIG_FILENAME = "mcp.json";
    private static final String CONFIG_DIR = ".config/mcp";
    
    private final ObjectMapper jsonMapper;

    /**
     * Creates a new MCP configuration loader.
     */
    public McpConfigLoader() {
        this.jsonMapper = createJsonMapper();
    }
    
    /**
     * Creates and configures the Jackson JsonMapper for JSON parsing.
     * 
     * @return Configured JsonMapper instance
     */
    private ObjectMapper createJsonMapper() {
        return JsonMapper.builder()
                // Don't fail on unknown properties for forward compatibility
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Allow comments in JSON files for better documentation
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
                // Allow trailing commas for easier editing
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA, true)
                .build();
    }

    /**
     * Loads MCP configuration from the specified file.
     * 
     * @param configFile The MCP configuration JSON file
     * @return A populated McpConfig object
     * @throws IllegalArgumentException if configFile is null or invalid
     * @throws IOException if the file cannot be read or parsed
     */
    public McpConfig load(File configFile) throws IOException {
        validateConfigFile(configFile);
        
        logger.info("Loading MCP configuration from: {}", configFile.getAbsolutePath());
        
        try {
            McpConfig config = jsonMapper.readValue(configFile, McpConfig.class);
            validateConfiguration(config);
            
            int serverCount = countServers(config);
            logger.info("Successfully loaded MCP configuration with {} servers", serverCount);
            
            return config;
        } catch (IOException e) {
            String errorMessage = String.format(
                "Failed to parse MCP configuration file '%s': %s",
                configFile.getName(), e.getMessage()
            );
            logger.error(errorMessage, e);
            throw new IOException(errorMessage, e);
        }
    }
    
    /**
     * Loads MCP configuration from a file path string.
     * 
     * @param configPath Path to the MCP configuration file
     * @return A populated McpConfig object
     * @throws IllegalArgumentException if configPath is null or empty
     * @throws IOException if the file cannot be read or parsed
     */
    public McpConfig load(String configPath) throws IOException {
        if (isNullOrEmpty(configPath)) {
            throw new IllegalArgumentException("Configuration file path cannot be null or empty");
        }
        
        return load(new File(configPath));
    }
    
    /**
     * Attempts to load configuration from default locations.
     * Searches in order:
     * 1. Current directory
     * 2. User home directory
     * 3. User's .config/mcp directory
     * 
     * @return The loaded configuration, or null if not found
     */
    public McpConfig loadFromDefaultLocation() {
        String[] searchPaths = getDefaultSearchPaths();
        
        for (String path : searchPaths) {
            File configFile = new File(path);
            if (configFile.exists()) {
                try {
                    logger.info("Found MCP configuration at: {}", path);
                    return load(configFile);
                } catch (IOException e) {
                    logger.warn("Failed to load configuration from {}: {}", 
                               path, e.getMessage());
                }
            }
        }
        
        logger.debug("No MCP configuration found in default locations");
        return null;
    }
    
    /**
     * Creates a sample MCP configuration file at the specified location.
     * 
     * @param targetFile The file to create
     * @throws IOException if the file cannot be written
     */
    public void createSampleConfiguration(File targetFile) throws IOException {
        Objects.requireNonNull(targetFile, "Target file cannot be null");
        
        String sampleContent = getSampleConfigurationContent();
        
        Path targetPath = targetFile.toPath();
        Files.writeString(targetPath, sampleContent);
        
        logger.info("Created sample MCP configuration at: {}", targetFile.getAbsolutePath());
    }
    
    // ===== Validation Methods =====
    
    /**
     * Validates the configuration file before attempting to load it.
     * 
     * @param configFile The file to validate
     * @throws IllegalArgumentException if the file is invalid
     * @throws IOException if the file cannot be accessed
     */
    private void validateConfigFile(File configFile) throws IOException {
        Objects.requireNonNull(configFile, "Configuration file cannot be null");
        
        if (!configFile.exists()) {
            throw new IOException("MCP configuration file not found: " + configFile.getAbsolutePath());
        }
        
        if (!configFile.isFile()) {
            throw new IOException("Path is not a file: " + configFile.getAbsolutePath());
        }
        
        if (!configFile.canRead()) {
            throw new IOException("File is not readable: " + configFile.getAbsolutePath());
        }
        
        checkFileExtension(configFile);
        checkFileSize(configFile);
    }
    
    /**
     * Checks if the file has the expected .json extension.
     * 
     * @param configFile The file to check
     */
    private void checkFileExtension(File configFile) {
        String fileName = configFile.getName().toLowerCase();
        if (!fileName.endsWith(JSON_FILE_EXTENSION)) {
            logger.warn("Configuration file '{}' does not have {} extension", 
                       configFile.getName(), JSON_FILE_EXTENSION);
        }
    }
    
    /**
     * Checks if the file size is within acceptable limits.
     * 
     * @param configFile The file to check
     * @throws IOException if the file is too large or empty
     */
    private void checkFileSize(File configFile) throws IOException {
        long fileSize = configFile.length();
        
        if (fileSize == 0) {
            throw new IOException("Configuration file is empty");
        }
        
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IOException(String.format(
                "Configuration file too large: %.2f MB (max: %d MB)", 
                fileSize / (1024.0 * 1024.0), MAX_FILE_SIZE_MB
            ));
        }
    }
    
    /**
     * Validates the loaded configuration object.
     * 
     * @param config The configuration to validate
     * @throws IllegalArgumentException if the configuration is invalid
     */
    private void validateConfiguration(McpConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Loaded configuration is null");
        }
        
        if (config.getMcpServers() == null || config.getMcpServers().isEmpty()) {
            logger.warn("Configuration contains no server definitions");
            return;
        }
        
        // Validate each server entry
        config.getMcpServers().forEach(this::validateServerEntry);
    }
    
    /**
     * Validates a single MCP server entry.
     * 
     * @param serverName The server name
     * @param serverEntry The server configuration entry
     */
    private void validateServerEntry(String serverName, McpConfig.McpServerEntry serverEntry) {
        if (isNullOrEmpty(serverName)) {
            throw new IllegalArgumentException("Server name cannot be empty");
        }
        
        if (serverEntry == null) {
            throw new IllegalArgumentException(
                String.format("Server '%s' has null configuration", serverName)
            );
        }
        
        if (isNullOrEmpty(serverEntry.getCommand())) {
            throw new IllegalArgumentException(
                String.format("Server '%s' has no command specified", serverName)
            );
        }
        
        logger.debug("Validated server '{}' with command: {}", 
                    serverName, serverEntry.getCommand());
    }
    
    // ===== Helper Methods =====
    
    /**
     * Gets the default search paths for configuration files.
     * 
     * @return Array of paths to search
     */
    private String[] getDefaultSearchPaths() {
        String userHome = System.getProperty("user.home");
        String separator = File.separator;
        
        return new String[] {
            CONFIG_FILENAME,  // Current directory
            userHome + separator + CONFIG_FILENAME,  // User home
            userHome + separator + CONFIG_DIR + separator + CONFIG_FILENAME  // Config directory
        };
    }
    
    /**
     * Gets sample configuration content.
     * 
     * @return Sample JSON configuration
     */
    private String getSampleConfigurationContent() {
        return """
            {
              "globalSettings": {
                "timeout": 60
              },
              "mcpServers": {
                "example-server": {
                  "command": "npx",
                  "args": ["-y", "@modelcontextprotocol/server-example"],
                  "env": {
                    "EXAMPLE_VAR": "value"
                  }
                },
                "another-server": {
                  "command": "python",
                  "args": ["-m", "mcp_server"],
                  "env": {
                    "API_KEY": "your-api-key-here"
                  }
                }
              }
            }
            """;
    }
    
    /**
     * Counts the number of servers in the configuration.
     * 
     * @param config The configuration to count servers in
     * @return The number of servers
     */
    private int countServers(McpConfig config) {
        if (config.getMcpServers() == null) {
            return 0;
        }
        return config.getMcpServers().size();
    }
    
    /**
     * Checks if a string is null or empty.
     * 
     * @param str The string to check
     * @return true if the string is null or empty
     */
    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
