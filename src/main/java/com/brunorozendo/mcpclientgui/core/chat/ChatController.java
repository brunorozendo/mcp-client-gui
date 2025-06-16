package com.brunorozendo.mcpclientgui.core.chat;

import com.brunorozendo.mcpclientgui.control.McpConnectionManager;
import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.brunorozendo.mcpclientgui.service.OllamaApiClient;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages chat conversations between the user and AI assistant.
 * Coordinates message processing, tool execution, and status updates.
 */
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    // Configuration
    private static final int MAX_CONVERSATION_TURNS = 10;
    private static final String STATUS_THINKING = "Thinking...";
    private static final String STATUS_EXECUTING_TOOL = "Executing tool: %s...";
    private static final String ERROR_NO_RESPONSE = "No response received from the AI model. Please try again.";
    private static final String ERROR_PROCESSING = "An error occurred while processing your message. Please try again.";
    
    // Core components
    private final String modelName;
    private final OllamaApiClient ollamaClient;
    private final ConversationHistory conversationHistory;
    private final ToolExecutor toolExecutor;
    private final MessageProcessor messageProcessor;
    private final List<OllamaApi.Tool> availableTools;
    
    // Status management
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private Consumer<String> statusCallback;
    private Runnable processingFinishedCallback;
    
    /**
     * Builder for creating ChatController instances.
     */
    public static class Builder {
        private String modelName;
        private OllamaApiClient ollamaClient;
        private McpConnectionManager mcpManager;
        private String systemPrompt;
        private List<OllamaApi.Tool> tools;
        private Consumer<Message> messageCallback;
        private Consumer<String> statusCallback;
        private Runnable processingFinishedCallback;
        
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }
        
        public Builder ollamaClient(OllamaApiClient client) {
            this.ollamaClient = client;
            return this;
        }
        
        public Builder mcpManager(McpConnectionManager manager) {
            this.mcpManager = manager;
            return this;
        }
        
        public Builder systemPrompt(String prompt) {
            this.systemPrompt = prompt;
            return this;
        }
        
        public Builder tools(List<OllamaApi.Tool> tools) {
            this.tools = tools;
            return this;
        }
        
        public Builder onMessageReceived(Consumer<Message> callback) {
            this.messageCallback = callback;
            return this;
        }
        
        public Builder onStatusUpdate(Consumer<String> callback) {
            this.statusCallback = callback;
            return this;
        }
        
        public Builder onProcessingFinished(Runnable callback) {
            this.processingFinishedCallback = callback;
            return this;
        }
        
        public ChatController build() {
            Objects.requireNonNull(modelName, "Model name is required");
            Objects.requireNonNull(ollamaClient, "Ollama client is required");
            Objects.requireNonNull(mcpManager, "MCP manager is required");
            
            return new ChatController(this);
        }
    }
    
    /**
     * Creates a new ChatController using the builder pattern.
     * 
     * @param builder The configured builder
     */
    private ChatController(Builder builder) {
        this.modelName = builder.modelName;
        this.ollamaClient = builder.ollamaClient;
        this.conversationHistory = new ConversationHistory(builder.systemPrompt);
        this.toolExecutor = new ToolExecutor(builder.mcpManager);
        this.messageProcessor = new MessageProcessor(builder.messageCallback);
        this.availableTools = builder.tools != null ? builder.tools : List.of();
        this.statusCallback = builder.statusCallback;
        this.processingFinishedCallback = builder.processingFinishedCallback;
        
        logger.info("Created ChatController for model: {}", modelName);
    }
    
    /**
     * Processes a user message asynchronously.
     * 
     * @param userInput The user's message
     * @return A CompletableFuture that completes when processing is done
     */
    public CompletableFuture<Void> processUserMessage(String userInput) {
        Objects.requireNonNull(userInput, "User input cannot be null");
        
        if (isProcessing.get()) {
            logger.warn("Already processing a message, ignoring new request");
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            isProcessing.set(true);
            try {
                handleUserMessage(userInput);
            } catch (Exception e) {
                logger.error("Error processing user message", e);
                messageProcessor.displayError(ERROR_PROCESSING);
            } finally {
                isProcessing.set(false);
                notifyProcessingFinished();
            }
        });
    }
    
    /**
     * Handles the processing of a user message.
     * 
     * @param userInput The user's input
     */
    private void handleUserMessage(String userInput) {
        // Add user message to history
        conversationHistory.addUserMessage(userInput);
        logger.debug("Processing user message: {}", 
                   userInput.length() > 100 ? userInput.substring(0, 100) + "..." : userInput);
        
        // Process conversation turns
        processConversationTurns();
    }
    
    /**
     * Processes conversation turns, handling tool calls as needed.
     */
    private void processConversationTurns() {
        int turnCount = 0;
        boolean needsMoreProcessing;
        
        do {
            needsMoreProcessing = false;
            turnCount++;
            
            if (turnCount > MAX_CONVERSATION_TURNS) {
                logger.warn("Reached maximum conversation turns ({})", MAX_CONVERSATION_TURNS);
                messageProcessor.displaySystemMessage(
                    "The conversation has become too complex. Please try rephrasing your request.");
                break;
            }
            
            // Update status
            updateStatus(STATUS_THINKING);
            
            // Call the AI
            OllamaApi.ChatResponse response = callOllama();
            
            if (response == null || response.message() == null) {
                messageProcessor.displayError(ERROR_NO_RESPONSE);
                break;
            }
            
            // Process the response
            OllamaApi.Message assistantMessage = response.message();
            conversationHistory.addAssistantMessage(assistantMessage);
            
            // Display the message
            messageProcessor.processAssistantMessage(assistantMessage);
            
            // Check for tool calls
            if (hasToolCalls(assistantMessage)) {
                needsMoreProcessing = processToolCalls(assistantMessage.tool_calls());
            }
            
        } while (needsMoreProcessing);
    }
    
    /**
     * Makes a call to the Ollama API.
     * 
     * @return The chat response, or null if an error occurs
     */
    private OllamaApi.ChatResponse callOllama() {
        try {
            OllamaApi.ChatRequest request = new OllamaApi.ChatRequest(
                modelName,
                conversationHistory.getMessages(),
                false, // Don't stream
                availableTools.isEmpty() ? null : availableTools
            );
            
            logger.debug("Calling Ollama model '{}' with {} messages", 
                        modelName, conversationHistory.size());
            
            return ollamaClient.chat(request);
            
        } catch (IOException e) {
            logger.error("I/O error calling Ollama API", e);
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
     * Checks if a message contains tool calls.
     * 
     * @param message The message to check
     * @return true if tool calls are present
     */
    private boolean hasToolCalls(OllamaApi.Message message) {
        return message != null && 
               message.tool_calls() != null && 
               !message.tool_calls().isEmpty();
    }
    
    /**
     * Processes tool calls from the assistant.
     * 
     * @param toolCalls The tool calls to process
     * @return true if more conversation turns are needed
     */
    private boolean processToolCalls(List<OllamaApi.ToolCall> toolCalls) {
        logger.info("Processing {} tool calls", toolCalls.size());
        
        for (OllamaApi.ToolCall toolCall : toolCalls) {
            String toolName = toolCall.function() != null ? toolCall.function().name() : "unknown";
            
            // Update status
            updateStatus(String.format(STATUS_EXECUTING_TOOL, toolName));
            
            // Execute the tool
            ToolExecutor.ToolResult result = toolExecutor.execute(toolCall);
            
            // Add result to conversation
            conversationHistory.addToolResult(result.getOutput());
            
            if (!result.isSuccess()) {
                logger.error("Tool execution failed: {}", result.getOutput());
            }
        }
        
        // We need another turn to process the tool results
        return true;
    }
    
    /**
     * Updates the status through the callback.
     * 
     * @param status The status message
     */
    private void updateStatus(String status) {
        if (statusCallback != null) {
            Platform.runLater(() -> statusCallback.accept(status));
        }
    }
    
    /**
     * Notifies that processing has finished.
     */
    private void notifyProcessingFinished() {
        if (processingFinishedCallback != null) {
            Platform.runLater(processingFinishedCallback);
        }
    }
    
    /**
     * Clears the conversation history.
     */
    public void clearHistory() {
        conversationHistory.clear();
        logger.info("Cleared conversation history");
    }
    
    /**
     * Gets the current model name.
     * 
     * @return The model name
     */
    public String getModelName() {
        return modelName;
    }
    
    /**
     * Checks if the controller is currently processing a message.
     * 
     * @return true if processing is in progress
     */
    public boolean isProcessing() {
        return isProcessing.get();
    }
    
    /**
     * Gets the size of the conversation history.
     * 
     * @return The number of messages in the history
     */
    public int getHistorySize() {
        return conversationHistory.size();
    }
    
    /**
     * Updates the status callback.
     * 
     * @param callback The new status callback
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }
    
    /**
     * Updates the processing finished callback.
     * 
     * @param callback The new callback
     */
    public void setProcessingFinishedCallback(Runnable callback) {
        this.processingFinishedCallback = callback;
    }
}
