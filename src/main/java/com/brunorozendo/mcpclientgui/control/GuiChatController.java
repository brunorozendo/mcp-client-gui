package com.brunorozendo.mcpclientgui.control;

import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.brunorozendo.mcpclientgui.service.OllamaApiClient;
import io.modelcontextprotocol.spec.McpSchema;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controller for managing chat sessions in the GUI application.
 * 
 * This controller orchestrates the interaction between:
 * - The user (through the GUI)
 * - The Language Learning Model (through Ollama API)
 * - MCP servers (through the connection manager)
 * 
 * It maintains conversation history, processes tool calls, and manages
 * callbacks for updating the GUI with new messages and status updates.
 */
public class GuiChatController {

    private static final Logger logger = LoggerFactory.getLogger(GuiChatController.class);
    
    // Constants
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
        "<think>(.*?)</think>", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String TOOL_ROLE = "tool";
    private static final String STATUS_THINKING = "Thinking...";
    private static final String STATUS_EXECUTING_TOOL = "Executing tool %s...";
    
    // Core components
    private final String ollamaModelName;
    private final OllamaApiClient ollamaApiClient;
    private final McpConnectionManager mcpConnectionManager;
    private final List<OllamaApi.Tool> ollamaTools;
    private final List<OllamaApi.Message> conversationHistory;

    // GUI callbacks
    private Consumer<Message> onMessageReceived;
    private Consumer<String> onThinking;
    private Runnable onThinkingFinished;

