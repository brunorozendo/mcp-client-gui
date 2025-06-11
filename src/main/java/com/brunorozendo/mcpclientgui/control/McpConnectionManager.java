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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages connections to multiple MCP servers, discovers their capabilities (tools, resources, prompts),
 * and provides methods to interact with them.
 */
public class McpConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(McpConnectionManager.class);

    // Maps a server's logical name to its active client connection
    private final Map<String, McpAsyncClient> clients = new ConcurrentHashMap<>();

    // Maps a capability name (e.g., a tool name) to the server that provides it
    private final Map<String, String> toolToServerMapping = new ConcurrentHashMap<>();
    private final Map<String, String> resourceToServerMapping = new ConcurrentHashMap<>();
    private final Map<String, String> promptToServerMapping = new ConcurrentHashMap<>();

    /**
     * Initializes clients for all servers defined in the configuration.
     *
     * @param mcpConfig The loaded MCP configuration.
     */
    public void initializeClients(McpConfig mcpConfig) {
        if (mcpConfig.getMcpServers() == null || mcpConfig.getMcpServers().isEmpty()) {
            logger.warn("No MCP servers defined in config. Cannot initialize any clients.");
            return;
        }

        mcpConfig.getMcpServers().forEach(this::initializeClient);
    }

    private void initializeClient(String serverName, McpConfig.McpServerEntry entry) {
        StdioClientTransport transport = null;
        try {
            // 1. Build Server Parameters and Transport
            transport = createStdioTransport(entry);

            // 2. Build the MCP Async Client
            McpAsyncClient client = McpClient.async(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .initializationTimeout(Duration.ofSeconds(60))
                    .clientInfo(new McpSchema.Implementation("mcphost-gui", "1.0"))
                    .build();

            // 3. Initialize the connection (blocking)
            logger.info("Initializing MCP Client for server: '{}' with command: {}", serverName, entry.getCommand());
            client.initialize().block(Duration.ofSeconds(60));

            // 4. If successful, store the client and discover its capabilities
            if (client.isInitialized()) {
                clients.put(serverName, client);
                logger.info("✅ MCP Client for server '{}' initialized successfully.", serverName);
                discoverAndMapCapabilities(serverName, client);
            } else {
                logger.error("❌ Failed to initialize MCP Client for server: {}", serverName);
                closeTransportGracefully(transport);
            }
        } catch (Exception e) {
            logger.error("❌ Error initializing MCP Client for server '{}': {}", serverName, e.getMessage(), e);
            closeTransportGracefully(transport);
        }
    }

    private StdioClientTransport createStdioTransport(McpConfig.McpServerEntry entry) {
        ServerParameters.Builder serverParamsBuilder = ServerParameters.builder(entry.getCommand())
                .args(entry.getArgs() != null ? entry.getArgs() : List.of());
        if (entry.getEnv() != null) {
            serverParamsBuilder.env(entry.getEnv());
        }
        return new StdioClientTransport(serverParamsBuilder.build());
    }

    private void discoverAndMapCapabilities(String serverName, McpAsyncClient client) {
        McpSchema.ServerCapabilities capabilities = client.getServerCapabilities();
        if (capabilities == null) {
            logger.warn("Server '{}' did not report any capabilities.", serverName);
            return;
        }

        if (capabilities.tools() != null) discoverTools(serverName, client);
        if (capabilities.resources() != null) discoverResources(serverName, client);
        if (capabilities.prompts() != null) discoverPrompts(serverName, client);
    }

    private void discoverTools(String serverName, McpAsyncClient client) {
        try {
            McpSchema.ListToolsResult toolsResult = client.listTools().block(Duration.ofSeconds(30));
            if (toolsResult != null && toolsResult.tools() != null) {
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    warnOnDuplicateMapping(toolToServerMapping, tool.name(), "tool", serverName);
                    toolToServerMapping.put(tool.name(), serverName);
                    logger.info("  -> Discovered Tool: {} (from server: {})", tool.name(), serverName);
                }
            }
        } catch (Exception e) {
            logger.error("Error discovering tools from server '{}': {}", serverName, e.getMessage());
        }
    }

    private void discoverResources(String serverName, McpAsyncClient client) {
        try {
            McpSchema.ListResourcesResult resourcesResult = client.listResources().block(Duration.ofSeconds(30));
            if (resourcesResult != null && resourcesResult.resources() != null) {
                for (McpSchema.Resource resource : resourcesResult.resources()) {
                    warnOnDuplicateMapping(resourceToServerMapping, resource.uri(), "resource", serverName);
                    resourceToServerMapping.put(resource.uri(), serverName);
                    logger.info("  -> Discovered Resource: {} (from server: {})", resource.uri(), serverName);
                }
            }
        } catch (Exception e) {
            logger.error("Error discovering resources from server '{}': {}", serverName, e.getMessage());
        }
    }

    private void discoverPrompts(String serverName, McpAsyncClient client) {
        try {
            McpSchema.ListPromptsResult promptsResult = client.listPrompts().block(Duration.ofSeconds(30));
            if (promptsResult != null && promptsResult.prompts() != null) {
                for (McpSchema.Prompt prompt : promptsResult.prompts()) {
                    warnOnDuplicateMapping(promptToServerMapping, prompt.name(), "prompt", serverName);
                    promptToServerMapping.put(prompt.name(), serverName);
                    logger.info("  -> Discovered Prompt: {} (from server: {})", prompt.name(), serverName);
                }
            }
        } catch (Exception e) {
            logger.error("Error discovering prompts from server '{}': {}", serverName, e.getMessage());
        }
    }

    private void warnOnDuplicateMapping(Map<String, String> map, String key, String type, String newServer) {
        if (map.containsKey(key)) {
            logger.warn("Duplicate {} name '{}' found. Previous mapping from server '{}' will be overwritten by server '{}'.",
                    type, key, map.get(key), newServer);
        }
    }

    /**
     * Gathers all unique tools from all connected and initialized servers.
     */
    public List<McpSchema.Tool> getAllTools() {
        return toolToServerMapping.keySet().stream()
                .map(this::getToolByName)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Gathers all unique resources from all connected and initialized servers.
     */
    public List<McpSchema.Resource> getAllResources() {
        return resourceToServerMapping.keySet().stream()
                .map(this::getResourceByUri)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Gathers all unique prompts from all connected and initialized servers.
     */
    public List<McpSchema.Prompt> getAllPrompts() {
        return promptToServerMapping.keySet().stream()
                .map(this::getPromptByName)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    private java.util.Optional<McpSchema.Tool> getToolByName(String toolName) {
        String serverName = toolToServerMapping.get(toolName);
        McpAsyncClient client = clients.get(serverName);
        if (client != null && client.isInitialized()) {
            try {
                return client.listTools().block(Duration.ofSeconds(10)).tools().stream()
                        .filter(t -> t.name().equals(toolName))
                        .findFirst();
            } catch (Exception e) {
                logger.error("Error getting tool '{}' from server '{}': {}", toolName, serverName, e.getMessage());
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<McpSchema.Resource> getResourceByUri(String resourceUri) {
        String serverName = resourceToServerMapping.get(resourceUri);
        McpAsyncClient client = clients.get(serverName);
        if (client != null && client.isInitialized()) {
            try {
                return client.listResources().block(Duration.ofSeconds(10)).resources().stream()
                        .filter(r -> r.uri().equals(resourceUri))
                        .findFirst();
            } catch (Exception e) {
                logger.error("Error getting resource '{}' from server '{}': {}", resourceUri, serverName, e.getMessage());
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<McpSchema.Prompt> getPromptByName(String promptName) {
        String serverName = promptToServerMapping.get(promptName);
        McpAsyncClient client = clients.get(serverName);
        if (client != null && client.isInitialized()) {
            try {
                return client.listPrompts().block(Duration.ofSeconds(10)).prompts().stream()
                        .filter(p -> p.name().equals(promptName))
                        .findFirst();
            } catch (Exception e) {
                logger.error("Error getting prompt '{}' from server '{}': {}", promptName, serverName, e.getMessage());
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Calls a specific tool with the given arguments.
     */
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        String serverName = toolToServerMapping.get(toolName);
        if (serverName == null) {
            String errorMsg = "Error: Tool '" + toolName + "' not found or its server is not mapped.";
            logger.error(errorMsg);
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(errorMsg)), true);
        }

        McpAsyncClient client = clients.get(serverName);
        if (client == null || !client.isInitialized()) {
            String errorMsg = "Error: Client for tool '" + toolName + "' is not available or not initialized.";
            logger.error(errorMsg);
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(errorMsg)), true);
        }

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
        try {
            logger.info("Calling tool '{}' on server '{}' with args: {}", toolName, serverName, arguments);
            // Block for the result, as this is part of a synchronous workflow
            return client.callTool(request).block(Duration.ofSeconds(120));
        } catch (Exception e) {
            logger.error("Error calling tool '{}': {}", toolName, e.getMessage(), e);
            String errorMsg = "Error calling tool '" + toolName + "': " + e.getMessage();
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(errorMsg)), true);
        }
    }

    /**
     * Closes all active client connections gracefully.
     */
    public void closeAllClients() {
        logger.info("Closing all MCP clients...");
        clients.forEach((serverName, client) -> {
            try {
                if (client.isInitialized()) {
                    logger.debug("Closing client for server '{}'", serverName);
                    client.closeGracefully().block(Duration.ofSeconds(10));
                }
            } catch (Exception e) {
                logger.error("Error closing MCP client for server '{}': {}", serverName, e.getMessage());
            }
        });
        clients.clear();
        toolToServerMapping.clear();
        resourceToServerMapping.clear();
        promptToServerMapping.clear();
        logger.info("All MCP clients have been closed.");
    }

    private void closeTransportGracefully(StdioClientTransport transport) {
        if (transport != null) {
            try {
                transport.closeGracefully().block(Duration.ofSeconds(5));
            } catch (Exception ce) {
                logger.error("Error closing transport for a failed server connection: {}", ce.getMessage());
            }
        }
    }
}
