package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.McpConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Service for loading MCP (Model Context Protocol) configuration from JSON files.
 * 
 * This loader handles:
 * - Reading and parsing MCP configuration files
 * - Validation of configuration structure
 * - Error handling for malformed or missing files
 * 
 * The expected configuration format follows the MCP specification,
 * typically containing server definitions with their commands and arguments.
 */
public class McpConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(McpConfigLoader.class);
    
    // Configuration constants
    private static final String CONFIG_FILE_EXTENSION = ".json";
    private static final long MAX_CONFIG_SIZE_BYTES = 10 * 1024 * 1024; // 10MB max
    
    private final ObjectMapper objectMapper;

    /**
     * Creates a new MCP configuration loader.
     */
    public McpConfigLoader() {
        this.objectMapper = createObjectMapper();
    }
    
    /**
     * Creates and configures the Jackson ObjectMapper for JSON parsing.
     * 
     * @return Configured ObjectMapper instance
     */
    private ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                // Don't fail on unknown properties (forward compatibility)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Allow comments in JSON files
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
                // Allow trailing commas for easier editing
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
    }

    /**
     * Loads MCP configuration from the specified file.
     * 
     * Validates that:
     * - The file exists and is readable
     * - The file has a .json extension
     * - The file size is reasonable
     * - The JSON content is valid and can be parsed
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
            McpConfig config = objectMapper.readValue(configFile, McpConfig.class);
            validateLoadedConfig(config);
            
            logger.info("Successfully loaded MCP configuration with {} servers", 
                       config.getMcpServers() != null ? config.getMcpServers().size() : 0);
            
            return config;
        } catch (IOException e) {
            logger.error("Failed to parse MCP configuration file: {}", configFile.getAbsolutePath(), e);
            throw new IOException("Failed to parse MCP configuration: " + e.getMessage(), e);
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
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Configuration file path cannot be null or empty");
        }
        
        return load(new File(configPath));
    }
    
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
            String errorMsg = String.format("MCP configuration file not found: %s", 
                                          configFile.getAbsolutePath());
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        
        if (!configFile.isFile()) {
            String errorMsg = String.format("MCP configuration path is not a file: %s", 
                                          configFile.getAbsolutePath());
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        
        if (!configFile.canRead()) {
            String errorMsg = String.format("MCP configuration file is not readable: %s", 
                                          configFile.getAbsolutePath());
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        
        validateFileExtension(configFile);
        validateFileSize(configFile);
    }
    
    /**
     * Validates that the file has the expected .json extension.
     * 
     * @param configFile The file to check
     * @throws IllegalArgumentException if the file doesn't have .json extension
     */
    private void validateFileExtension(File configFile) {
        String fileName = configFile.getName().toLowerCase();
        if (!fileName.endsWith(CONFIG_FILE_EXTENSION)) {
            logger.warn("MCP configuration file does not have .json extension: {}", 
                       configFile.getName());
            // We'll allow it but warn about it
        }
    }
    
    /**
     * Validates that the file size is reasonable.
     * 
     * @param configFile The file to check
     * @throws IOException if the file is too large
     */
    private void validateFileSize(File configFile) throws IOException {
        long fileSize = configFile.length();
        if (fileSize > MAX_CONFIG_SIZE_BYTES) {
            String errorMsg = String.format(
                "MCP configuration file is too large: %d bytes (max: %d bytes)", 
                fileSize, MAX_CONFIG_SIZE_BYTES
            );
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        
        if (fileSize == 0) {
            String errorMsg = "MCP configuration file is empty";
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
    }
    
    /**
     * Validates the loaded configuration object.
     * 
     * @param config The configuration to validate
     * @throws IllegalArgumentException if the configuration is invalid
     */
    private void validateLoadedConfig(McpConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Loaded configuration is null");
        }
        
        if (config.getMcpServers() == null || config.getMcpServers().isEmpty()) {
            logger.warn("MCP configuration contains no server definitions");
            // This is allowed but we warn about it
        } else {
            // Validate each server entry
            config.getMcpServers().forEach(this::validateServerEntry);
        }
    }
    
    /**
     * Validates a single MCP server entry.
     * 
     * @param serverName The server name
     * @param serverEntry The server configuration entry
     */
    private void validateServerEntry(String serverName, McpConfig.McpServerEntry serverEntry) {
        if (serverName == null || serverName.trim().isEmpty()) {
            logger.warn("MCP configuration contains server with empty name");
        }
        
        if (serverEntry == null) {
            logger.error("MCP server '{}' has null configuration", serverName);
            throw new IllegalArgumentException("Server '" + serverName + "' has null configuration");
        }
        
        if (serverEntry.getCommand() == null || serverEntry.getCommand().trim().isEmpty()) {
            logger.error("MCP server '{}' has no command specified", serverName);
            throw new IllegalArgumentException("Server '" + serverName + "' has no command specified");
        }
        
        logger.debug("Validated MCP server '{}' with command: {}", serverName, serverEntry.getCommand());
    }
    
    /**
     * Attempts to load configuration from a default location.
     * 
     * Looks for mcp.json in common locations:
     * - Current directory
     * - User home directory
     * - .config/mcp/ directory
     * 
     * @return The loaded configuration, or null if not found
     */
    public McpConfig loadFromDefaultLocation() {
        String[] defaultPaths = {
            "mcp.json",
            System.getProperty("user.home") + File.separator + "mcp.json",
            System.getProperty("user.home") + File.separator + ".config" + File.separator + "mcp" + File.separator + "mcp.json"
        };
        
        for (String path : defaultPaths) {
            File configFile = new File(path);
            if (configFile.exists()) {
                try {
                    logger.info("Found MCP configuration at default location: {}", path);
                    return load(configFile);
                } catch (IOException e) {
                    logger.warn("Failed to load MCP configuration from {}: {}", path, e.getMessage());
                }
            }
        }
        
        logger.debug("No MCP configuration found at default locations");
        return null;
    }
    
    /**
     * Creates a sample MCP configuration file at the specified location.
     * 
     * @param targetFile The file to create
     * @throws IOException if the file cannot be written
     */
    public void createSampleConfig(File targetFile) throws IOException {
        Objects.requireNonNull(targetFile, "Target file cannot be null");
        
        String sampleConfig = """
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
                }
              }
            }
            """;
        
        Path targetPath = targetFile.toPath();
        Files.writeString(targetPath, sampleConfig);
        
        logger.info("Created sample MCP configuration at: {}", targetFile.getAbsolutePath());
    }
}
