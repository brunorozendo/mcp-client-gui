package com.brunorozendo.mcpclientgui.control;

import com.brunorozendo.mcpclientgui.model.McpConfig;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages connections to multiple MCP (Model Context Protocol) servers.
 * 
 * This manager handles:
 * - Establishing connections to MCP servers defined in configuration
 * - Discovering and mapping server capabilities (tools, resources, prompts)
 * - Routing tool calls to the appropriate server
 * - Managing connection lifecycle and graceful shutdown
 * 
 * The manager maintains a registry of which server provides which capability,
 * allowing transparent routing of requests to the appropriate server.
 */
public class McpConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(McpConnectionManager.class);

    // Configuration constants
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TOOL_CALL_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TRANSPORT_CLOSE_TIMEOUT = Duration.ofSeconds(5);
    
    // Status indicators
    private static final String STATUS_SUCCESS = "✅";
    private static final String STATUS_FAILURE = "❌";
    private static final String CAPABILITY_ARROW = "  → ";
    
    // Client information
    private static final String CLIENT_NAME = "mcphost-gui";
    private static final String CLIENT_VERSION = "1.0";

    // Connection and capability mappings
    private final Map<String, McpAsyncClient> clients = new ConcurrentHashMap<>();
    private final Map<String, String> toolToServerMapping = new ConcurrentHashMap<>();
    private final Map<String, String> resourceToServerMapping = new ConcurrentHashMap<>();
    private final Map<String, String> promptToServerMapping = new ConcurrentHashMap<>();

    /**
     * Initializes connections to all servers defined in the configuration.
     * 
     * Each server is initialized in sequence. Failed connections are logged
     * but don't prevent other servers from being initialized.
     *
     * @param mcpConfig The MCP configuration containing server definitions
     * @throws IllegalArgumentException if mcpConfig is null
     */
    public void initializeClients(McpConfig mcpConfig) {
        Objects.requireNonNull(mcpConfig, "MCP configuration cannot be null");
        
        if (mcpConfig.getMcpServers() == null || mcpConfig.getMcpServers().isEmpty()) {
            logger.warn("No MCP servers defined in configuration. No clients will be initialized.");
            return;
        }

        logger.info("Initializing {} MCP server(s)...", mcpConfig.getMcpServers().size());
        mcpConfig.getMcpServers().forEach(this::initializeClient);
        
        logInitializationSummary();
    }

    /**
     * Initializes a single MCP server client.
     * 
     * @param serverName The logical name of the server
     * @param entry The server configuration entry
     */
    private void initializeClient(String serverName, McpConfig.McpServerEntry entry) {
        StdioClientTransport transport = null;
        
        try {
            logger.info("Initializing MCP client for server: '{}'", serverName);
            
            // Create transport
            transport = createStdioTransport(entry);

            // Build and initialize client
            McpAsyncClient client = buildMcpClient(transport);
            initializeConnection(client, serverName);

            // Store client and discover capabilities
            if (client.isInitialized()) {
                clients.put(serverName, client);
                logger.info("{} MCP client for server '{}' initialized successfully", STATUS_SUCCESS, serverName);
                discoverAndMapCapabilities(serverName, client);
            } else {
                logger.error("{} Failed to initialize MCP client for server: {}", STATUS_FAILURE, serverName);
                closeTransportGracefully(transport);
            }
        } catch (Exception e) {
            logger.error("{} Error initializing MCP client for server '{}': {}", STATUS_FAILURE, serverName, e.getMessage(), e);
            closeTransportGracefully(transport);
        }
    }

    /**
     * Creates a stdio transport for the given server configuration.
     * 
     * @param entry The server configuration entry
     * @return Configured StdioClientTransport
     */
    private StdioClientTransport createStdioTransport(McpConfig.McpServerEntry entry) {
        ServerParameters.Builder paramsBuilder = ServerParameters.builder(entry.getCommand());
        
        // Add arguments if provided
        if (entry.getArgs() != null && !entry.getArgs().isEmpty()) {
            paramsBuilder.args(entry.getArgs());
            logger.debug("Server command with args: {} {}", entry.getCommand(), String.join(" ", entry.getArgs()));
        }
        
        // Add environment variables if provided
        if (entry.getEnv() != null && !entry.getEnv().isEmpty()) {
            paramsBuilder.env(entry.getEnv());
            logger.debug("Server environment variables: {}", entry.getEnv().keySet());
        }
        
        return new StdioClientTransport(paramsBuilder.build());
    }
    
    /**
     * Builds an MCP async client with the specified transport.
     * 
     * @param transport The transport to use
     * @return Configured McpAsyncClient
     */
    private McpAsyncClient buildMcpClient(StdioClientTransport transport) {
        return McpClient.async(transport)
                .requestTimeout(DEFAULT_REQUEST_TIMEOUT)
                .initializationTimeout(INITIALIZATION_TIMEOUT)
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .build();
    }
    
    /**
     * Initializes the connection for a client.
     * 
     * @param client The client to initialize
     * @param serverName The server name for logging
     * @throws RuntimeException if initialization times out
     */
    private void initializeConnection(McpAsyncClient client, String serverName) {
        logger.debug("Establishing connection to server '{}'...", serverName);
        client.initialize().block(INITIALIZATION_TIMEOUT);
    }

    /**
     * Discovers and maps all capabilities from a connected server.
     * 
     * @param serverName The server name
     * @param client The initialized client
     */
    private void discoverAndMapCapabilities(String serverName, McpAsyncClient client) {
        McpSchema.ServerCapabilities capabilities = client.getServerCapabilities();
        
        if (capabilities == null) {
            logger.warn("Server '{}' did not report any capabilities", serverName);
            return;
        }

        logger.info("Discovering capabilities for server '{}':", serverName);
        
        // Discover each type of capability
        if (capabilities.tools() != null) {
            discoverTools(serverName, client);
        }
        
        if (capabilities.resources() != null) {
            discoverResources(serverName, client);
        }
        
        if (capabilities.prompts() != null) {
            discoverPrompts(serverName, client);
        }
    }

    /**
     * Discovers and maps tools from a server.
     * 
     * @param serverName The server name
     * @param client The client to query
     */
    private void discoverTools(String serverName, McpAsyncClient client) {
        try {
            McpSchema.ListToolsResult toolsResult = client.listTools().block(DEFAULT_REQUEST_TIMEOUT);
            
            if (toolsResult != null && toolsResult.tools() != null) {
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    mapCapability(toolToServerMapping, tool.name(), serverName, "tool");
                    logger.info("{} Discovered tool: {} (from server: {})", CAPABILITY_ARROW, tool.name(), serverName);
                }
                
                logger.info("  Total tools discovered: {}", toolsResult.tools().size());
            }
        } catch (Exception e) {
            logger.error("Error discovering tools from server '{}': {}", serverName, e.getMessage());
        }
    }

    /**
     * Discovers and maps resources from a server.
     * 
     * @param serverName The server name
     * @param client The client to query
     */
    private void discoverResources(String serverName, McpAsyncClient client) {
        try {
            McpSchema.ListResourcesResult resourcesResult = client.listResources().block(DEFAULT_REQUEST_TIMEOUT);
            
            if (resourcesResult != null && resourcesResult.resources() != null) {
                for (McpSchema.Resource resource : resourcesResult.resources()) {
                    mapCapability(resourceToServerMapping, resource.uri(), serverName, "resource");
                    logger.info("{} Discovered resource: {} (from server: {})", CAPABILITY_ARROW, resource.uri(), serverName);
                }
                
                logger.info("  Total resources discovered: {}", resourcesResult.resources().size());
            }
        } catch (Exception e) {
            logger.error("Error discovering resources from server '{}': {}", serverName, e.getMessage());
        }
    }

    /**
     * Discovers and maps prompts from a server.
     * 
     * @param serverName The server name
     * @param client The client to query
     */
    private void discoverPrompts(String serverName, McpAsyncClient client) {
        try {
            McpSchema.ListPromptsResult promptsResult = client.listPrompts().block(DEFAULT_REQUEST_TIMEOUT);
            
            if (promptsResult != null && promptsResult.prompts() != null) {
                for (McpSchema.Prompt prompt : promptsResult.prompts()) {
                    mapCapability(promptToServerMapping, prompt.name(), serverName, "prompt");
                    logger.info("{} Discovered prompt: {} (from server: {})", CAPABILITY_ARROW, prompt.name(), serverName);
                }
                
                logger.info("  Total prompts discovered: {}", promptsResult.prompts().size());
            }
        } catch (Exception e) {
            logger.error("Error discovering prompts from server '{}': {}", serverName, e.getMessage());
        }
    }

    /**
     * Maps a capability to its providing server.
     * 
     * @param map The mapping to update
     * @param key The capability identifier
     * @param serverName The server providing the capability
     * @param type The type of capability for logging
     */
    private void mapCapability(Map<String, String> map, String key, String serverName, String type) {
        String existingServer = map.get(key);
        
        if (existingServer != null) {
            logger.warn("Duplicate {} '{}' found. Server '{}' is overriding server '{}'", 
                       type, key, serverName, existingServer);
        }
        
        map.put(key, serverName);
    }

    /**
     * Logs a summary of the initialization process.
     */
    private void logInitializationSummary() {
        int connectedServers = clients.size();
        int totalTools = toolToServerMapping.size();
        int totalResources = resourceToServerMapping.size();
        int totalPrompts = promptToServerMapping.size();
        
        logger.info("MCP initialization complete: {} servers connected, {} tools, {} resources, {} prompts available",
                   connectedServers, totalTools, totalResources, totalPrompts);
    }

    // ===== Capability Retrieval Methods =====

    /**
     * Gets all unique tools from all connected servers.
     * 
     * @return List of all available tools
     */
    public List<McpSchema.Tool> getAllTools() {
        return getCapabilities(toolToServerMapping, this::getToolByName);
    }

    /**
     * Gets all unique resources from all connected servers.
     * 
     * @return List of all available resources
     */
    public List<McpSchema.Resource> getAllResources() {
        return getCapabilities(resourceToServerMapping, this::getResourceByUri);
    }

    /**
     * Gets all unique prompts from all connected servers.
     * 
     * @return List of all available prompts
     */
    public List<McpSchema.Prompt> getAllPrompts() {
        return getCapabilities(promptToServerMapping, this::getPromptByName);
    }

    /**
     * Generic method to get capabilities from a mapping.
     * 
     * @param mapping The capability to server mapping
     * @param retriever Function to retrieve a capability by its identifier
     * @return List of capabilities
     */
    private <T> List<T> getCapabilities(Map<String, String> mapping, 
                                       java.util.function.Function<String, Optional<T>> retriever) {
        return mapping.keySet().stream()
                .map(retriever)
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific tool by name.
     * 
     * @param toolName The tool name
     * @return Optional containing the tool if found
     */
    private Optional<McpSchema.Tool> getToolByName(String toolName) {
        return getCapabilityFromServer(
            toolName, 
            toolToServerMapping.get(toolName),
            client -> client.listTools().block(DEFAULT_REQUEST_TIMEOUT).tools().stream()
                    .filter(t -> t.name().equals(toolName))
                    .findFirst()
        );
    }

    /**
     * Gets a specific resource by URI.
     * 
     * @param resourceUri The resource URI
     * @return Optional containing the resource if found
     */
    private Optional<McpSchema.Resource> getResourceByUri(String resourceUri) {
        return getCapabilityFromServer(
            resourceUri,
            resourceToServerMapping.get(resourceUri),
            client -> client.listResources().block(DEFAULT_REQUEST_TIMEOUT).resources().stream()
                    .filter(r -> r.uri().equals(resourceUri))
                    .findFirst()
        );
    }

    /**
     * Gets a specific prompt by name.
     * 
     * @param promptName The prompt name
     * @return Optional containing the prompt if found
     */
    private Optional<McpSchema.Prompt> getPromptByName(String promptName) {
        return getCapabilityFromServer(
            promptName,
            promptToServerMapping.get(promptName),
            client -> client.listPrompts().block(DEFAULT_REQUEST_TIMEOUT).prompts().stream()
                    .filter(p -> p.name().equals(promptName))
                    .findFirst()
        );
    }

    /**
     * Generic method to get a capability from its server.
     * 
     * @param capabilityId The capability identifier
     * @param serverName The server that provides it
     * @param getter Function to get the capability from the client
     * @return Optional containing the capability if found
     */
    private <T> Optional<T> getCapabilityFromServer(String capabilityId, String serverName,
                                                   java.util.function.Function<McpAsyncClient, Optional<T>> getter) {
        if (serverName == null) {
            logger.debug("No server mapping found for capability: {}", capabilityId);
            return Optional.empty();
        }
        
        McpAsyncClient client = clients.get(serverName);
        if (client == null || !client.isInitialized()) {
            logger.warn("Server '{}' is not available for capability: {}", serverName, capabilityId);
            return Optional.empty();
        }
        
        try {
            return getter.apply(client);
        } catch (Exception e) {
            logger.error("Error retrieving capability '{}' from server '{}': {}", 
                        capabilityId, serverName, e.getMessage());
            return Optional.empty();
        }
    }

    // ===== Tool Execution =====

    /**
     * Calls a specific tool with the given arguments.
     * 
     * Routes the call to the appropriate server based on the tool mapping.
     * 
     * @param toolName The name of the tool to call
     * @param arguments The arguments to pass to the tool
     * @return The result of the tool call
     * @throws NullPointerException if toolName is null
     */
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "Tool name cannot be null");
        
        // Find the server that provides this tool
        String serverName = toolToServerMapping.get(toolName);
        if (serverName == null) {
            return createErrorResult(String.format("Tool '%s' not found in any connected server", toolName));
        }

        // Get the client for the server
        McpAsyncClient client = clients.get(serverName);
        if (client == null || !client.isInitialized()) {
            return createErrorResult(String.format("Server '%s' for tool '%s' is not available", serverName, toolName));
        }

        // Execute the tool call
        return executeToolCall(client, serverName, toolName, arguments);
    }

    /**
     * Executes a tool call on a specific client.
     * 
     * @param client The client to use
     * @param serverName The server name for logging
     * @param toolName The tool name
     * @param arguments The tool arguments
     * @return The tool call result
     */
    private McpSchema.CallToolResult executeToolCall(McpAsyncClient client, String serverName, 
                                                    String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
        
        try {
            logger.info("Calling tool '{}' on server '{}' with args: {}", toolName, serverName, arguments);
            
            McpSchema.CallToolResult result = client.callTool(request).block(TOOL_CALL_TIMEOUT);
            
            if (result != null) {
                logger.info("Tool '{}' completed successfully", toolName);
                return result;
            } else {
                return createErrorResult(String.format("Tool '%s' returned null result", toolName));
            }
        } catch (Exception e) {
            logger.error("Error calling tool '{}' on server '{}': {}", toolName, serverName, e.getMessage(), e);
            return createErrorResult(String.format("Error calling tool '%s': %s", toolName, e.getMessage()));
        }
    }

    /**
     * Creates an error result for failed tool calls.
     * 
     * @param errorMessage The error message
     * @return Error result
     */
    private McpSchema.CallToolResult createErrorResult(String errorMessage) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(errorMessage)), 
            true
        );
    }

    // ===== Connection Management =====

    /**
     * Closes all active client connections gracefully.
     * 
     * This method should be called when shutting down the application
     * to ensure all MCP server connections are properly closed.
     */
    public void closeAllClients() {
        if (clients.isEmpty()) {
            logger.debug("No MCP clients to close");
            return;
        }
        
        logger.info("Closing {} MCP client(s)...", clients.size());
        
        clients.forEach((serverName, client) -> {
            try {
                if (client.isInitialized()) {
                    logger.debug("Closing client for server '{}'", serverName);
                    client.closeGracefully().block(CLOSE_TIMEOUT);
                    logger.debug("Successfully closed client for server '{}'", serverName);
                }
            } catch (Exception e) {
                logger.error("Error closing MCP client for server '{}': {}", serverName, e.getMessage());
            }
        });
        
        // Clear all mappings
        clients.clear();
        toolToServerMapping.clear();
        resourceToServerMapping.clear();
        promptToServerMapping.clear();
        
        logger.info("All MCP clients have been closed");
    }

    /**
     * Closes a transport gracefully, handling any errors.
     * 
     * @param transport The transport to close
     */
    private void closeTransportGracefully(StdioClientTransport transport) {
        if (transport == null) {
            return;
        }
        
        try {
            logger.debug("Closing transport...");
            transport.closeGracefully().block(TRANSPORT_CLOSE_TIMEOUT);
        } catch (Exception e) {
            logger.error("Error closing transport: {}", e.getMessage());
        }
    }

    // ===== Status Methods =====

    /**
     * Gets the number of connected servers.
     * 
     * @return The count of connected servers
     */
    public int getConnectedServerCount() {
        return (int) clients.values().stream()
                .filter(McpAsyncClient::isInitialized)
                .count();
    }

    /**
     * Checks if a specific server is connected.
     * 
     * @param serverName The server name to check
     * @return True if the server is connected and initialized
     */
    public boolean isServerConnected(String serverName) {
        McpAsyncClient client = clients.get(serverName);
        return client != null && client.isInitialized();
    }

    /**
     * Gets the names of all connected servers.
     * 
     * @return Set of connected server names
     */
    public Set<String> getConnectedServerNames() {
        return clients.entrySet().stream()
                .filter(entry -> entry.getValue().isInitialized())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
