package com.brunorozendo.mcpclientgui.core;

import com.brunorozendo.mcpclientgui.model.Chat;
import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.brunorozendo.mcpclientgui.service.DatabaseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages all chat-related operations including creation, deletion, and persistence.
 * This class serves as a centralized manager for chat functionality, separating
 * business logic from UI concerns.
 */
public class ChatManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatManager.class);
    
    private static final String DEFAULT_CHAT_NAME_PREFIX = "Chat ";
    private static final String WELCOME_CHAT_NAME = "Welcome Chat";
    private static final String WELCOME_MESSAGE_CONTENT = 
        "Welcome to MCP Client GUI! Configure your settings to get started.";
    private static final String DEFAULT_FALLBACK_MODEL = "qwen3:8b";
    
    private final DatabaseService databaseService;
    private final ObservableList<Chat> chats;
    private Chat currentChat;
    
    /**
     * Creates a new ChatManager instance.
     */
    public ChatManager() {
        this.databaseService = DatabaseService.getInstance();
        this.chats = FXCollections.observableArrayList();
    }
    
    /**
     * Loads all chats from the database and performs necessary migrations.
     * 
     * @param defaultModelName The default model name to use for migration
     * @return The observable list of loaded chats
     */
    public ObservableList<Chat> loadAllChats(String defaultModelName) {
        logger.info("Loading all chats from database");
        
        List<Chat> savedChats = databaseService.getAllChats();
        migrateChatsWithoutModels(savedChats, defaultModelName);
        
        chats.clear();
        chats.addAll(savedChats);
        
        if (chats.isEmpty()) {
            createWelcomeChat(defaultModelName);
        }
        
        logger.info("Loaded {} chats", chats.size());
        return chats;
    }
    
    /**
     * Creates a new chat with an auto-generated name.
     * 
     * @param modelName The LLM model to use for the chat
     * @return The newly created chat
     */
    public Chat createNewChat(String modelName) {
        String chatName = generateUniqueChatName();
        return createChat(chatName, modelName);
    }
    
    /**
     * Creates a new chat with the specified name and model.
     * 
     * @param chatName The name for the new chat
     * @param modelName The LLM model to use for the chat
     * @return The newly created and persisted chat
     */
    public Chat createChat(String chatName, String modelName) {
        logger.info("Creating new chat: {} with model: {}", chatName, modelName);
        
        Chat newChat = new Chat(chatName, modelName);
        newChat = databaseService.saveChat(newChat);
        chats.add(newChat);
        
        return newChat;
    }
    
    /**
     * Renames an existing chat.
     * 
     * @param chat The chat to rename
     * @param newName The new name for the chat
     */
    public void renameChat(Chat chat, String newName) {
        if (chat == null || newName == null || newName.trim().isEmpty()) {
            logger.warn("Cannot rename chat with invalid parameters");
            return;
        }
        
        logger.info("Renaming chat {} to {}", chat.getName(), newName);
        chat.setName(newName.trim());
        databaseService.saveChat(chat);
    }
    
    /**
     * Deletes a chat and all its associated messages.
     * 
     * @param chat The chat to delete
     * @return true if the chat was successfully deleted
     */
    public boolean deleteChat(Chat chat) {
        if (chat == null) {
            return false;
        }
        
        logger.info("Deleting chat: {}", chat.getName());
        
        if (chat.getId() != null) {
            databaseService.deleteChat(chat.getId());
        }
        
        chats.remove(chat);
        
        if (chat == currentChat) {
            currentChat = null;
        }
        
        return true;
    }
    
    /**
     * Updates the model for a chat.
     * 
     * @param chat The chat to update
     * @param newModelName The new model name
     */
    public void updateChatModel(Chat chat, String newModelName) {
        if (chat == null || newModelName == null) {
            return;
        }
        
        logger.info("Updating chat {} model from {} to {}", 
                   chat.getName(), chat.getLlmModelName(), newModelName);
        
        chat.setLlmModelName(newModelName);
        databaseService.saveChat(chat);
    }
    
    /**
     * Loads messages for a chat if they haven't been loaded yet.
     * 
     * @param chat The chat to load messages for
     */
    public void ensureMessagesLoaded(Chat chat) {
        if (chat.getId() != null && !chat.hasMessages()) {
            logger.debug("Loading messages for chat: {}", chat.getName());
            List<Message> messages = databaseService.getMessagesForChat(chat.getId());
            chat.getMessages().addAll(messages);
        }
    }
    
    /**
     * Creates and saves a new message in the specified chat.
     * 
     * @param chat The chat to add the message to
     * @param content The message content
     * @param isFromUser Whether the message is from the user
     * @return The created and persisted message
     */
    public Message createMessage(Chat chat, String content, boolean isFromUser) {
        Message message = new Message(content, isFromUser, LocalDateTime.now());
        
        if (chat.getId() != null) {
            message = databaseService.saveMessage(message, chat.getId());
        }
        
        chat.getMessages().add(message);
        return message;
    }
    
    /**
     * Gets the current active chat.
     * 
     * @return The current chat, or null if none is selected
     */
    public Chat getCurrentChat() {
        return currentChat;
    }
    
    /**
     * Sets the current active chat.
     * 
     * @param chat The chat to make current
     */
    public void setCurrentChat(Chat chat) {
        this.currentChat = chat;
        if (chat != null) {
            ensureMessagesLoaded(chat);
        }
    }
    
    /**
     * Gets the observable list of all chats.
     * 
     * @return The observable list of chats
     */
    public ObservableList<Chat> getChats() {
        return chats;
    }
    
    /**
     * Generates a unique name for a new chat.
     * 
     * @return A unique chat name
     */
    private String generateUniqueChatName() {
        int nextNumber = chats.size() + 1;
        String baseName = DEFAULT_CHAT_NAME_PREFIX + nextNumber;
        
        // Ensure uniqueness
        while (chatNameExists(baseName)) {
            nextNumber++;
            baseName = DEFAULT_CHAT_NAME_PREFIX + nextNumber;
        }
        
        return baseName;
    }
    
    /**
     * Checks if a chat with the given name already exists.
     * 
     * @param name The name to check
     * @return true if a chat with this name exists
     */
    private boolean chatNameExists(String name) {
        return chats.stream().anyMatch(chat -> chat.getName().equals(name));
    }
    
    /**
     * Creates a welcome chat for first-time users.
     * 
     * @param defaultModelName The default model to use
     */
    private void createWelcomeChat(String defaultModelName) {
        String modelName = defaultModelName != null && !defaultModelName.isEmpty() 
            ? defaultModelName : DEFAULT_FALLBACK_MODEL;
        
        Chat welcomeChat = createChat(WELCOME_CHAT_NAME, modelName);
        createMessage(welcomeChat, WELCOME_MESSAGE_CONTENT, false);
    }
    
    /**
     * Migrates chats that don't have a model assigned.
     * 
     * @param chats The chats to migrate
     * @param defaultModelName The default model to assign
     */
    private void migrateChatsWithoutModels(List<Chat> chats, String defaultModelName) {
        if (defaultModelName == null || defaultModelName.isEmpty()) {
            return;
        }
        
        for (Chat chat : chats) {
            if (chat.getLlmModelName() == null || chat.getLlmModelName().isEmpty()) {
                logger.info("Migrating chat {} to use model {}", chat.getName(), defaultModelName);
                chat.setLlmModelName(defaultModelName);
                databaseService.saveChat(chat);
            }
        }
    }
}
