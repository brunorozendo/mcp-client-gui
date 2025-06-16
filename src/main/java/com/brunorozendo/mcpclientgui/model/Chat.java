package com.brunorozendo.mcpclientgui.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * Represents a chat conversation session.
 * 
 * A chat contains a collection of messages exchanged between the user and an AI model.
 * Each chat is associated with a specific Language Learning Model (LLM) that processes
 * the messages and generates responses.
 */
public class Chat {
    private Long id;
    private String name;
    private String llmModelName;
    private final ObservableList<Message> messages;
    
    /**
     * Creates a new chat with the specified name.
     * The LLM model will need to be set separately.
     * 
     * @param name The display name for this chat
     * @deprecated Use {@link #Chat(String, String)} to specify the model name
     */
    @Deprecated
    public Chat(String name) {
        this(name, null);
    }
    
    /**
     * Creates a new chat with the specified name and LLM model.
     * 
     * @param name The display name for this chat
     * @param llmModelName The name of the LLM model to use for this chat
     */
    public Chat(String name, String llmModelName) {
        this.name = Objects.requireNonNull(name, "Chat name cannot be null");
        this.llmModelName = llmModelName;
        this.messages = FXCollections.observableArrayList();
    }
    
    /**
     * Creates a chat with a database ID and name.
     * This constructor is typically used when loading chats from the database.
     * 
     * @param id The unique database identifier for this chat
     * @param name The display name for this chat
     * @deprecated Use {@link #Chat(Long, String, String)} to specify the model name
     */
    @Deprecated
    public Chat(Long id, String name) {
        this(id, name, null);
    }
    
    /**
     * Creates a chat with complete database information.
     * This constructor is typically used when loading chats from the database.
     * 
     * @param id The unique database identifier for this chat
     * @param name The display name for this chat
     * @param llmModelName The name of the LLM model used for this chat
     */
    public Chat(Long id, String name, String llmModelName) {
        this(name, llmModelName);
        this.id = id;
    }
    
    /**
     * Gets the unique database identifier for this chat.
     * 
     * @return The chat ID, or null if not yet persisted
     */
    public Long getId() {
        return id;
    }
    
    /**
     * Sets the unique database identifier for this chat.
     * 
     * @param id The chat ID
     */
    public void setId(Long id) {
        this.id = id;
    }
    
    /**
     * Gets the display name of this chat.
     * 
     * @return The chat name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the display name for this chat.
     * 
     * @param name The chat name (cannot be null)
     */
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Chat name cannot be null");
    }
    
    /**
     * Gets the name of the LLM model used for this chat.
     * 
     * @return The LLM model name, or null if not set
     */
    public String getLlmModelName() {
        return llmModelName;
    }
    
    /**
     * Sets the LLM model to use for this chat.
     * 
     * @param llmModelName The name of the LLM model
     */
    public void setLlmModelName(String llmModelName) {
        this.llmModelName = llmModelName;
    }
    
    /**
     * Gets the observable list of messages in this chat.
     * The list can be observed for changes, making it suitable for JavaFX bindings.
     * 
     * @return The observable list of messages
     */
    public ObservableList<Message> getMessages() {
        return messages;
    }
    
    /**
     * Checks if this chat has any messages.
     * 
     * @return True if the chat contains at least one message, false otherwise
     */
    public boolean hasMessages() {
        return !messages.isEmpty();
    }
    
    /**
     * Gets the number of messages in this chat.
     * 
     * @return The message count
     */
    public int getMessageCount() {
        return messages.size();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Chat chat = (Chat) obj;
        return Objects.equals(id, chat.id) &&
               Objects.equals(name, chat.name) &&
               Objects.equals(llmModelName, chat.llmModelName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name, llmModelName);
    }
    
    @Override
    public String toString() {
        return String.format("Chat{id=%d, name='%s', model='%s', messages=%d}",
                           id, name, llmModelName, messages.size());
    }
}
