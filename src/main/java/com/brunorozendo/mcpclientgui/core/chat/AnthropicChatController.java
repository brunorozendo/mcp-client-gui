package com.brunorozendo.mcpclientgui.core.chat;

import com.anthropic.models.beta.messages.BetaMessageParam;
import com.brunorozendo.mcpclientgui.model.Message;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Connects an {@link AnthropicConversation} to the chat window: runs each turn off the JavaFX thread
 * and sends text, status updates and errors back through the same callbacks as {@link ChatController}.
 */
public class AnthropicChatController implements ChatSession {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicChatController.class);

    private static final String ERROR_PROCESSING = "An error occurred while processing your message. Please try again.";

    private final AnthropicConversation conversation;
    private final Supplier<List<Message>> storedMessages;
    private final MessageProcessor messageProcessor;
    private final Consumer<String> statusCallback;
    private final Runnable processingFinishedCallback;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    /**
     * Creates a controller.
     *
     * @param conversation The conversation runner for the chat's model
     * @param storedMessages Returns the chat's stored messages; called on the JavaFX thread
     * @param messageCallback Receives messages to display (on the JavaFX thread)
     * @param statusCallback Receives status updates (may be null)
     * @param processingFinishedCallback Called when a turn is done (may be null)
     */
    public AnthropicChatController(AnthropicConversation conversation,
                                   Supplier<List<Message>> storedMessages,
                                   Consumer<Message> messageCallback,
                                   Consumer<String> statusCallback,
                                   Runnable processingFinishedCallback) {
        this.conversation = Objects.requireNonNull(conversation, "Conversation is required");
        this.storedMessages = Objects.requireNonNull(storedMessages, "Stored messages supplier is required");
        this.messageProcessor = new MessageProcessor(messageCallback);
        this.statusCallback = statusCallback;
        this.processingFinishedCallback = processingFinishedCallback;
        logger.info("Created AnthropicChatController for model: {}", conversation.getModelId());
    }

    @Override
    public CompletableFuture<Void> processUserMessage(String userInput) {
        Objects.requireNonNull(userInput, "User input cannot be null");

        if (isProcessing.get()) {
            logger.warn("Already processing a message, ignoring new request");
            return CompletableFuture.completedFuture(null);
        }

        // Snapshot the stored chat on the calling (JavaFX) thread. The window stores the new user
        // message before calling here, so leave it out: it is added as the final user turn.
        List<Message> snapshot = new ArrayList<>(storedMessages.get());
        if (!snapshot.isEmpty()) {
            Message last = snapshot.get(snapshot.size() - 1);
            if (last.isFromUser() && userInput.equals(last.getContent())) {
                snapshot.remove(snapshot.size() - 1);
            }
        }
        List<BetaMessageParam> history = AnthropicConversation.historyFromStoredMessages(snapshot, userInput);

        return CompletableFuture.runAsync(() -> {
            isProcessing.set(true);
            try {
                conversation.run(history, new AnthropicConversation.Listener() {
                    @Override
                    public void onAssistantText(String text) {
                        messageProcessor.displayAssistantText(text);
                    }

                    @Override
                    public void onStatus(String status) {
                        if (statusCallback != null) {
                            Platform.runLater(() -> statusCallback.accept(status));
                        }
                    }

                    @Override
                    public void onNotice(String message) {
                        messageProcessor.displaySystemMessage(message);
                    }

                    @Override
                    public void onError(String message) {
                        messageProcessor.displayError(message);
                    }
                });
            } catch (Exception e) {
                logger.error("Error processing user message", e);
                messageProcessor.displayError(ERROR_PROCESSING);
            } finally {
                isProcessing.set(false);
                if (processingFinishedCallback != null) {
                    Platform.runLater(processingFinishedCallback);
                }
            }
        });
    }

    @Override
    public String getModelName() {
        return conversation.getModelId();
    }

    @Override
    public boolean isProcessing() {
        return isProcessing.get();
    }
}
