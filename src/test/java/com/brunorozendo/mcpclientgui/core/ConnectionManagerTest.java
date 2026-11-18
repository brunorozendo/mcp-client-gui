package com.brunorozendo.mcpclientgui.core;

import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Initialization with and without usable MCP servers. No network call is made: the only MCP server
 * started is {@link FakeMcpStdioServer}, and neither LLM provider is contacted during initialization.
 */
class ConnectionManagerTest {

    @TempDir
    Path tempDir;

    private final List<ConnectionManager> managers = new ArrayList<>();

    @AfterEach
    void closeMcpClients() {
        // Stops any MCP server child process
        for (ConnectionManager manager : managers) {
            if (manager.getMcpConnectionManager() != null) {
                manager.getMcpConnectionManager().closeAllClients();
            }
        }
    }

    private ConnectionManager.ConnectionStatus initialize(ConnectionManager manager) throws Exception {
        return manager.initialize().get(30, TimeUnit.SECONDS);
    }

    private void assertReadyWithoutTools(ConnectionManager manager, ConnectionManager.ConnectionStatus status) {
        assertTrue(status.isSuccess(), status.getMessage());
        assertEquals(0, status.getToolCount());
        assertTrue(manager.isInitialized());
        assertNotNull(manager.getMcpConnectionManager());
        assertEquals(0, manager.getMcpConnectionManager().getConnectedServerCount());
        assertTrue(manager.getMcpTools().isEmpty());
        assertTrue(manager.getMcpResources().isEmpty());
        assertTrue(manager.getMcpPrompts().isEmpty());
        assertTrue(manager.getOllamaTools().isEmpty());
        assertTrue(manager.getAnthropicTools().isEmpty());
        assertNotNull(manager.getOllamaApiClient());
    }

    @Test
    void initializesWithoutMcpConfig() throws Exception {
        AppSettings settings = new AppSettings();
        ConnectionManager manager = new ConnectionManager(settings);

        ConnectionManager.ConnectionStatus status = initialize(manager);

        assertReadyWithoutTools(manager, status);
        assertTrue(status.getMessage().contains("No MCP servers configured"), status.getMessage());
    }

    @Test
    void initializesWhenMcpConfigFileDoesNotExist() throws Exception {
        AppSettings settings = new AppSettings();
        settings.setMcpConfigFile(tempDir.resolve("missing.json").toFile());
        ConnectionManager manager = new ConnectionManager(settings);

        ConnectionManager.ConnectionStatus status = initialize(manager);

        assertReadyWithoutTools(manager, status);
        assertTrue(status.getMessage().contains("MCP tools unavailable"), status.getMessage());
        assertTrue(status.getMessage().contains("not found"), status.getMessage());
    }

    @Test
    void initializesWhenMcpConfigIsInvalidJson() throws Exception {
        File config = Files.writeString(tempDir.resolve("mcp.json"), "{ this is not json").toFile();
        AppSettings settings = new AppSettings();
        settings.setMcpConfigFile(config);
        ConnectionManager manager = new ConnectionManager(settings);

        ConnectionManager.ConnectionStatus status = initialize(manager);

        assertReadyWithoutTools(manager, status);
        assertTrue(status.getMessage().contains("MCP tools unavailable"), status.getMessage());
        assertFalse(status.getMessage().contains("\n"), status.getMessage());
    }

    @Test
    void initializesWithBlankOllamaUrl() throws Exception {
        AppSettings settings = new AppSettings();
        settings.setOllamaBaseUrl(null);
        ConnectionManager manager = new ConnectionManager(settings);

        ConnectionManager.ConnectionStatus status = initialize(manager);

        assertReadyWithoutTools(manager, status);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void connectsToStdioMcpServerAndCallsItsTool() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Map<String, Object> config = Map.of("mcpServers", Map.of("fake", Map.of(
            "command", java,
            "args", List.of("-cp", System.getProperty("java.class.path"), FakeMcpStdioServer.class.getName()))));
        File configFile = tempDir.resolve("mcp.json").toFile();
        new ObjectMapper().writeValue(configFile, config);
        AppSettings settings = new AppSettings();
        settings.setMcpConfigFile(configFile);
        ConnectionManager manager = new ConnectionManager(settings);
        managers.add(manager);

        ConnectionManager.ConnectionStatus status = initialize(manager);

        assertTrue(status.isSuccess(), status.getMessage());
        assertEquals("Connected to 1 tools from MCP servers.", status.getMessage());
        assertEquals(1, status.getToolCount());
        assertEquals(1, manager.getMcpTools().size());
        assertEquals("echo", manager.getMcpTools().get(0).name());
        assertEquals(1, manager.getMcpConnectionManager().getConnectedServerCount());

        McpSchema.CallToolResult result = manager.getMcpConnectionManager().callTool("echo", Map.of("text", "hello"));

        assertFalse(Boolean.TRUE.equals(result.isError()), String.valueOf(result));
        assertEquals(1, result.content().size());
        McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
        assertEquals("echo: hello", text.text());
    }
}
