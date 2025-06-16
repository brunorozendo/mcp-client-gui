package com.brunorozendo.mcpclientgui.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a single message in a chat conversation.
 * 
 * Messages can be from either the user or the AI assistant, and are
 * associated with a specific chat session. Each message has a timestamp
 * indicating when it was created.
 */
public class Message {
    private Long id;
    private Long chatId;
    private String content;
    private boolean fromUser;
    private LocalDateTime timestamp;
    
    /**
     * Creates a new message without database identifiers.
     * This constructor is typically used for creating new messages before they are saved.
     * 
     * @param content The text content of the message
     * @param fromUser True if the message is from the user, false if from the AI
     * @param timestamp The time when the message was created
     */
    public Message(String content, boolean fromUser, LocalDateTime timestamp) {
        this.content = Objects.requireNonNull(content, "Message content cannot be null");
        this.fromUser = fromUser;
        this.timestamp = Objects.requireNonNull(timestamp, "Message timestamp cannot be null");
    }
    
    /**
     * Creates a message with complete database information.
     * This constructor is typically used when loading messages from the database.
     * 
     * @param id The unique database identifier for this message
     * @param chatId The ID of the chat this message belongs to
     * @param content The text content of the message
     * @param fromUser True if the message is from the user, false if from the AI
     * @param timestamp The time when the message was created
     */
    public Message(Long id, Long chatId, String content, boolean fromUser, LocalDateTime timestamp) {
        this(content, fromUser, timestamp);
        this.id = id;
        this.chatId = chatId;
    }
    
    /**
     * Gets the unique database identifier for this message.
     * 
     * @return The message ID, or null if not yet persisted
     */
    public Long getId() {
        return id;
    }
    
    /**
     * Sets the unique database identifier for this message.
     * 
     * @param id The message ID
     */
    public void setId(Long id) {
        this.id = id;
    }
    
    /**
     * Gets the ID of the chat this message belongs to.
     * 
     * @return The chat ID, or null if not yet associated with a chat
     */
    public Long getChatId() {
        return chatId;
    }
    
    /**
     * Associates this message with a specific chat.
     * 
     * @param chatId The ID of the chat this message belongs to
     */
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    /**
     * Gets the text content of the message.
     * 
     * @return The message content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Sets the text content of the message.
     * 
     * @param content The message content (cannot be null)
     */
    public void setContent(String content) {
        this.content = Objects.requireNonNull(content, "Message content cannot be null");
    }
    
    /**
     * Checks if this message was sent by the user.
     * 
     * @return True if the message is from the user, false if from the AI assistant
     */
    public boolean isFromUser() {
        return fromUser;
    }
    
    /**
     * Sets whether this message is from the user or the AI.
     * 
     * @param fromUser True if the message is from the user, false if from the AI
     */
    public void setFromUser(boolean fromUser) {
        this.fromUser = fromUser;
    }
    
    /**
     * Gets the timestamp when this message was created.
     * 
     * @return The message timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Sets the timestamp for this message.
     * 
     * @param timestamp The time when the message was created (cannot be null)
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = Objects.requireNonNull(timestamp, "Message timestamp cannot be null");
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Message message = (Message) obj;
        return fromUser == message.fromUser &&
               Objects.equals(id, message.id) &&
               Objects.equals(chatId, message.chatId) &&
               Objects.equals(content, message.content) &&
               Objects.equals(timestamp, message.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, content, fromUser, timestamp);
    }
    
    @Override
    public String toString() {
        return "Message{" +
               "id=" + id +
               ", chatId=" + chatId +
               ", fromUser=" + fromUser +
               ", timestamp=" + timestamp +
               ", content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'" +
               '}';
    }
}
