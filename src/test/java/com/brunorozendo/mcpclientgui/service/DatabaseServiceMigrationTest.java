package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.brunorozendo.mcpclientgui.model.LlmProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database migration tests. Every test uses its own temporary database file.
 */
class DatabaseServiceMigrationTest {

    private static final String ANTHROPIC = LlmProvider.DEFAULT_ANTHROPIC_MODEL_NAME;

    @TempDir
    Path tempDir;

    private String dbPath(String name) {
        return tempDir.resolve(name).toAbsolutePath().toString();
    }

    /** Creates a database with the schema and data of the release before this feature. */
    private void createLegacyDatabase(String path) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
                    llm_model_name TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""");
            statement.execute("""
                CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER NOT NULL,
                    content TEXT NOT NULL, from_user BOOLEAN NOT NULL, timestamp TIMESTAMP NOT NULL,
                    FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE)""");
            statement.execute("""
                CREATE TABLE app_settings (id INTEGER PRIMARY KEY CHECK (id = 1), llm_model TEXT,
                    mcp_config_file TEXT, ollama_base_url TEXT, default_llm_model_name TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""");
            statement.execute("""
                CREATE TABLE llm_models (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE,
                    is_default BOOLEAN NOT NULL DEFAULT 0)""");
            statement.execute("INSERT INTO llm_models (name, is_default) VALUES ('qwen3:14b', 1), ('llama3:8b', 0)");
            statement.execute("INSERT INTO app_settings (id, mcp_config_file, ollama_base_url, default_llm_model_name) "
                + "VALUES (1, '/tmp/mcp.json', 'http://localhost:11434', 'qwen3:14b')");
            statement.execute("INSERT INTO chats (name, llm_model_name) VALUES ('Old chat', 'qwen3:14b')");
        }
    }

    private static int userVersion(String path) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            return resultSet.getInt(1);
        }
    }

    @Test
    void freshInstallDefaultsToAnthropic() throws Exception {
        String path = dbPath("fresh.db");
        DatabaseService db = new DatabaseService(path);
        try {
            AppSettings settings = db.loadSettings();
            assertEquals(ANTHROPIC, settings.getDefaultLlmModelName());
            assertEquals(List.of(ANTHROPIC), settings.getLlmModels().stream().map(AppSettings.LlmModel::getName).toList());
            assertTrue(settings.getLlmModels().get(0).isDefault());
            assertNull(settings.getAnthropicApiKey());
            assertEquals(1, db.getSchemaVersion());
        } finally {
            db.close();
        }
    }

    @Test
    void existingDatabaseGetsAnthropicDefaultOnceAndKeepsOllamaModelsAndChats() throws Exception {
        String path = dbPath("legacy.db");
        createLegacyDatabase(path);

        DatabaseService db = new DatabaseService(path);
        try {
            AppSettings settings = db.loadSettings();
            assertEquals(ANTHROPIC, settings.getDefaultLlmModelName());
            List<String> names = settings.getLlmModels().stream().map(AppSettings.LlmModel::getName).toList();
            assertTrue(names.containsAll(List.of(ANTHROPIC, "qwen3:14b", "llama3:8b")), names.toString());
            assertEquals(1, settings.getLlmModels().stream().filter(AppSettings.LlmModel::isDefault).count());
            assertEquals("http://localhost:11434", settings.getOllamaBaseUrl());
            assertEquals(new File("/tmp/mcp.json").getAbsolutePath(), settings.getMcpConfigFile().getAbsolutePath());

            // Existing chats keep their model
            assertEquals("qwen3:14b", db.getAllChats().get(0).getLlmModelName());

            // The user later picks an Ollama model as default again
            settings.setDefaultLlmModelName("qwen3:14b");
            db.saveSettings(settings);
        } finally {
            db.close();
        }
        assertEquals(1, userVersion(path));

        // Re-opening must not re-apply the migration
        DatabaseService reopened = new DatabaseService(path);
        try {
            AppSettings settings = reopened.loadSettings();
            assertEquals("qwen3:14b", settings.getDefaultLlmModelName());
            assertTrue(settings.getLlmModelByName("qwen3:14b").isDefault());
            assertFalse(settings.getLlmModelByName(ANTHROPIC).isDefault());
        } finally {
            reopened.close();
        }
    }

    @Test
    void apiKeyColumnIsAddedAndKeyRoundTrips() throws Exception {
        String path = dbPath("key.db");
        createLegacyDatabase(path);

        DatabaseService db = new DatabaseService(path);
        try {
            AppSettings settings = db.loadSettings();
            assertNull(settings.getAnthropicApiKey());
            settings.setAnthropicApiKey("  test-key-not-a-real-key  ");
            db.saveSettings(settings);
        } finally {
            db.close();
        }

        DatabaseService reopened = new DatabaseService(path);
        try {
            AppSettings settings = reopened.loadSettings();
            assertEquals("test-key-not-a-real-key", settings.getAnthropicApiKey());
            settings.setAnthropicApiKey(null);
            reopened.saveSettings(settings);
            assertNull(reopened.loadSettings().getAnthropicApiKey());
        } finally {
            reopened.close();
        }
    }
}
