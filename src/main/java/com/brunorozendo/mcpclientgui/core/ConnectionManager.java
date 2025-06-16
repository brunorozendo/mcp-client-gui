package com.brunorozendo.mcpclientgui.core;

import com.brunorozendo.mcpclientgui.control.McpConnectionManager;
import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.brunorozendo.mcpclientgui.model.McpConfig;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.brunorozendo.mcpclientgui.service.McpConfigLoader;
import com.brunorozendo.mcpclientgui.service.OllamaApiClient;
import com.brunorozendo.mcpclientgui.util.SchemaConverter;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages connections to MCP servers and the AI client.
 * This class handles initialization and provides access to MCP capabilities.
 */
public class ConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    
    private final AppSettings settings;
    private McpConnectionManager mcpConnectionManager;
    private OllamaApiClient ollamaApiClient;
    
    // Cached MCP capabilities
    private List<McpSchema.Tool> mcpTools;
    private List<McpSchema.Resource> mcpResources;
    private List<McpSchema.Prompt> mcpPrompts;
    
    private boolean initialized = false;
    
    /**
     * Connection status information.
     */
    public static class ConnectionStatus {
        private final boolean success;
        private final String message;
        private final int toolCount;
        
        public ConnectionStatus(boolean success, String message, int toolCount) {
            this.success = success;
            this.message = message;
            this.toolCount = toolCount;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getToolCount() { return toolCount; }
    }
    
    /**
     * Creates a new ConnectionManager with the specified settings.
     * 
     * @param settings The application settings containing connection configuration
     */
    public ConnectionManager(AppSettings settings) {
        this.settings = settings;
    }
    
    /**
     * Initializes connections to MCP servers and AI client asynchronously.
     * 
     * @return A CompletableFuture that completes with the connection status
     */
    public CompletableFuture<ConnectionStatus> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            if (!settings.isValid()) {
                return new ConnectionStatus(false, 
                    "Invalid settings. Please check your configuration.", 0);
            }
            
            try {
                logger.info("Initializing connections...");
                
                // Load MCP configuration
                McpConfig mcpConfig = loadMcpConfig();
                
                // Initialize MCP connections
                initializeMcpConnections(mcpConfig);
                
                // Initialize Ollama client
                initializeOllamaClient();
                
                // Fetch MCP capabilities
                fetchCapabilities();
                
                initialized = true;
                
                return new ConnectionStatus(true, 
                    String.format("Connected to %d tools from MCP servers.", mcpTools.size()),
                    mcpTools.size());
                
            } catch (Exception e) {
                logger.error("Error initializing connections", e);
                return new ConnectionStatus(false, 
                    "Failed to initialize: " + e.getMessage(), 0);
            }
        });
    }
    
    /**
     * Checks if the connection manager is initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Gets the MCP connection manager.
     * 
     * @return The MCP connection manager, or null if not initialized
     */
    public McpConnectionManager getMcpConnectionManager() {
        return mcpConnectionManager;
    }
    
    /**
     * Gets the Ollama API client.
     * 
     * @return The Ollama API client, or null if not initialized
     */
    public OllamaApiClient getOllamaApiClient() {
        return ollamaApiClient;
    }
    
    /**
     * Gets all available MCP tools.
     * 
     * @return List of MCP tools, or null if not initialized
     */
    public List<McpSchema.Tool> getMcpTools() {
        return mcpTools;
    }
    
    /**
     * Gets all available MCP resources.
     * 
     * @return List of MCP resources, or null if not initialized
     */
    public List<McpSchema.Resource> getMcpResources() {
        return mcpResources;
    }
    
    /**
     * Gets all available MCP prompts.
     * 
     * @return List of MCP prompts, or null if not initialized
     */
    public List<McpSchema.Prompt> getMcpPrompts() {
        return mcpPrompts;
    }
    
    /**
     * Converts MCP tools to Ollama format.
     * 
     * @return List of tools in Ollama format
     * @throws IllegalStateException if not initialized
     */
    public List<OllamaApi.Tool> getOllamaTools() {
        if (!initialized || mcpTools == null) {
            throw new IllegalStateException("Connection manager not initialized");
        }
        
        return SchemaConverter.convertMcpToolsToOllamaTools(mcpTools);
    }
    
    /**
     * Closes all connections and releases resources.
     */
    public void close() {
        if (mcpConnectionManager != null) {
            try {
                logger.info("Closing MCP connections...");
                // Add close logic if needed
            } catch (Exception e) {
                logger.error("Error closing MCP connections", e);
            }
        }
        
        initialized = false;
        mcpTools = null;
        mcpResources = null;
        mcpPrompts = null;
    }
    
    /**
     * Loads the MCP configuration from file.
     * 
     * @return The loaded MCP configuration
     * @throws Exception if loading fails
     */
    private McpConfig loadMcpConfig() throws Exception {
        McpConfigLoader configLoader = new McpConfigLoader();
        return configLoader.load(settings.getMcpConfigFile());
    }
    
    /**
     * Initializes the MCP connection manager.
     * 
     * @param mcpConfig The MCP configuration to use
     * @throws Exception if initialization fails
     */
    private void initializeMcpConnections(McpConfig mcpConfig) throws Exception {
        mcpConnectionManager = new McpConnectionManager();
        mcpConnectionManager.initializeClients(mcpConfig);
        logger.info("MCP connections initialized");
    }
    
    /**
     * Initializes the Ollama API client.
     */
    private void initializeOllamaClient() {
        ollamaApiClient = new OllamaApiClient(settings.getOllamaBaseUrl());
        logger.info("Ollama API client initialized with base URL: {}", settings.getOllamaBaseUrl());
    }
    
    /**
     * Fetches all capabilities from connected MCP servers.
     */
    private void fetchCapabilities() {
        logger.info("Fetching MCP capabilities...");
        
        mcpTools = mcpConnectionManager.getAllTools();
        mcpResources = mcpConnectionManager.getAllResources();
        mcpPrompts = mcpConnectionManager.getAllPrompts();
        
        logger.info("Fetched {} tools, {} resources, {} prompts", 
                   mcpTools.size(), mcpResources.size(), mcpPrompts.size());
    }
}
