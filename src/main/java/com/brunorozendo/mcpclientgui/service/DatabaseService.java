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
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createChatsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createSettingsTable);
            logger.info("Database tables created successfully");
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
        String sql = "INSERT INTO chats (name) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, chat.getName());
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
        String sql = "UPDATE chats SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, chat.getName());
            pstmt.setLong(2, chat.getId());
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
        String sql = "SELECT id, name FROM chats ORDER BY updated_at DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Chat chat = new Chat(rs.getLong("id"), rs.getString("name"));
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

    // AppSettings operations
    public AppSettings loadSettings() {
        String sql = "SELECT llm_model, mcp_config_file, ollama_base_url FROM app_settings WHERE id = 1";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                AppSettings settings = new AppSettings();
                settings.setLlmModel(rs.getString("llm_model"));
                
                String mcpConfigPath = rs.getString("mcp_config_file");
                if (mcpConfigPath != null) {
                    settings.setMcpConfigFile(new File(mcpConfigPath));
                }
                
                settings.setOllamaBaseUrl(rs.getString("ollama_base_url"));
                logger.info("Settings loaded from database");
                return settings;
            }
        } catch (SQLException e) {
            logger.error("Error loading settings", e);
        }
        
        // Return default settings if none exist
        logger.info("No settings found in database, using defaults");
        return new AppSettings();
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
            sql = "UPDATE app_settings SET llm_model = ?, mcp_config_file = ?, ollama_base_url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        } else {
            sql = "INSERT INTO app_settings (id, llm_model, mcp_config_file, ollama_base_url) VALUES (1, ?, ?, ?)";
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, settings.getLlmModel());
            pstmt.setString(2, settings.getMcpConfigFile() != null ? settings.getMcpConfigFile().getAbsolutePath() : null);
            pstmt.setString(3, settings.getOllamaBaseUrl());
            pstmt.executeUpdate();
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
