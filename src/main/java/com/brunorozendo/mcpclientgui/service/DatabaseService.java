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

public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static final String DB_NAME = "mcp_client_gui.db";
    private static DatabaseService instance;
    private Connection connection;
    private final String dbPath;

    private DatabaseService() {
        // Create directory if it doesn't exist
        String userHome = System.getProperty("user.home");
        File dbDir = new File(userHome, ".mcp-client-gui");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        
        dbPath = new File(dbDir, DB_NAME).getAbsolutePath();
        initializeDatabase();
    }

    public static DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    private void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            // Connect to database
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            logger.info("Connected to database at: " + dbPath);
            
            // Create tables if they don't exist
            createTables();
        } catch (Exception e) {
            logger.error("Error initializing database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void createTables() throws SQLException {
        String createChatsTable = """
            CREATE TABLE IF NOT EXISTS chats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                llm_model_name TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String createMessagesTable = """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id INTEGER NOT NULL,
                content TEXT NOT NULL,
                from_user BOOLEAN NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
            )
            """;

        String createSettingsTable = """
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                llm_model TEXT,
                mcp_config_file TEXT,
                ollama_base_url TEXT,
                default_llm_model_name TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String createLlmModelsTable = """
            CREATE TABLE IF NOT EXISTS llm_models (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                is_default BOOLEAN NOT NULL DEFAULT 0
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createChatsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createSettingsTable);
            stmt.execute(createLlmModelsTable);
            
            // Migrate existing data if needed
            migrateExistingData();
            
            logger.info("Database tables created successfully");
        }
    }
    
    private void migrateExistingData() throws SQLException {
        // Check if migration is needed
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM llm_models")) {
            if (rs.next() && rs.getInt(1) > 0) {
                // Already migrated
                return;
            }
        }
        
        // Migrate existing llm_model from settings to llm_models table
        String selectOldModel = "SELECT llm_model FROM app_settings WHERE id = 1";
        String insertModel = "INSERT OR IGNORE INTO llm_models (name, is_default) VALUES (?, ?)";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(selectOldModel)) {
            if (rs.next()) {
                String oldModel = rs.getString("llm_model");
                if (oldModel != null && !oldModel.trim().isEmpty()) {
                    try (PreparedStatement pstmt = connection.prepareStatement(insertModel)) {
                        pstmt.setString(1, oldModel);
                        pstmt.setBoolean(2, true);
                        pstmt.executeUpdate();
                        
                        // Update settings with default model name
                        String updateSettings = "UPDATE app_settings SET default_llm_model_name = ? WHERE id = 1";
                        try (PreparedStatement updatePstmt = connection.prepareStatement(updateSettings)) {
                            updatePstmt.setString(1, oldModel);
                            updatePstmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    // Chat operations
    public Chat saveChat(Chat chat) {
        if (chat.getId() == null) {
            return insertChat(chat);
        } else {
            return updateChat(chat);
        }
    }

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
            logger.info("Chat saved with ID: " + chat.getId());
            return chat;
        } catch (SQLException e) {
            logger.error("Error saving chat", e);
            throw new RuntimeException("Failed to save chat", e);
        }
    }

    private Chat updateChat(Chat chat) {
        String sql = "UPDATE chats SET name = ?, llm_model_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, chat.getName());
            pstmt.setString(2, chat.getLlmModelName());
            pstmt.setLong(3, chat.getId());
            pstmt.executeUpdate();
            logger.info("Chat updated: " + chat.getId());
            return chat;
        } catch (SQLException e) {
            logger.error("Error updating chat", e);
            throw new RuntimeException("Failed to update chat", e);
        }
    }

    public void deleteChat(Long chatId) {
        String sql = "DELETE FROM chats WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
            logger.info("Chat deleted: " + chatId);
        } catch (SQLException e) {
            logger.error("Error deleting chat", e);
            throw new RuntimeException("Failed to delete chat", e);
        }
    }

    public List<Chat> getAllChats() {
        List<Chat> chats = new ArrayList<>();
        String sql = "SELECT id, name, llm_model_name FROM chats ORDER BY updated_at DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Chat chat = new Chat(rs.getLong("id"), rs.getString("name"), rs.getString("llm_model_name"));
                chats.add(chat);
            }
        } catch (SQLException e) {
            logger.error("Error loading chats", e);
            throw new RuntimeException("Failed to load chats", e);
        }
        
        return chats;
    }

    // Message operations
    public Message saveMessage(Message message, Long chatId) {
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
            
            return message;
        } catch (SQLException e) {
            logger.error("Error saving message", e);
            throw new RuntimeException("Failed to save message", e);
        }
    }

    private void updateChatTimestamp(Long chatId) {
        String sql = "UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating chat timestamp", e);
        }
    }

    public List<Message> getMessagesForChat(Long chatId) {
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
        } catch (SQLException e) {
            logger.error("Error loading messages for chat: " + chatId, e);
            throw new RuntimeException("Failed to load messages", e);
        }
        
        return messages;
    }

    public void deleteMessagesForChat(Long chatId) {
        String sql = "DELETE FROM messages WHERE chat_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
            logger.info("Messages deleted for chat: " + chatId);
        } catch (SQLException e) {
            logger.error("Error deleting messages", e);
            throw new RuntimeException("Failed to delete messages", e);
        }
    }

    // LLM Model operations
    public void saveLlmModel(AppSettings.LlmModel model) {
        String sql = "INSERT OR REPLACE INTO llm_models (name, is_default) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, model.getName());
            pstmt.setBoolean(2, model.isDefault());
            pstmt.executeUpdate();
            logger.info("LLM model saved: " + model.getName());
        } catch (SQLException e) {
            logger.error("Error saving LLM model", e);
            throw new RuntimeException("Failed to save LLM model", e);
        }
    }
    
    public void deleteLlmModel(String modelName) {
        String sql = "DELETE FROM llm_models WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, modelName);
            pstmt.executeUpdate();
            logger.info("LLM model deleted: " + modelName);
        } catch (SQLException e) {
            logger.error("Error deleting LLM model", e);
            throw new RuntimeException("Failed to delete LLM model", e);
        }
    }
    
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
        } catch (SQLException e) {
            logger.error("Error loading LLM models", e);
            throw new RuntimeException("Failed to load LLM models", e);
        }
        
        return models;
    }
    
    public void setDefaultLlmModel(String modelName) {
        // First, unset all defaults
        String unsetSql = "UPDATE llm_models SET is_default = 0";
        // Then set the new default
        String setSql = "UPDATE llm_models SET is_default = 1 WHERE name = ?";
        
        try {
            connection.setAutoCommit(false);
            
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(unsetSql);
            }
            
            try (PreparedStatement pstmt = connection.prepareStatement(setSql)) {
                pstmt.setString(1, modelName);
                pstmt.executeUpdate();
            }
            
            connection.commit();
            logger.info("Default LLM model set to: " + modelName);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.error("Error rolling back transaction", rollbackEx);
            }
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

    // AppSettings operations
    public AppSettings loadSettings() {
        String sql = "SELECT mcp_config_file, ollama_base_url, default_llm_model_name FROM app_settings WHERE id = 1";
        
        AppSettings settings = new AppSettings();
        
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
        }
        
        // Load LLM models
        List<AppSettings.LlmModel> models = getAllLlmModels();
        settings.setLlmModels(models);
        
        // If no models exist, add the default one
        if (models.isEmpty()) {
            AppSettings.LlmModel defaultModel = new AppSettings.LlmModel("qwen3:14b", true);
            saveLlmModel(defaultModel);
            models.add(defaultModel);
            settings.setDefaultLlmModelName(defaultModel.getName());
        }
        
        logger.info("Settings loaded from database");
        return settings;
    }
    
    public void saveSettings(AppSettings settings) {
        // First check if settings exist
        String checkSql = "SELECT COUNT(*) FROM app_settings WHERE id = 1";
        boolean exists = false;
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Error checking settings existence", e);
        }
        
        String sql;
        if (exists) {
            sql = "UPDATE app_settings SET mcp_config_file = ?, ollama_base_url = ?, default_llm_model_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        } else {
            sql = "INSERT INTO app_settings (id, mcp_config_file, ollama_base_url, default_llm_model_name) VALUES (1, ?, ?, ?)";
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, settings.getMcpConfigFile() != null ? settings.getMcpConfigFile().getAbsolutePath() : null);
            pstmt.setString(2, settings.getOllamaBaseUrl());
            pstmt.setString(3, settings.getDefaultLlmModelName());
            pstmt.executeUpdate();
            
            // Save/update LLM models
            for (AppSettings.LlmModel model : settings.getLlmModels()) {
                saveLlmModel(model);
            }
            
            // Update default model in the llm_models table
            if (settings.getDefaultLlmModelName() != null) {
                setDefaultLlmModel(settings.getDefaultLlmModelName());
            }
            
            logger.info("Settings saved to database");
        } catch (SQLException e) {
            logger.error("Error saving settings", e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }

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
