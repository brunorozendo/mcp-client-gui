package com.brunorozendo.mcpclientgui.service;

/**
 * Central repository for all SQL queries used in the application.
 * Keeps SQL statements organized and maintainable in one location.
 */
class DatabaseQueries {
    
    // ===== Table Creation Queries =====
    
    static final String CREATE_CHATS_TABLE = """
        CREATE TABLE IF NOT EXISTS chats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            llm_model_name TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;
    
    static final String CREATE_MESSAGES_TABLE = """
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id INTEGER NOT NULL,
            content TEXT NOT NULL,
            from_user BOOLEAN NOT NULL,
            timestamp TIMESTAMP NOT NULL,
            FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
        )
        """;
    
    static final String CREATE_SETTINGS_TABLE = """
        CREATE TABLE IF NOT EXISTS app_settings (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            llm_model TEXT,
            mcp_config_file TEXT,
            ollama_base_url TEXT,
            default_llm_model_name TEXT,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;
    
    static final String CREATE_LLM_MODELS_TABLE = """
        CREATE TABLE IF NOT EXISTS llm_models (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            is_default BOOLEAN NOT NULL DEFAULT 0
        )
        """;
    
    // ===== Chat Queries =====
    
    static final String INSERT_CHAT = 
        "INSERT INTO chats (name, llm_model_name) VALUES (?, ?)";
    
    static final String UPDATE_CHAT = 
        "UPDATE chats SET name = ?, llm_model_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
    
    static final String DELETE_CHAT = 
        "DELETE FROM chats WHERE id = ?";
    
    static final String SELECT_ALL_CHATS = 
        "SELECT id, name, llm_model_name FROM chats ORDER BY updated_at DESC";
    
    static final String UPDATE_CHAT_TIMESTAMP = 
        "UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";
    
    // ===== Message Queries =====
    
    static final String INSERT_MESSAGE = 
        "INSERT INTO messages (chat_id, content, from_user, timestamp) VALUES (?, ?, ?, ?)";
    
    static final String SELECT_MESSAGES_FOR_CHAT = 
        "SELECT id, content, from_user, timestamp FROM messages WHERE chat_id = ? ORDER BY timestamp ASC";
    
    static final String DELETE_MESSAGES_FOR_CHAT = 
        "DELETE FROM messages WHERE chat_id = ?";
    
    // ===== LLM Model Queries =====
    
    static final String INSERT_OR_REPLACE_MODEL = 
        "INSERT OR REPLACE INTO llm_models (name, is_default) VALUES (?, ?)";
    
    static final String DELETE_MODEL = 
        "DELETE FROM llm_models WHERE name = ?";
    
    static final String SELECT_ALL_MODELS = 
        "SELECT name, is_default FROM llm_models ORDER BY name";
    
    static final String UNSET_ALL_DEFAULT_MODELS = 
        "UPDATE llm_models SET is_default = 0";
    
    static final String SET_DEFAULT_MODEL = 
        "UPDATE llm_models SET is_default = 1 WHERE name = ?";
    
    // ===== Settings Queries =====
    
    static final String SELECT_SETTINGS = 
        "SELECT mcp_config_file, ollama_base_url, default_llm_model_name FROM app_settings WHERE id = 1";
    
    static final String UPDATE_SETTINGS = 
        "UPDATE app_settings SET mcp_config_file = ?, ollama_base_url = ?, " +
        "default_llm_model_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
    
    static final String INSERT_SETTINGS = 
        "INSERT INTO app_settings (id, mcp_config_file, ollama_base_url, default_llm_model_name) " +
        "VALUES (1, ?, ?, ?)";
    
    static final String COUNT_SETTINGS = 
        "SELECT COUNT(*) FROM app_settings WHERE id = 1";
    
    // ===== Migration Queries =====
    
    static final String COUNT_MODELS = 
        "SELECT COUNT(*) FROM llm_models";
    
    static final String SELECT_OLD_MODEL = 
        "SELECT llm_model FROM app_settings WHERE id = 1";
    
    static final String INSERT_MODEL_IF_NOT_EXISTS = 
        "INSERT OR IGNORE INTO llm_models (name, is_default) VALUES (?, ?)";
    
    static final String UPDATE_DEFAULT_MODEL_NAME = 
        "UPDATE app_settings SET default_llm_model_name = ? WHERE id = 1";
    
    // Private constructor to prevent instantiation
    private DatabaseQueries() {}
}
