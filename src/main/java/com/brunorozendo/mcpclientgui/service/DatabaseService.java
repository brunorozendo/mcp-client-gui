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
    private static final String DATABASE_NAME = "mcp_client_gui.db";
    private static final String DATABASE_DIRECTORY = ".mcp-client-gui";
    private static final String JDBC_URL_PREFIX = "jdbc:sqlite:";
    private static final String SQLITE_DRIVER_CLASS = "org.sqlite.JDBC";
    
    // Default values
    private static final String DEFAULT_MODEL_NAME = "qwen3:14b";
    
    // Singleton instance
    private static DatabaseService instance;
    
    // Database connection
    private Connection connection;
    private final String databasePath;

    /**
     * Private constructor for singleton pattern.
     */
    private DatabaseService() {
        this.databasePath = createDatabasePath();
        initializeDatabase();
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
     * Creates the database directory and returns the full database path.
     * 
     * @return The absolute path to the database file
     */
    private String createDatabasePath() {
        String userHome = System.getProperty("user.home");
        File databaseDirectory = new File(userHome, DATABASE_DIRECTORY);
        
        if (!databaseDirectory.exists()) {
            boolean created = databaseDirectory.mkdirs();
            if (!created) {
                logger.warn("Failed to create database directory: {}", databaseDirectory.getAbsolutePath());
            }
        }
        
        File databaseFile = new File(databaseDirectory, DATABASE_NAME);
        return databaseFile.getAbsolutePath();
    }

    /**
     * Initializes the database connection and creates necessary tables.
     * 
     * @throws RuntimeException if database initialization fails
     */
    private void initializeDatabase() {
        try {
            loadSQLiteDriver();
            establishConnection();
            createTablesIfNeeded();
            performDataMigrations();
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Loads the SQLite JDBC driver.
     * 
     * @throws ClassNotFoundException if driver is not found
     */
    private void loadSQLiteDriver() throws ClassNotFoundException {
        Class.forName(SQLITE_DRIVER_CLASS);
        logger.debug("SQLite JDBC driver loaded successfully");
    }
    
    /**
     * Establishes connection to the database.
     * 
     * @throws SQLException if connection fails
     */
    private void establishConnection() throws SQLException {
        String jdbcUrl = JDBC_URL_PREFIX + databasePath;
        connection = DriverManager.getConnection(jdbcUrl);
        logger.info("Connected to database at: {}", databasePath);
    }

    /**
     * Creates all necessary database tables if they don't exist.
     * 
     * @throws SQLException if table creation fails
     */
    private void createTablesIfNeeded() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(DatabaseQueries.CREATE_CHATS_TABLE);
            statement.execute(DatabaseQueries.CREATE_MESSAGES_TABLE);
            statement.execute(DatabaseQueries.CREATE_SETTINGS_TABLE);
            statement.execute(DatabaseQueries.CREATE_LLM_MODELS_TABLE);
            
            logger.info("Database tables created/verified successfully");
        }
    }
    
    /**
     * Performs any necessary data migrations.
     * 
     * @throws SQLException if migration fails
     */
    private void performDataMigrations() throws SQLException {
        if (!needsMigration()) {
            return;
        }
        
        String oldModelName = getOldModelName();
        if (oldModelName != null && !oldModelName.trim().isEmpty()) {
            migrateToNewModelFormat(oldModelName);
        }
    }
    
    /**
     * Checks if database needs migration.
     * 
     * @return true if migration is needed
     * @throws SQLException if check fails
     */
    private boolean needsMigration() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(DatabaseQueries.COUNT_MODELS)) {
            return resultSet.next() && resultSet.getInt(1) == 0;
        }
    }
    
    /**
     * Gets the old model name from settings table.
     * 
     * @return the old model name or null
     * @throws SQLException if query fails
     */
    private String getOldModelName() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(DatabaseQueries.SELECT_OLD_MODEL)) {
            if (resultSet.next()) {
                return resultSet.getString("llm_model");
            }
        }
        return null;
    }
    
    /**
     * Migrates old single-model format to new multi-model format.
     * 
     * @param oldModelName the model name to migrate
     * @throws SQLException if migration fails
     */
    private void migrateToNewModelFormat(String oldModelName) throws SQLException {
        // Insert model into new table
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.INSERT_MODEL_IF_NOT_EXISTS)) {
            statement.setString(1, oldModelName);
            statement.setBoolean(2, true);
            statement.executeUpdate();
        }
        
        // Update settings with default model name
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.UPDATE_DEFAULT_MODEL_NAME)) {
            statement.setString(1, oldModelName);
            statement.executeUpdate();
        }
        
        logger.info("Migrated old model '{}' to new format", oldModelName);
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
        validateNotNull(chat, "Chat");
        
        if (chat.getId() == null) {
            return createNewChat(chat);
        } else {
            return updateExistingChat(chat);
        }
    }

    /**
     * Creates a new chat in the database.
     * 
     * @param chat The chat to create
     * @return The chat with its generated ID
     */
    private Chat createNewChat(Chat chat) {
        try (PreparedStatement statement = connection.prepareStatement(
                DatabaseQueries.INSERT_CHAT, Statement.RETURN_GENERATED_KEYS)) {
            
            statement.setString(1, chat.getName());
            statement.setString(2, chat.getLlmModelName());
            statement.executeUpdate();
            
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    chat.setId(generatedKeys.getLong(1));
                }
            }
            
            logger.info("Created new chat with ID: {}", chat.getId());
            return chat;
            
        } catch (SQLException e) {
            logger.error("Failed to create chat", e);
            throw new RuntimeException("Failed to create chat", e);
        }
    }

    /**
     * Updates an existing chat in the database.
     * 
     * @param chat The chat to update
     * @return The updated chat
     */
    private Chat updateExistingChat(Chat chat) {
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.UPDATE_CHAT)) {
            statement.setString(1, chat.getName());
            statement.setString(2, chat.getLlmModelName());
            statement.setLong(3, chat.getId());
            
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("No chat found with ID: {}", chat.getId());
            } else {
                logger.info("Updated chat with ID: {}", chat.getId());
            }
            
            return chat;
            
        } catch (SQLException e) {
            logger.error("Failed to update chat", e);
            throw new RuntimeException("Failed to update chat", e);
        }
    }

    /**
     * Deletes a chat and all its associated messages.
     * 
     * @param chatId The ID of the chat to delete
     */
    public void deleteChat(Long chatId) {
        validateNotNull(chatId, "Chat ID");
        
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.DELETE_CHAT)) {
            statement.setLong(1, chatId);
            
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Deleted chat with ID: {}", chatId);
            } else {
                logger.warn("No chat found with ID: {}", chatId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to delete chat", e);
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
        
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(DatabaseQueries.SELECT_ALL_CHATS)) {
            
            while (resultSet.next()) {
                Chat chat = extractChatFromResultSet(resultSet);
                chats.add(chat);
            }
            
            logger.debug("Loaded {} chats from database", chats.size());
            
        } catch (SQLException e) {
            logger.error("Failed to load chats", e);
            throw new RuntimeException("Failed to load chats", e);
        }
        
        return chats;
    }
    
    /**
     * Extracts a Chat object from a ResultSet.
     * 
     * @param resultSet The result set to extract from
     * @return The extracted Chat
     * @throws SQLException if extraction fails
     */
    private Chat extractChatFromResultSet(ResultSet resultSet) throws SQLException {
        return new Chat(
            resultSet.getLong("id"), 
            resultSet.getString("name"), 
            resultSet.getString("llm_model_name")
        );
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
        validateNotNull(message, "Message");
        validateNotNull(chatId, "Chat ID");
        
        try (PreparedStatement statement = connection.prepareStatement(
                DatabaseQueries.INSERT_MESSAGE, Statement.RETURN_GENERATED_KEYS)) {
            
            statement.setLong(1, chatId);
            statement.setString(2, message.getContent());
            statement.setBoolean(3, message.isFromUser());
            statement.setTimestamp(4, Timestamp.valueOf(message.getTimestamp()));
            statement.executeUpdate();
            
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message.setId(generatedKeys.getLong(1));
                    message.setChatId(chatId);
                }
            }
            
            updateChatLastModified(chatId);
            
            logger.debug("Saved message with ID: {} for chat: {}", message.getId(), chatId);
            return message;
            
        } catch (SQLException e) {
            logger.error("Failed to save message", e);
            throw new RuntimeException("Failed to save message", e);
        }
    }
    
    /**
     * Updates the last modified timestamp for a chat.
     * 
     * @param chatId The ID of the chat to update
     */
    private void updateChatLastModified(Long chatId) {
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.UPDATE_CHAT_TIMESTAMP)) {
            statement.setLong(1, chatId);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update chat timestamp", e);
            // Non-critical error, don't throw exception
        }
    }

    /**
     * Gets all messages for a specific chat.
     * 
     * @param chatId The ID of the chat
     * @return List of messages ordered by timestamp
     */
    public List<Message> getMessagesForChat(Long chatId) {
        validateNotNull(chatId, "Chat ID");
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.SELECT_MESSAGES_FOR_CHAT)) {
            statement.setLong(1, chatId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Message message = extractMessageFromResultSet(resultSet, chatId);
                    messages.add(message);
                }
            }
            
            logger.debug("Loaded {} messages for chat: {}", messages.size(), chatId);
            
        } catch (SQLException e) {
            logger.error("Failed to load messages for chat: {}", chatId, e);
            throw new RuntimeException("Failed to load messages", e);
        }
        
        return messages;
    }
    
    /**
     * Extracts a Message object from a ResultSet.
     * 
     * @param resultSet The result set to extract from
     * @param chatId The chat ID for the message
     * @return The extracted Message
     * @throws SQLException if extraction fails
     */
    private Message extractMessageFromResultSet(ResultSet resultSet, Long chatId) throws SQLException {
        return new Message(
            resultSet.getLong("id"),
            chatId,
            resultSet.getString("content"),
            resultSet.getBoolean("from_user"),
            resultSet.getTimestamp("timestamp").toLocalDateTime()
        );
    }

    /**
     * Deletes all messages for a specific chat.
     * 
     * @param chatId The ID of the chat whose messages to delete
     */
    public void deleteMessagesForChat(Long chatId) {
        validateNotNull(chatId, "Chat ID");
        
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.DELETE_MESSAGES_FOR_CHAT)) {
            statement.setLong(1, chatId);
            
            int rowsAffected = statement.executeUpdate();
            logger.info("Deleted {} messages for chat: {}", rowsAffected, chatId);
            
        } catch (SQLException e) {
            logger.error("Failed to delete messages", e);
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
        validateNotNull(model, "LLM model");
        
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.INSERT_OR_REPLACE_MODEL)) {
            statement.setString(1, model.getName());
            statement.setBoolean(2, model.isDefault());
            statement.executeUpdate();
            
            logger.info("Saved LLM model: {}", model.getName());
            
        } catch (SQLException e) {
            logger.error("Failed to save LLM model", e);
            throw new RuntimeException("Failed to save LLM model", e);
        }
    }
    
    /**
     * Deletes an LLM model by name.
     * 
     * @param modelName The name of the model to delete
     */
    public void deleteLlmModel(String modelName) {
        validateNotNull(modelName, "Model name");
        
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.DELETE_MODEL)) {
            statement.setString(1, modelName);
            
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Deleted LLM model: {}", modelName);
            } else {
                logger.warn("No LLM model found with name: {}", modelName);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to delete LLM model", e);
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
        
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(DatabaseQueries.SELECT_ALL_MODELS)) {
            
            while (resultSet.next()) {
                AppSettings.LlmModel model = extractModelFromResultSet(resultSet);
                models.add(model);
            }
            
            logger.debug("Loaded {} LLM models from database", models.size());
            
        } catch (SQLException e) {
            logger.error("Failed to load LLM models", e);
            throw new RuntimeException("Failed to load LLM models", e);
        }
        
        return models;
    }
    
    /**
     * Extracts an LlmModel object from a ResultSet.
     * 
     * @param resultSet The result set to extract from
     * @return The extracted LlmModel
     * @throws SQLException if extraction fails
     */
    private AppSettings.LlmModel extractModelFromResultSet(ResultSet resultSet) throws SQLException {
        return new AppSettings.LlmModel(
            resultSet.getString("name"),
            resultSet.getBoolean("is_default")
        );
    }
    
    /**
     * Sets a specific model as the default.
     * Ensures only one model is marked as default.
     * 
     * @param modelName The name of the model to set as default
     */
    public void setDefaultLlmModel(String modelName) {
        validateNotNull(modelName, "Model name");
        
        try {
            connection.setAutoCommit(false);
            
            try {
                // Clear all defaults
                clearAllDefaultModels();
                
                // Set new default
                setModelAsDefault(modelName);
                
                connection.commit();
                logger.info("Set default LLM model to: {}", modelName);
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to set default LLM model", e);
            throw new RuntimeException("Failed to set default LLM model", e);
        } finally {
            resetAutoCommit();
        }
    }
    
    /**
     * Clears the default flag from all models.
     * 
     * @throws SQLException if operation fails
     */
    private void clearAllDefaultModels() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(DatabaseQueries.UNSET_ALL_DEFAULT_MODELS);
        }
    }
    
    /**
     * Sets a specific model as default.
     * 
     * @param modelName The model name
     * @throws SQLException if operation fails or model not found
     */
    private void setModelAsDefault(String modelName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DatabaseQueries.SET_DEFAULT_MODEL)) {
            statement.setString(1, modelName);
            
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Model not found: " + modelName);
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
        
        loadBasicSettings(settings);
        loadLlmModels(settings);
        ensureDefaultModelExists(settings);
        
        logger.info("Loaded application settings from database");
        return settings;
    }
    
    /**
     * Loads basic settings from the database.
     * 
     * @param settings The settings object to populate
     */
    private void loadBasicSettings(AppSettings settings) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(DatabaseQueries.SELECT_SETTINGS)) {
            
            if (resultSet.next()) {
                extractBasicSettings(resultSet, settings);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to load settings", e);
            // Continue with default settings
        }
    }
    
    /**
     * Extracts basic settings from a ResultSet.
     * 
     * @param resultSet The result set to extract from
     * @param settings The settings object to populate
     * @throws SQLException if extraction fails
     */
    private void extractBasicSettings(ResultSet resultSet, AppSettings settings) throws SQLException {
        String mcpConfigPath = resultSet.getString("mcp_config_file");
        if (mcpConfigPath != null) {
            settings.setMcpConfigFile(new File(mcpConfigPath));
        }
        
        settings.setOllamaBaseUrl(resultSet.getString("ollama_base_url"));
        settings.setDefaultLlmModelName(resultSet.getString("default_llm_model_name"));
    }
    
    /**
     * Loads LLM models into settings.
     * 
     * @param settings The settings object to populate
     */
    private void loadLlmModels(AppSettings settings) {
        List<AppSettings.LlmModel> models = getAllLlmModels();
        settings.setLlmModels(models);
    }
    
    /**
     * Ensures at least one default model exists.
     * 
     * @param settings The settings to check and update
     */
    private void ensureDefaultModelExists(AppSettings settings) {
        if (settings.getLlmModels().isEmpty()) {
            AppSettings.LlmModel defaultModel = new AppSettings.LlmModel(DEFAULT_MODEL_NAME, true);
            saveLlmModel(defaultModel);
            settings.getLlmModels().add(defaultModel);
            settings.setDefaultLlmModelName(defaultModel.getName());
        }
    }
    
    /**
     * Saves application settings to the database.
     * 
     * @param settings The settings to save
     */
    public void saveSettings(AppSettings settings) {
        validateNotNull(settings, "Settings");
        
        saveBasicSettings(settings);
        saveAllModels(settings);
        
        logger.info("Saved application settings to database");
    }
    
    /**
     * Saves basic settings to the database.
     * 
     * @param settings The settings to save
     */
    private void saveBasicSettings(AppSettings settings) {
        boolean exists = doesSettingsRecordExist();
        String query = exists ? DatabaseQueries.UPDATE_SETTINGS : DatabaseQueries.INSERT_SETTINGS;
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            populateSettingsStatement(statement, settings);
            statement.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to save settings", e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }
    
    /**
     * Checks if a settings record already exists.
     * 
     * @return true if settings exist
     */
    private boolean doesSettingsRecordExist() {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(DatabaseQueries.COUNT_SETTINGS)) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        } catch (SQLException e) {
            logger.error("Failed to check settings existence", e);
            return false;
        }
    }
    
    /**
     * Populates a PreparedStatement with settings values.
     * 
     * @param statement The statement to populate
     * @param settings The settings containing values
     * @throws SQLException if population fails
     */
    private void populateSettingsStatement(PreparedStatement statement, AppSettings settings) throws SQLException {
        statement.setString(1, settings.getMcpConfigFile() != null ? 
            settings.getMcpConfigFile().getAbsolutePath() : null);
        statement.setString(2, settings.getOllamaBaseUrl());
        statement.setString(3, settings.getDefaultLlmModelName());
    }
    
    /**
     * Saves all models from settings.
     * 
     * @param settings The settings containing models
     */
    private void saveAllModels(AppSettings settings) {
        for (AppSettings.LlmModel model : settings.getLlmModels()) {
            saveLlmModel(model);
        }
        
        if (settings.getDefaultLlmModelName() != null) {
            setDefaultLlmModel(settings.getDefaultLlmModelName());
        }
    }
    
    // ===== Utility Methods =====
    
    /**
     * Validates that an object is not null.
     * 
     * @param object The object to validate
     * @param name The name of the object for error messages
     * @throws NullPointerException if object is null
     */
    private void validateNotNull(Object object, String name) {
        Objects.requireNonNull(object, name + " cannot be null");
    }
    
    /**
     * Resets auto-commit mode to true.
     */
    private void resetAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            logger.error("Failed to reset auto-commit", e);
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
            logger.error("Failed to close database connection", e);
        }
    }
}
