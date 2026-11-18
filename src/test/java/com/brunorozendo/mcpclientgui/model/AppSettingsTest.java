package com.brunorozendo.mcpclientgui.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void validWithAModelAndNoMcpConfig() {
        AppSettings settings = new AppSettings();

        assertNull(settings.getMcpConfigFile());
        assertTrue(settings.isValid());
        assertFalse(settings.hasExistingMcpConfigFile());
    }

    @Test
    void validWithAMissingMcpConfigFile() {
        AppSettings settings = new AppSettings();
        settings.setMcpConfigFile(tempDir.resolve("missing.json").toFile());

        assertTrue(settings.isValid());
        assertFalse(settings.hasExistingMcpConfigFile());
    }

    @Test
    void reportsAnExistingMcpConfigFile() throws Exception {
        File config = Files.writeString(tempDir.resolve("mcp.json"), "{\"mcpServers\":{}}").toFile();
        AppSettings settings = new AppSettings();
        settings.setMcpConfigFile(config);

        assertTrue(settings.hasExistingMcpConfigFile());
    }

    @Test
    void invalidWithoutADefaultModel() {
        AppSettings settings = new AppSettings();
        settings.setDefaultLlmModelName(null);

        assertFalse(settings.isValid());
    }
}
