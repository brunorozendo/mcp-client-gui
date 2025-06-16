package com.brunorozendo.mcpclientgui.core.chat;

import com.brunorozendo.mcpclientgui.model.OllamaApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages the conversation history for a chat session.
 * Provides methods to add messages, clear history, and maintain the system prompt.
 */
public class ConversationHistory {
    
    private static final String SYSTEM_ROLE = "system";
    
    private final List<OllamaApi.Message> messages;
    private final String systemPrompt;
    
    /**
     * Creates a new conversation history with an optional system prompt.
     * 
     * @param systemPrompt The initial system prompt, or null if none
     */
    public ConversationHistory(String systemPrompt) {
        this.messages = new ArrayList<>();
        this.systemPrompt = systemPrompt;
        
        // Initialize with system prompt if provided
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new OllamaApi.Message(SYSTEM_ROLE, systemPrompt));
        }
    }
    
    /**
     * Adds a message to the conversation history.
     * 
     * @param message The message to add
     * @throws NullPointerException if message is null
     */
    public void addMessage(OllamaApi.Message message) {
        Objects.requireNonNull(message, "Message cannot be null");
        messages.add(message);
    }
    
    /**
     * Adds a user message to the conversation.
     * 
     * @param content The user's message content
     * @throws NullPointerException if content is null
     */
    public void addUserMessage(String content) {
        Objects.requireNonNull(content, "Content cannot be null");
        messages.add(new OllamaApi.Message("user", content));
    }
    
    /**
     * Adds an assistant message to the conversation.
     * 
     * @param message The assistant's message
     * @throws NullPointerException if message is null
     */
    public void addAssistantMessage(OllamaApi.Message message) {
        Objects.requireNonNull(message, "Message cannot be null");
        messages.add(message);
    }
    
    /**
     * Adds a tool result to the conversation.
     * 
     * @param toolResult The tool execution result
     * @throws NullPointerException if toolResult is null
     */
    public void addToolResult(String toolResult) {
        Objects.requireNonNull(toolResult, "Tool result cannot be null");
        messages.add(new OllamaApi.Message("tool", toolResult));
    }
    
    /**
     * Gets a copy of the current conversation history.
     * 
     * @return A new list containing all messages
     */
    public List<OllamaApi.Message> getMessages() {
        return new ArrayList<>(messages);
    }
    
    /**
     * Gets the number of messages in the history.
     * 
     * @return The message count
     */
    public int size() {
        return messages.size();
    }
    
    /**
     * Checks if the history is empty.
     * 
     * @return true if no messages exist
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    /**
     * Clears the conversation history.
     * If a system prompt exists, it is preserved.
     */
    public void clear() {
        messages.clear();
        
        // Re-add system prompt if it exists
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new OllamaApi.Message(SYSTEM_ROLE, systemPrompt));
        }
    }
    
    /**
     * Gets the system prompt if one exists.
     * 
     * @return The system prompt, or null if none
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    /**
     * Checks if this conversation has a system prompt.
     * 
     * @return true if a system prompt exists
     */
    public boolean hasSystemPrompt() {
        return systemPrompt != null && !systemPrompt.isBlank();
    }
}
