package com.brunorozendo.mcpclientgui.control;

import com.brunorozendo.mcpclientgui.util.SchemaConverter;
import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.brunorozendo.mcpclientgui.service.OllamaApiClient;
import io.modelcontextprotocol.spec.McpSchema;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages the chat session between the user, the LLM, and the MCP servers for the GUI application.
 */
public class GuiChatController {

    private static final Logger logger = LoggerFactory.getLogger(GuiChatController.class);
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final String ollamaModelName;
    private final OllamaApiClient ollamaApiClient;
    private final McpConnectionManager mcpConnectionManager;
    private final List<OllamaApi.Tool> ollamaTools;
    private final List<OllamaApi.Message> conversationHistory = new ArrayList<>();

    // Callbacks for GUI updates
    private Consumer<Message> onMessageReceived;
    private Consumer<String> onThinking;
    private Runnable onThinkingFinished;

    public GuiChatController(String ollamaModelName, 
                           OllamaApiClient ollamaApiClient, 
                           McpConnectionManager mcpConnectionManager,
                           String systemPrompt, 
                           List<OllamaApi.Tool> ollamaTools) {
        this.ollamaModelName = ollamaModelName;
        this.ollamaApiClient = ollamaApiClient;
        this.mcpConnectionManager = mcpConnectionManager;
        this.ollamaTools = ollamaTools;

        // Initialize conversation with the system prompt
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            logger.debug("Initializing with System Prompt:\n{}", systemPrompt);
            this.conversationHistory.add(new OllamaApi.Message("system", systemPrompt));
        }
    }

    public void setOnMessageReceived(Consumer<Message> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    public void setOnThinking(Consumer<String> onThinking) {
        this.onThinking = onThinking;
    }

    public void setOnThinkingFinished(Runnable onThinkingFinished) {
        this.onThinkingFinished = onThinkingFinished;
    }

    /**
     * Processes a user message asynchronously and returns the AI response.
     */
    public CompletableFuture<Void> processUserMessage(String userInput) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Add user message to history
                conversationHistory.add(new OllamaApi.Message("user", userInput));

                // Process the conversation turn, including potential tool calls
                processConversationTurn();
            } catch (Exception e) {
                logger.error("Error processing user message", e);
                Platform.runLater(() -> {
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(new Message("Error: " + e.getMessage(), false, LocalDateTime.now()));
                    }
                });
            }
        });
    }

    /**
     * Handles a single turn of the conversation, which may involve multiple calls to the LLM
     * if tool usage is required.
     */
    private void processConversationTurn() {
        boolean requiresFollowUp;
        do {
            requiresFollowUp = false;

            // Show thinking indicator
            Platform.runLater(() -> {
                if (onThinking != null) {
                    onThinking.accept("Thinking...");
                }
            });

            // 1. Call the LLM with the current conversation history
            OllamaApi.ChatResponse chatResponse = callOllama();
            
            // Hide thinking indicator
            Platform.runLater(() -> {
                if (onThinkingFinished != null) {
                    onThinkingFinished.run();
                }
            });
            
            if (chatResponse == null || chatResponse.message() == null) {
                Platform.runLater(() -> {
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(new Message("No response received due to an API error", false, LocalDateTime.now()));
                    }
                });
                break; // Exit the loop on API error
            }

            OllamaApi.Message assistantMessage = chatResponse.message();
            conversationHistory.add(assistantMessage); // Add assistant's response to history

            // 2. Display the assistant's text content (excluding thinking)
            String displayContent = extractDisplayContent(assistantMessage);
            if (!displayContent.isEmpty()) {
                Platform.runLater(() -> {
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(new Message(displayContent, false, LocalDateTime.now()));
                    }
                });
            }

            // 3. If the assistant requested tool calls, execute them
            if (assistantMessage.tool_calls() != null && !assistantMessage.tool_calls().isEmpty()) {
                executeToolCalls(assistantMessage.tool_calls());
                requiresFollowUp = true; // A tool was called, so we need to send the result back to the LLM
            }

        } while (requiresFollowUp);
    }

    private OllamaApi.ChatResponse callOllama() {
        OllamaApi.ChatRequest chatRequest = new OllamaApi.ChatRequest(
                ollamaModelName,
                new ArrayList<>(conversationHistory), // Send a copy
                false,
                ollamaTools.isEmpty() ? null : ollamaTools
        );

        try {
            return ollamaApiClient.chat(chatRequest);
        } catch (Exception e) {
            logger.error("Error communicating with Ollama API: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractDisplayContent(OllamaApi.Message assistantMessage) {
        String assistantContent = assistantMessage.content() != null ? assistantMessage.content() : "";
        
        // Remove <think> tags for display
        Matcher assistantThinkMatcher = THINK_TAG_PATTERN.matcher(assistantContent);
        return assistantThinkMatcher.replaceAll("").trim();
    }

    private void executeToolCalls(List<OllamaApi.ToolCall> toolCalls) {
        for (OllamaApi.ToolCall toolCall : toolCalls) {
            String toolName = toolCall.function().name();
            Map<String, Object> toolArgs = toolCall.function().arguments();

            logger.info("LLM -> Tool Call: {} | Args: {}", toolName, toolArgs);

            if (toolArgs == null) {
                logger.error("Tool call for '{}' received null arguments.", toolName);
                addToolResultToHistory("Error: Tool " + toolName + " called with no arguments.");
                continue;
            }

            // Show tool execution indicator
            Platform.runLater(() -> {
                if (onThinking != null) {
                    onThinking.accept("Executing tool " + toolName + "...");
                }
            });

            McpSchema.CallToolResult mcpToolResult;
            try {
                mcpToolResult = mcpConnectionManager.callTool(toolName, toolArgs);
            } catch (Exception e) {
                logger.error("Error executing MCP tool '{}': {}", toolName, e.getMessage(), e);
                mcpToolResult = new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error during tool execution: " + e.getMessage())), true);
            } finally {
                Platform.runLater(() -> {
                    if (onThinkingFinished != null) {
                        onThinkingFinished.run();
                    }
                });
            }

            String toolResultString = formatToolResult(toolName, mcpToolResult);
            logger.info("Tool -> Result: {}", toolResultString);
            addToolResultToHistory(toolResultString);
        }
    }

    private String formatToolResult(String toolName, McpSchema.CallToolResult result) {
        // Join the text content from the result.
        String content = result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));

        if (content.isEmpty() && !result.content().isEmpty()) {
            // Fallback for non-text content, just use toString()
            content = result.content().get(0).toString();
        } else if (content.isEmpty()) {
            content = "Tool " + toolName + " executed with no output.";
        }

        if (result.isError() != null && result.isError()) {
            return "Error from tool " + toolName + ": " + content;
        }
        return content;
    }

    private void addToolResultToHistory(String toolResultString) {
        // The role for tool results is 'tool'
        conversationHistory.add(new OllamaApi.Message("tool", toolResultString));
    }

    /**
     * Clear the conversation history (but keep the system prompt).
     */
    public void clearHistory() {
        conversationHistory.clear();
        // Re-add system prompt if it existed
        if (!conversationHistory.isEmpty() && "system".equals(conversationHistory.get(0).role())) {
            // Keep the system prompt
            OllamaApi.Message systemPrompt = conversationHistory.get(0);
            conversationHistory.clear();
            conversationHistory.add(systemPrompt);
        }
    }
}