    /**
     * Creates a new chat controller for managing AI conversations.
     * 
     * @param ollamaModelName The name of the Ollama model to use
     * @param ollamaApiClient The client for communicating with Ollama
     * @param mcpConnectionManager The manager for MCP server connections
     * @param systemPrompt The initial system prompt for the conversation
     * @param ollamaTools The list of available tools for the model
     * @throws NullPointerException if any required parameter is null
     */
    public GuiChatController(String ollamaModelName, 
                           OllamaApiClient ollamaApiClient, 
                           McpConnectionManager mcpConnectionManager,
                           String systemPrompt, 
                           List<OllamaApi.Tool> ollamaTools) {
        this.ollamaModelName = Objects.requireNonNull(ollamaModelName, "Model name cannot be null");
        this.ollamaApiClient = Objects.requireNonNull(ollamaApiClient, "Ollama client cannot be null");
        this.mcpConnectionManager = Objects.requireNonNull(mcpConnectionManager, "MCP manager cannot be null");
        this.ollamaTools = ollamaTools != null ? ollamaTools : new ArrayList<>();
        this.conversationHistory = new ArrayList<>();

        // Initialize with system prompt if provided
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            logger.debug("Initializing chat with system prompt ({} chars)", systemPrompt.length());
            this.conversationHistory.add(new OllamaApi.Message(SYSTEM_ROLE, systemPrompt));
        }
    }

    // ===== Callback Configuration =====
    
    /**
     * Sets the callback for when a message is received from the AI.
     * 
     * @param onMessageReceived The callback to invoke with the AI's message
     */
    public void setOnMessageReceived(Consumer<Message> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    /**
     * Sets the callback for when the AI is thinking or processing.
     * 
     * @param onThinking The callback to invoke with status text
     */
    public void setOnThinking(Consumer<String> onThinking) {
        this.onThinking = onThinking;
    }

    /**
     * Sets the callback for when the AI finishes thinking.
     * 
     * @param onThinkingFinished The callback to invoke when processing completes
     */
    public void setOnThinkingFinished(Runnable onThinkingFinished) {
        this.onThinkingFinished = onThinkingFinished;
    }

    // ===== Message Processing =====
    
    /**
     * Processes a user message asynchronously.
     * 
     * This method:
     * 1. Adds the user message to the conversation history
     * 2. Sends the conversation to the AI model
     * 3. Processes the AI's response (including any tool calls)
     * 4. Updates the GUI through callbacks
     * 
     * @param userInput The user's message text
     * @return A CompletableFuture that completes when processing is done
     * @throws NullPointerException if userInput is null
     */
    public CompletableFuture<Void> processUserMessage(String userInput) {
        Objects.requireNonNull(userInput, "User input cannot be null");
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Add user message to history
                conversationHistory.add(new OllamaApi.Message(USER_ROLE, userInput));
                logger.debug("Processing user message: {}", 
                           userInput.length() > 100 ? userInput.substring(0, 100) + "..." : userInput);

                // Process the conversation turn
                processConversationTurn();
            } catch (Exception e) {
                logger.error("Error processing user message", e);
                handleProcessingError(e);
            }
        });
    }

    /**
     * Handles a single turn of the conversation.
     * 
     * This may involve multiple calls to the LLM if tool usage is required.
     * The method continues processing until no more tool calls are needed.
     */
    private void processConversationTurn() {
        boolean requiresFollowUp;
        int iterationCount = 0;
        final int MAX_ITERATIONS = 10; // Prevent infinite loops
        
        do {
            requiresFollowUp = false;
            iterationCount++;
            
            if (iterationCount > MAX_ITERATIONS) {
                logger.warn("Reached maximum conversation iterations ({}), stopping", MAX_ITERATIONS);
                break;
            }

            // Show thinking status
            updateThinkingStatus(STATUS_THINKING);

            // Call the LLM
            OllamaApi.ChatResponse chatResponse = callOllama();
            
            // Hide thinking status
            notifyThinkingFinished();
            
            if (chatResponse == null || chatResponse.message() == null) {
                handleEmptyResponse();
                break;
            }

            // Process the response
            OllamaApi.Message assistantMessage = chatResponse.message();
            conversationHistory.add(assistantMessage);

            // Display the assistant's message (excluding thinking tags)
            displayAssistantMessage(assistantMessage);

            // Check if tool calls are needed
            if (hasToolCalls(assistantMessage)) {
                executeToolCalls(assistantMessage.tool_calls());
                requiresFollowUp = true; // Need to send tool results back to LLM
            }

        } while (requiresFollowUp);
    }

    /**
     * Makes a call to the Ollama API with the current conversation.
     * 
     * @return The chat response, or null if an error occurs
     */
    private OllamaApi.ChatResponse callOllama() {
        OllamaApi.ChatRequest chatRequest = new OllamaApi.ChatRequest(
                ollamaModelName,
                new ArrayList<>(conversationHistory), // Send a copy
                false, // Don't stream
                ollamaTools.isEmpty() ? null : ollamaTools
        );

        try {
            logger.debug("Calling Ollama model '{}' with {} messages", 
                        ollamaModelName, conversationHistory.size());
            return ollamaApiClient.chat(chatRequest);
        } catch (IOException e) {
            logger.error("I/O error communicating with Ollama API", e);
            return null;
        } catch (InterruptedException e) {
            logger.error("Interrupted while calling Ollama API", e);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error calling Ollama API", e);
            return null;
        }
    }

    /**
     * Extracts displayable content from an assistant message.
     * Removes thinking tags that shouldn't be shown to the user.
     * 
     * @param assistantMessage The message from the assistant
     * @return The cleaned content for display
     */
    private String extractDisplayContent(OllamaApi.Message assistantMessage) {
        String content = assistantMessage.content() != null ? assistantMessage.content() : "";
        
        // Remove <think> tags
        Matcher matcher = THINK_TAG_PATTERN.matcher(content);
        String cleanContent = matcher.replaceAll("").trim();
        
        if (matcher.find()) {
            logger.debug("Removed thinking content from assistant message");
        }
        
        return cleanContent;
    }

    /**
     * Displays an assistant message through the GUI callback.
     * 
     * @param assistantMessage The message to display
     */
    private void displayAssistantMessage(OllamaApi.Message assistantMessage) {
        String displayContent = extractDisplayContent(assistantMessage);
        
        if (!displayContent.isEmpty()) {
            Message guiMessage = new Message(displayContent, false, LocalDateTime.now());
            
            Platform.runLater(() -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(guiMessage);
                }
            });
            
            logger.debug("Displayed assistant message ({} chars)", displayContent.length());
        }
    }

    /**
     * Checks if an assistant message contains tool calls.
     * 
     * @param message The message to check
     * @return True if tool calls are present
     */
    private boolean hasToolCalls(OllamaApi.Message message) {
        return message.tool_calls() != null && !message.tool_calls().isEmpty();
    }

    /**
     * Executes all tool calls requested by the assistant.
     * 
     * @param toolCalls The list of tool calls to execute
     */
    private void executeToolCalls(List<OllamaApi.ToolCall> toolCalls) {
        logger.info("Executing {} tool calls", toolCalls.size());
        
        for (OllamaApi.ToolCall toolCall : toolCalls) {
            executeToolCall(toolCall);
        }
    }

    /**
     * Executes a single tool call.
     * 
     * @param toolCall The tool call to execute
     */
    private void executeToolCall(OllamaApi.ToolCall toolCall) {
        String toolName = toolCall.function().name();
        Map<String, Object> toolArgs = toolCall.function().arguments();

        logger.info("Executing tool: {} with args: {}", toolName, toolArgs);

        if (toolArgs == null) {
            logger.error("Tool '{}' called with null arguments", toolName);
            addToolResultToHistory(createErrorResult(toolName, "No arguments provided"));
            return;
        }

        // Update status
        updateThinkingStatus(String.format(STATUS_EXECUTING_TOOL, toolName));

        try {
            // Execute the tool
            McpSchema.CallToolResult result = mcpConnectionManager.callTool(toolName, toolArgs);
            
            // Format and add result to history
            String formattedResult = formatToolResult(toolName, result);
            addToolResultToHistory(formattedResult);
            
            logger.info("Tool '{}' executed successfully", toolName);
        } catch (Exception e) {
            logger.error("Error executing tool '{}'", toolName, e);
            addToolResultToHistory(createErrorResult(toolName, e.getMessage()));
        } finally {
            notifyThinkingFinished();
        }
    }

    /**
     * Formats a tool result for inclusion in the conversation.
     * 
     * @param toolName The name of the tool
     * @param result The result from the tool execution
     * @return Formatted result string
     */
    private String formatToolResult(String toolName, McpSchema.CallToolResult result) {
        // Extract text content from the result
        String content = result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));

        if (content.isEmpty() && !result.content().isEmpty()) {
            // Fallback for non-text content
            content = result.content().get(0).toString();
        } else if (content.isEmpty()) {
            content = String.format("Tool '%s' executed successfully with no output.", toolName);
        }

        // Add error prefix if this was an error
        if (Boolean.TRUE.equals(result.isError())) {
            return String.format("Error from tool '%s': %s", toolName, content);
        }
        
        return content;
    }

    /**
     * Creates an error result message for a tool.
     * 
     * @param toolName The tool that failed
     * @param errorMessage The error message
     * @return Formatted error result
     */
    private String createErrorResult(String toolName, String errorMessage) {
        return String.format("Error executing tool '%s': %s", toolName, errorMessage);
    }

    /**
     * Adds a tool result to the conversation history.
     * 
     * @param toolResultString The formatted tool result
     */
    private void addToolResultToHistory(String toolResultString) {
        conversationHistory.add(new OllamaApi.Message(TOOL_ROLE, toolResultString));
        logger.debug("Added tool result to history ({} chars)", toolResultString.length());
    }

    // ===== Status Updates =====
    
    /**
     * Updates the thinking status in the GUI.
     * 
     * @param status The status message to display
     */
    private void updateThinkingStatus(String status) {
        Platform.runLater(() -> {
            if (onThinking != null) {
                onThinking.accept(status);
            }
        });
    }

    /**
     * Notifies that thinking/processing has finished.
     */
    private void notifyThinkingFinished() {
        Platform.runLater(() -> {
            if (onThinkingFinished != null) {
                onThinkingFinished.run();
            }
        });
    }

    // ===== Error Handling =====
    
    /**
     * Handles errors that occur during message processing.
     * 
     * @param error The error that occurred
     */
    private void handleProcessingError(Exception error) {
        String errorMessage = "Error: " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
        Message errorGuiMessage = new Message(errorMessage, false, LocalDateTime.now());
        
        Platform.runLater(() -> {
            if (onMessageReceived != null) {
                onMessageReceived.accept(errorGuiMessage);
            }
        });
    }

    /**
     * Handles the case where the LLM returns an empty response.
     */
    private void handleEmptyResponse() {
        String errorMessage = "No response received from the AI model. Please try again.";
        Message errorGuiMessage = new Message(errorMessage, false, LocalDateTime.now());
        
        Platform.runLater(() -> {
            if (onMessageReceived != null) {
                onMessageReceived.accept(errorGuiMessage);
            }
        });
    }

    // ===== History Management =====
    
    /**
     * Clears the conversation history.
     * 
     * If a system prompt exists, it is preserved.
     */
    public void clearHistory() {
        logger.info("Clearing conversation history");
        
        // Check if we have a system prompt to preserve
        OllamaApi.Message systemPrompt = null;
        if (!conversationHistory.isEmpty() && SYSTEM_ROLE.equals(conversationHistory.get(0).role())) {
            systemPrompt = conversationHistory.get(0);
        }
        
        // Clear history
        conversationHistory.clear();
        
        // Re-add system prompt if it existed
        if (systemPrompt != null) {
            conversationHistory.add(systemPrompt);
            logger.debug("Preserved system prompt in cleared history");
        }
    }

    /**
     * Gets the current conversation history size.
     * 
     * @return The number of messages in the history
     */
    public int getHistorySize() {
        return conversationHistory.size();
    }

    /**
     * Gets the model name being used.
     * 
     * @return The Ollama model name
     */
    public String getModelName() {
        return ollamaModelName;
    }
}
