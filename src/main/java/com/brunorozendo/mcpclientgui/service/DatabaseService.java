package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.brunorozendo.mcpclientgui.model.Chat;
import com.brunorozendo.mcpclientgui.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service for managing database operations.
 * 
 * This singleton service handles all database interactions including:
 * - Chat persistence and retrieval
 * - Message storage and loading
 * - Application settings management
 * - LLM model configuration
 * 
 * The database is stored in the user's home directory under .mcp-client-gui/
 */
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    
    // Database configuration
    private static final String DB_NAME = "mcp_client_gui.db";
    private static final String DB_DIRECTORY_NAME = ".mcp-client-gui";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    
    // SQL Queries - Chats
    private static final String CREATE_CHATS_TABLE = """
        CREATE TABLE IF NOT EXISTS chats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            llm_model_name TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;
    
    // SQL Queries - Messages
    private static final String CREATE_MESSAGES_TABLE = """
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id INTEGER NOT NULL,
            content TEXT NOT NULL,
            from_user BOOLEAN NOT NULL,
            timestamp TIMESTAMP NOT NULL,
            FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
        )
        """;
    
    // SQL Queries - Settings
    private static final String CREATE_SETTINGS_TABLE = """
        CREATE TABLE IF NOT EXISTS app_settings (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            llm_model TEXT,
            mcp_config_file TEXT,
            ollama_base_url TEXT,
            default_llm_model_name TEXT,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;
    
    // SQL Queries - LLM Models
    private static final String CREATE_LLM_MODELS_TABLE = """
        CREATE TABLE IF NOT EXISTS llm_models (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            is_default BOOLEAN NOT NULL DEFAULT 0
        )
        """;
    
    // Singleton instance
    private static DatabaseService instance;
    
    // Database connection
    private Connection connection;
    private final String dbPath;

    /**
     * Private constructor for singleton pattern.
     * Initializes the database connection and creates tables if needed.
     */
    private DatabaseService() {
        this.dbPath = createDatabasePath();
        initializeDatabase();
    }
    
    /**
     * Creates the database directory and returns the full database path.
     * 
     * @return The absolute path to the database file
     */
    private String createDatabasePath() {
        String userHome = System.getProperty("user.home");
        File dbDir = new File(userHome, DB_DIRECTORY_NAME);
        
        if (!dbDir.exists() && !dbDir.mkdirs()) {
            logger.warn("Failed to create database directory: {}", dbDir.getAbsolutePath());
        }
        
        return new File(dbDir, DB_NAME).getAbsolutePath();
    }

    /**
     * Gets the singleton instance of the DatabaseService.
     * 
     * @return The DatabaseService instance
     */
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    /**
     * Initializes the database connection and creates necessary tables.
     * 
     * @throws RuntimeException if database initialization fails
     */
    private void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName(SQLITE_DRIVER);
            
            // Connect to database
            connection = DriverManager.getConnection(JDBC_PREFIX + dbPath);
            logger.info("Connected to database at: {}", dbPath);
            
            // Create tables if they don't exist
            createTables();
        } catch (Exception e) {
            logger.error("Error initializing database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Creates all necessary database tables.
     * 
     * @throws SQLException if table creation fails
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_CHATS_TABLE);
            stmt.execute(CREATE_MESSAGES_TABLE);
            stmt.execute(CREATE_SETTINGS_TABLE);
            stmt.execute(CREATE_LLM_MODELS_TABLE);
            
            // Perform any necessary data migrations
            migrateExistingData();
            
            logger.info("Database tables created/verified successfully");
        }
    }
    
    /**
     * Migrates existing data to new schema if needed.
     * Currently handles migration of old single-model format to multi-model format.
     * 
     * @throws SQLException if migration fails
     */
    private void migrateExistingData() throws SQLException {
        // Check if migration is needed
        if (isAlreadyMigrated()) {
            return;
        }
        
        // Migrate old single model to new models table
        String oldModel = getOldModelFromSettings();
        if (oldModel != null && !oldModel.trim().isEmpty()) {
            migrateOldModel(oldModel);
        }
    }
    
    /**
     * Checks if the database has already been migrated.
     * 
     * @return True if migration has been completed
     * @throws SQLException if query fails
     */
    private boolean isAlreadyMigrated() throws SQLException {
        String query = "SELECT COUNT(*) FROM llm_models";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
    
    /**
     * Gets the old model name from the settings table.
     * 
     * @return The old model name, or null if not found
     * @throws SQLException if query fails
     */
    private String getOldModelFromSettings() throws SQLException {
        String query = "SELECT llm_model FROM app_settings WHERE id = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getString("llm_model");
            }
        }
        return null;
    }
    
    /**
     * Migrates an old model to the new models table.
     * 
     * @param oldModel The model name to migrate
     * @throws SQLException if migration fails
     */
    private void migrateOldModel(String oldModel) throws SQLException {
        // Insert model into new table
        String insertModel = "INSERT OR IGNORE INTO llm_models (name, is_default) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertModel)) {
            pstmt.setString(1, oldModel);
            pstmt.setBoolean(2, true);
            pstmt.executeUpdate();
        }
        
        // Update settings with default model name
        String updateSettings = "UPDATE app_settings SET default_llm_model_name = ? WHERE id = 1";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSettings)) {
            pstmt.setString(1, oldModel);
            pstmt.executeUpdate();
        }
        
        logger.info("Migrated old model '{}' to new format", oldModel);
    }

    // ===== Chat Operations =====
    
    /**
     * Saves a chat to the database.
     * Creates a new chat if ID is null, otherwise updates existing chat.
     * 
     * @param chat The chat to save
     * @return The saved chat with updated ID if newly created
     */
    public Chat saveChat(Chat chat) {
        Objects.requireNonNull(chat, "Chat cannot be null");
        
        if (chat.getId() == null) {
            return insertChat(chat);
        } else {
            return updateChat(chat);
        }
    }

    /**
     * Inserts a new chat into the database.
     * 
     * @param chat The chat to insert
     * @return The chat with its generated ID
     */
    private Chat insertChat(Chat chat) {
        String sql = "INSERT INTO chats (name, llm_model_name) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, chat.getName());
            pstmt.setString(2, chat.getLlmModelName());
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    chat.setId(generatedKeys.getLong(1));
                }
            }
            
            logger.info("Created new chat with ID: {}", chat.getId());
            return chat;
        } catch (SQLException e) {
            logger.error("Error creating chat", e);
            throw new RuntimeException("Failed to create chat", e);
        }
    }

    /**
     * Updates an existing chat in the database.
     * 
     * @param chat The chat to update
     * @return The updated chat
     */
    private Chat updateChat(Chat chat) {
        String sql = "UPDATE chats SET name = ?, llm_model_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, chat.getName());
            pstmt.setString(2, chat.getLlmModelName());
            pstmt.setLong(3, chat.getId());
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("No chat found with ID: {}", chat.getId());
            } else {
                logger.info("Updated chat with ID: {}", chat.getId());
            }
            
            return chat;
        } catch (SQLException e) {
            logger.error("Error updating chat", e);
            throw new RuntimeException("Failed to update chat", e);
        }
    }

    /**
     * Deletes a chat and all its associated messages.
     * 
     * @param chatId The ID of the chat to delete
     */
    public void deleteChat(Long chatId) {
        Objects.requireNonNull(chatId, "Chat ID cannot be null");
        
        String sql = "DELETE FROM chats WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Deleted chat with ID: {}", chatId);
            } else {
                logger.warn("No chat found with ID: {}", chatId);
            }
        } catch (SQLException e) {
            logger.error("Error deleting chat", e);
            throw new RuntimeException("Failed to delete chat", e);
        }
    }

    /**
     * Gets all chats from the database, ordered by most recently updated.
     * 
     * @return List of all chats
     */
    public List<Chat> getAllChats() {
        List<Chat> chats = new ArrayList<>();
        String sql = "SELECT id, name, llm_model_name FROM chats ORDER BY updated_at DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Chat chat = new Chat(
                    rs.getLong("id"), 
                    rs.getString("name"), 
                    rs.getString("llm_model_name")
                );
                chats.add(chat);
            }
            
            logger.debug("Loaded {} chats from database", chats.size());
        } catch (SQLException e) {
            logger.error("Error loading chats", e);
            throw new RuntimeException("Failed to load chats", e);
        }
        
        return chats;
    }

    // ===== Message Operations =====
    
    /**
     * Saves a message to the database.
     * 
     * @param message The message to save
     * @param chatId The ID of the chat this message belongs to
     * @return The saved message with its generated ID
     */
    public Message saveMessage(Message message, Long chatId) {
        Objects.requireNonNull(message, "Message cannot be null");
        Objects.requireNonNull(chatId, "Chat ID cannot be null");
        
        String sql = "INSERT INTO messages (chat_id, content, from_user, timestamp) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, message.getContent());
            pstmt.setBoolean(3, message.isFromUser());
            pstmt.setTimestamp(4, Timestamp.valueOf(message.getTimestamp()));
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message.setId(generatedKeys.getLong(1));
                    message.setChatId(chatId);
                }
            }
            
            // Update chat's updated_at timestamp
            updateChatTimestamp(chatId);
            
            logger.debug("Saved message with ID: {} for chat: {}", message.getId(), chatId);
            return message;
        } catch (SQLException e) {
            logger.error("Error saving message", e);
            throw new RuntimeException("Failed to save message", e);
        }
    }

    /**
     * Updates the last updated timestamp for a chat.
     * 
     * @param chatId The ID of the chat to update
     */
    private void updateChatTimestamp(Long chatId) {
        String sql = "UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating chat timestamp", e);
            // Don't throw exception as this is not critical
        }
    }

    /**
     * Gets all messages for a specific chat.
     * 
     * @param chatId The ID of the chat
     * @return List of messages ordered by timestamp
     */
    public List<Message> getMessagesForChat(Long chatId) {
        Objects.requireNonNull(chatId, "Chat ID cannot be null");
        
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT id, content, from_user, timestamp FROM messages WHERE chat_id = ? ORDER BY timestamp ASC";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message message = new Message(
                        rs.getLong("id"),
                        chatId,
                        rs.getString("content"),
                        rs.getBoolean("from_user"),
                        rs.getTimestamp("timestamp").toLocalDateTime()
                    );
                    messages.add(message);
                }
            }
            
            logger.debug("Loaded {} messages for chat: {}", messages.size(), chatId);
        } catch (SQLException e) {
            logger.error("Error loading messages for chat: {}", chatId, e);
            throw new RuntimeException("Failed to load messages", e);
        }
        
        return messages;
    }

    /**
     * Deletes all messages for a specific chat.
     * 
     * @param chatId The ID of the chat whose messages to delete
     */
    public void deleteMessagesForChat(Long chatId) {
        Objects.requireNonNull(chatId, "Chat ID cannot be null");
        
        String sql = "DELETE FROM messages WHERE chat_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            
            int rowsAffected = pstmt.executeUpdate();
            logger.info("Deleted {} messages for chat: {}", rowsAffected, chatId);
        } catch (SQLException e) {
            logger.error("Error deleting messages", e);
            throw new RuntimeException("Failed to delete messages", e);
        }
    }

    // ===== LLM Model Operations =====
    
    /**
     * Saves or updates an LLM model configuration.
     * 
     * @param model The model to save
     */
    public void saveLlmModel(AppSettings.LlmModel model) {
        Objects.requireNonNull(model, "LLM model cannot be null");
        
        String sql = "INSERT OR REPLACE INTO llm_models (name, is_default) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, model.getName());
            pstmt.setBoolean(2, model.isDefault());
            pstmt.executeUpdate();
            
            logger.info("Saved LLM model: {}", model.getName());
        } catch (SQLException e) {
            logger.error("Error saving LLM model", e);
            throw new RuntimeException("Failed to save LLM model", e);
        }
    }
    
    /**
     * Deletes an LLM model by name.
     * 
     * @param modelName The name of the model to delete
     */
    public void deleteLlmModel(String modelName) {
        Objects.requireNonNull(modelName, "Model name cannot be null");
        
        String sql = "DELETE FROM llm_models WHERE name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, modelName);
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Deleted LLM model: {}", modelName);
            } else {
                logger.warn("No LLM model found with name: {}", modelName);
            }
        } catch (SQLException e) {
            logger.error("Error deleting LLM model", e);
            throw new RuntimeException("Failed to delete LLM model", e);
        }
    }
    
    /**
     * Gets all LLM models from the database.
     * 
     * @return List of all LLM models
     */
    public List<AppSettings.LlmModel> getAllLlmModels() {
        List<AppSettings.LlmModel> models = new ArrayList<>();
        String sql = "SELECT name, is_default FROM llm_models ORDER BY name";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                AppSettings.LlmModel model = new AppSettings.LlmModel(
                    rs.getString("name"),
                    rs.getBoolean("is_default")
                );
                models.add(model);
            }
            
            logger.debug("Loaded {} LLM models from database", models.size());
        } catch (SQLException e) {
            logger.error("Error loading LLM models", e);
            throw new RuntimeException("Failed to load LLM models", e);
        }
        
        return models;
    }
    
    /**
     * Sets a specific model as the default.
     * Ensures only one model is marked as default.
     * 
     * @param modelName The name of the model to set as default
     */
    public void setDefaultLlmModel(String modelName) {
        Objects.requireNonNull(modelName, "Model name cannot be null");
        
        try {
            connection.setAutoCommit(false);
            
            try {
                // First, unset all defaults
                String unsetSql = "UPDATE llm_models SET is_default = 0";
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(unsetSql);
                }
                
                // Then set the new default
                String setSql = "UPDATE llm_models SET is_default = 1 WHERE name = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(setSql)) {
                    pstmt.setString(1, modelName);
                    
                    int rowsAffected = pstmt.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new SQLException("Model not found: " + modelName);
                    }
                }
                
                connection.commit();
                logger.info("Set default LLM model to: {}", modelName);
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
            
        } catch (SQLException e) {
            logger.error("Error setting default LLM model", e);
            throw new RuntimeException("Failed to set default LLM model", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error("Error resetting auto-commit", e);
            }
        }
    }

    // ===== AppSettings Operations =====
    
    /**
     * Loads application settings from the database.
     * Creates default settings if none exist.
     * 
     * @return The loaded or default settings
     */
    public AppSettings loadSettings() {
        AppSettings settings = new AppSettings();
        
        // Load basic settings
        loadBasicSettings(settings);
        
        // Load LLM models
        List<AppSettings.LlmModel> models = getAllLlmModels();
        settings.setLlmModels(models);
        
        // Ensure at least one model exists
        ensureDefaultModel(settings, models);
        
        logger.info("Loaded application settings from database");
        return settings;
    }
    
    /**
     * Loads basic settings (MCP config, Ollama URL) from the database.
     * 
     * @param settings The settings object to populate
     */
    private void loadBasicSettings(AppSettings settings) {
        String sql = "SELECT mcp_config_file, ollama_base_url, default_llm_model_name FROM app_settings WHERE id = 1";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                String mcpConfigPath = rs.getString("mcp_config_file");
                if (mcpConfigPath != null) {
                    settings.setMcpConfigFile(new File(mcpConfigPath));
                }
                
                settings.setOllamaBaseUrl(rs.getString("ollama_base_url"));
                settings.setDefaultLlmModelName(rs.getString("default_llm_model_name"));
            }
        } catch (SQLException e) {
            logger.error("Error loading settings", e);
            // Continue with default settings
        }
    }
    
    /**
     * Ensures at least one default model exists in the settings.
     * 
     * @param settings The settings to update
     * @param models The list of loaded models
     */
    private void ensureDefaultModel(AppSettings settings, List<AppSettings.LlmModel> models) {
        if (models.isEmpty()) {
            // Create default model
            AppSettings.LlmModel defaultModel = new AppSettings.LlmModel("qwen3:14b", true);
            saveLlmModel(defaultModel);
            models.add(defaultModel);
            settings.setDefaultLlmModelName(defaultModel.getName());
        }
    }
    
    /**
     * Saves application settings to the database.
     * 
     * @param settings The settings to save
     */
    public void saveSettings(AppSettings settings) {
        Objects.requireNonNull(settings, "Settings cannot be null");
        
        // Save basic settings
        saveBasicSettings(settings);
        
        // Save LLM models
        saveLlmModels(settings);
        
        logger.info("Saved application settings to database");
    }
    
    /**
     * Saves basic settings to the database.
     * 
     * @param settings The settings to save
     */
    private void saveBasicSettings(AppSettings settings) {
        // Check if settings exist
        boolean exists = settingsExist();
        
        String sql;
        if (exists) {
            sql = "UPDATE app_settings SET mcp_config_file = ?, ollama_base_url = ?, " +
                  "default_llm_model_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        } else {
            sql = "INSERT INTO app_settings (id, mcp_config_file, ollama_base_url, default_llm_model_name) " +
                  "VALUES (1, ?, ?, ?)";
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, settings.getMcpConfigFile() != null ? 
                settings.getMcpConfigFile().getAbsolutePath() : null);
            pstmt.setString(2, settings.getOllamaBaseUrl());
            pstmt.setString(3, settings.getDefaultLlmModelName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving settings", e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }
    
    /**
     * Checks if settings already exist in the database.
     * 
     * @return True if settings exist
     */
    private boolean settingsExist() {
        String checkSql = "SELECT COUNT(*) FROM app_settings WHERE id = 1";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.error("Error checking settings existence", e);
            return false;
        }
    }
    
    /**
     * Saves all LLM models from settings.
     * 
     * @param settings The settings containing models to save
     */
    private void saveLlmModels(AppSettings settings) {
        // Save/update all models
        for (AppSettings.LlmModel model : settings.getLlmModels()) {
            saveLlmModel(model);
        }
        
        // Update default model
        if (settings.getDefaultLlmModelName() != null) {
            setDefaultLlmModel(settings.getDefaultLlmModelName());
        }
    }

    /**
     * Closes the database connection.
     * Should be called when the application shuts down.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }
}
