package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.McpConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Loads the MCP configuration from a JSON file.
 */
public class McpConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(McpConfigLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Loads the configuration from the specified file.
     *
     * @param configFile The mcp.json file.
     * @return A populated McpConfig object.
     * @throws IOException if the file does not exist or cannot be parsed.
     */
    public McpConfig load(File configFile) throws IOException {
        if (!configFile.exists()) {
            String errorMsg = "MCP config file not found: " + configFile.getAbsolutePath();
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        logger.info("Loading MCP config from: {}", configFile.getAbsolutePath());
        return objectMapper.readValue(configFile, McpConfig.class);
    }
}
