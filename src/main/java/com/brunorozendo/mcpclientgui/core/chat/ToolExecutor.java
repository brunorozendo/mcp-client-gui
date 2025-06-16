package com.brunorozendo.mcpclientgui.core.chat;

import com.brunorozendo.mcpclientgui.control.McpConnectionManager;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Handles the execution of tool calls requested by the AI assistant.
 * This class manages the interaction with MCP servers and formats the results
 * for inclusion in the conversation.
 */
public class ToolExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolExecutor.class);
    
    private static final String ERROR_FORMAT = "Error executing tool '%s': %s";
    private static final String ERROR_FROM_TOOL_FORMAT = "Error from tool '%s': %s";
    private static final String SUCCESS_NO_OUTPUT_FORMAT = "Tool '%s' executed successfully with no output.";
    private static final String NULL_ARGUMENTS_ERROR = "No arguments provided";
    
    private final McpConnectionManager mcpConnectionManager;
    
    /**
     * Tool execution result containing the formatted output and any error information.
     */
    public static class ToolResult {
        private final String output;
        private final boolean success;
        private final String toolName;
        
        private ToolResult(String toolName, String output, boolean success) {
            this.toolName = toolName;
            this.output = output;
            this.success = success;
        }
        
        public static ToolResult success(String toolName, String output) {
            return new ToolResult(toolName, output, true);
        }
        
        public static ToolResult error(String toolName, String errorMessage) {
            return new ToolResult(toolName, 
                String.format(ERROR_FORMAT, toolName, errorMessage), false);
        }
        
        public String getOutput() { return output; }
        public boolean isSuccess() { return success; }
        public String getToolName() { return toolName; }
    }
    
    /**
     * Creates a new tool executor.
     * 
     * @param mcpConnectionManager The MCP connection manager for executing tools
     * @throws NullPointerException if mcpConnectionManager is null
     */
    public ToolExecutor(McpConnectionManager mcpConnectionManager) {
        this.mcpConnectionManager = Objects.requireNonNull(
            mcpConnectionManager, "MCP connection manager cannot be null");
    }
    
    /**
     * Executes a single tool call and returns the result.
     * 
     * @param toolCall The tool call to execute
     * @return The execution result
     * @throws NullPointerException if toolCall is null
     */
    public ToolResult execute(OllamaApi.ToolCall toolCall) {
        Objects.requireNonNull(toolCall, "Tool call cannot be null");
        
        OllamaApi.FunctionCall function = toolCall.function();
        if (function == null) {
            logger.error("Tool call has null function");
            return ToolResult.error("unknown", "Tool call has no function");
        }
        
        String toolName = function.name();
        Map<String, Object> arguments = function.arguments();
        
        logger.info("Executing tool: {} with args: {}", toolName, arguments);
        
        // Validate arguments
        if (arguments == null) {
            logger.error("Tool '{}' called with null arguments", toolName);
            return ToolResult.error(toolName, NULL_ARGUMENTS_ERROR);
        }
        
        try {
            // Execute the tool through MCP
            McpSchema.CallToolResult mcpResult = mcpConnectionManager.callTool(toolName, arguments);
            
            // Format the result
            String formattedResult = formatToolResult(toolName, mcpResult);
            
            logger.info("Tool '{}' executed successfully", toolName);
            return ToolResult.success(toolName, formattedResult);
            
        } catch (Exception e) {
            logger.error("Error executing tool '{}'", toolName, e);
            return ToolResult.error(toolName, e.getMessage());
        }
    }
    
    /**
     * Executes multiple tool calls in sequence.
     * 
     * @param toolCalls The list of tool calls to execute
     * @return List of execution results
     * @throws NullPointerException if toolCalls is null
     */
    public List<ToolResult> executeAll(List<OllamaApi.ToolCall> toolCalls) {
        Objects.requireNonNull(toolCalls, "Tool calls cannot be null");
        
        logger.info("Executing {} tool calls", toolCalls.size());
        
        return toolCalls.stream()
                .map(this::execute)
                .collect(Collectors.toList());
    }
    
    /**
     * Formats a tool result from MCP into a string for the conversation.
     * 
     * @param toolName The name of the tool
     * @param result The MCP result
     * @return Formatted result string
     */
    private String formatToolResult(String toolName, McpSchema.CallToolResult result) {
        if (result == null) {
            return String.format(ERROR_FORMAT, toolName, "Null result from MCP");
        }
        
        // Extract text content from the result
        String content = extractTextContent(result);
        
        // Handle empty content
        if (content.isEmpty()) {
            content = String.format(SUCCESS_NO_OUTPUT_FORMAT, toolName);
        }
        
        // Add error prefix if this was an error
        if (Boolean.TRUE.equals(result.isError())) {
            return String.format(ERROR_FROM_TOOL_FORMAT, toolName, content);
        }
        
        return content;
    }
    
    /**
     * Extracts text content from an MCP result.
     * 
     * @param result The MCP result
     * @return The extracted text content
     */
    private String extractTextContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        
        // Try to extract text content
        String textContent = result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .filter(text -> text != null)
                .collect(Collectors.joining("\n"));
        
        // Fallback to toString for non-text content
        if (textContent.isEmpty() && !result.content().isEmpty()) {
            textContent = result.content().get(0).toString();
        }
        
        return textContent;
    }
    
    /**
     * Validates that a tool name exists in the MCP connection manager.
     * 
     * @param toolName The tool name to validate
     * @return true if the tool exists
     */
    public boolean isToolAvailable(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        
        return mcpConnectionManager.getAllTools().stream()
                .anyMatch(tool -> toolName.equals(tool.name()));
    }
    
    /**
     * Gets information about a specific tool.
     * 
     * @param toolName The tool name
     * @return The tool schema, or null if not found
     */
    public McpSchema.Tool getToolInfo(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        
        return mcpConnectionManager.getAllTools().stream()
                .filter(tool -> toolName.equals(tool.name()))
                .findFirst()
                .orElse(null);
    }
}
