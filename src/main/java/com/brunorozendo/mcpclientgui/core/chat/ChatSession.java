package com.brunorozendo.mcpclientgui.core.chat;

import java.util.concurrent.CompletableFuture;

/**
 * A chat with one model through one provider, as the main window drives it.
 * Implemented by {@link ChatController} (Ollama) and {@link AnthropicChatController} (Anthropic).
 */
public interface ChatSession {

    /**
     * Sends a user message to the model and handles the reply (including tool calls) asynchronously.
     * Must be called on the JavaFX thread; replies and status updates come back through the callbacks.
     *
     * @param userInput The user's message
     * @return A future that completes when the reply has been handled
     */
    CompletableFuture<Void> processUserMessage(String userInput);

    /**
     * Gets the model name this session talks to, as the provider's API expects it.
     *
     * @return The model name
     */
    String getModelName();

    /**
     * Checks whether a message is being processed.
     *
     * @return true while a message is in flight
     */
    boolean isProcessing();
}
