package com.brunorozendo.mcpclientgui.core.chat;

import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes and formats messages for display in the chat interface.
 * Handles extraction of displayable content and removal of internal tags.
 */
public class MessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);
    
    // Pattern for matching thinking tags that should be hidden from users
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
        "<think>(.*?)</think>", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private final Consumer<Message> messageCallback;
    
    /**
     * Creates a new message processor.
     * 
     * @param messageCallback The callback to invoke when a message should be displayed
     */
    public MessageProcessor(Consumer<Message> messageCallback) {
        this.messageCallback = messageCallback;
    }
    
    /**
     * Processes an assistant message for display.
     * Removes internal thinking tags and creates a GUI message.
     * 
     * @param assistantMessage The message from the assistant
     */
    public void processAssistantMessage(OllamaApi.Message assistantMessage) {
        if (assistantMessage == null) {
            logger.warn("Received null assistant message");
            return;
        }
        
        String displayContent = extractDisplayableContent(assistantMessage);
        
        if (!displayContent.isEmpty()) {
            displayMessage(displayContent, false);
            logger.debug("Processed assistant message ({} chars)", displayContent.length());
        } else {
            logger.debug("Assistant message had no displayable content");
        }
    }
    
    /**
     * Processes a user message for display.
     * 
     * @param userMessage The user's message content
     */
    public void processUserMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            logger.warn("Received empty user message");
            return;
        }
        
        displayMessage(userMessage, true);
        logger.debug("Processed user message ({} chars)", userMessage.length());
    }
    
    /**
     * Displays an error message in the chat.
     * 
     * @param errorMessage The error message to display
     */
    public void displayError(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            errorMessage = "An unknown error occurred";
        }
        
        String formattedError = "Error: " + errorMessage;
        displayMessage(formattedError, false);
        logger.error("Displaying error message: {}", errorMessage);
    }
    
    /**
     * Displays a system message in the chat.
     * 
     * @param systemMessage The system message to display
     */
    public void displaySystemMessage(String systemMessage) {
        if (systemMessage == null || systemMessage.isEmpty()) {
            return;
        }
        
        displayMessage(systemMessage, false);
        logger.info("Displaying system message: {}", systemMessage);
    }
    
    /**
     * Extracts displayable content from an assistant message.
     * Removes thinking tags and other internal markers.
     * 
     * @param message The assistant message
     * @return The cleaned content for display
     */
    private String extractDisplayableContent(OllamaApi.Message message) {
        String content = message.content();
        if (content == null) {
            return "";
        }
        
        // Remove thinking tags
        String cleanedContent = removeThinkingTags(content);
        
        // Trim whitespace
        cleanedContent = cleanedContent.trim();
        
        // Additional cleaning can be added here
        
        return cleanedContent;
    }
    
    /**
     * Removes thinking tags from content.
     * 
     * @param content The content to clean
     * @return The content without thinking tags
     */
    private String removeThinkingTags(String content) {
        Matcher matcher = THINK_TAG_PATTERN.matcher(content);
        
        if (matcher.find()) {
            logger.debug("Removing thinking tags from content");
            return matcher.replaceAll("").trim();
        }
        
        return content;
    }
    
    /**
     * Creates and displays a message through the callback.
     * 
     * @param content The message content
     * @param isFromUser Whether this is a user message
     */
    private void displayMessage(String content, boolean isFromUser) {
        Message message = new Message(content, isFromUser, LocalDateTime.now());
        
        // Use Platform.runLater to ensure GUI updates happen on JavaFX thread
        Platform.runLater(() -> {
            if (messageCallback != null) {
                messageCallback.accept(message);
            } else {
                logger.warn("No message callback set - message not displayed");
            }
        });
    }
    
    /**
     * Checks if thinking tags are present in content.
     * 
     * @param content The content to check
     * @return true if thinking tags are found
     */
    public boolean hasThinkingTags(String content) {
        if (content == null) {
            return false;
        }
        return THINK_TAG_PATTERN.matcher(content).find();
    }
    
    /**
     * Extracts thinking content from a message.
     * 
     * @param content The content to extract from
     * @return The thinking content, or empty string if none found
     */
    public String extractThinkingContent(String content) {
        if (content == null) {
            return "";
        }
        
        Matcher matcher = THINK_TAG_PATTERN.matcher(content);
        StringBuilder thinkingContent = new StringBuilder();
        
        while (matcher.find()) {
            if (thinkingContent.length() > 0) {
                thinkingContent.append("\n");
            }
            thinkingContent.append(matcher.group(1).trim());
        }
        
        return thinkingContent.toString();
    }
}
