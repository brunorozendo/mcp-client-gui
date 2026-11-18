package com.brunorozendo.mcpclientgui.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MCP config path validation. Uses only the static check, so neither JavaFX nor the database is started.
 */
class SettingsControllerValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyPathIsValid() {
        assertNull(SettingsController.mcpConfigPathError(""));
        assertNull(SettingsController.mcpConfigPathError("   "));
        assertNull(SettingsController.mcpConfigPathError(null));
    }

    @Test
    void existingFileIsValid() throws Exception {
        Path config = Files.writeString(tempDir.resolve("mcp.json"), "{}");
        assertNull(SettingsController.mcpConfigPathError(config.toString()));
    }

    @Test
    void missingFileIsInvalid() {
        assertNotNull(SettingsController.mcpConfigPathError(tempDir.resolve("missing.json").toString()));
    }
}
