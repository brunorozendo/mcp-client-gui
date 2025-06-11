package com.brunorozendo.mcpclientgui.controller;

import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.brunorozendo.mcpclientgui.service.DatabaseService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Controller for the Settings dialog.
 */
public class SettingsController {

    @FXML
    private TextField llmModelField;
    
    @FXML
    private TextField mcpConfigPathField;
    
    @FXML
    private Button browseMcpConfigButton;
    
    @FXML
    private TextField ollamaBaseUrlField;
    
    @FXML
    private Button saveButton;
    
    @FXML
    private Button cancelButton;

    private AppSettings settings;
    private boolean saved = false;
    private Stage dialogStage;
    private DatabaseService databaseService = DatabaseService.getInstance();

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setSettings(AppSettings settings) {
        this.settings = settings;
        updateFields();
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void initialize() {
        // Initialize default values
        ollamaBaseUrlField.setText("http://localhost:11434");
        llmModelField.setText("llama3.2");
    }

    private void updateFields() {
        if (settings != null) {
            llmModelField.setText(settings.getLlmModel() != null ? settings.getLlmModel() : "");
            mcpConfigPathField.setText(settings.getMcpConfigFile() != null ? settings.getMcpConfigFile().getAbsolutePath() : "");
            ollamaBaseUrlField.setText(settings.getOllamaBaseUrl() != null ? settings.getOllamaBaseUrl() : "http://localhost:11434");
        }
    }

    @FXML
    private void handleBrowseMcpConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select MCP Configuration File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        // Set initial directory to user home if no file is currently selected
        if (settings.getMcpConfigFile() != null && settings.getMcpConfigFile().getParentFile().exists()) {
            fileChooser.setInitialDirectory(settings.getMcpConfigFile().getParentFile());
        } else {
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }

        File selectedFile = fileChooser.showOpenDialog(dialogStage);
        if (selectedFile != null) {
            mcpConfigPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleSave() {
        if (validateInput()) {
            // Update settings
            settings.setLlmModel(llmModelField.getText().trim());
            settings.setOllamaBaseUrl(ollamaBaseUrlField.getText().trim());
            
            String mcpConfigPath = mcpConfigPathField.getText().trim();
            if (!mcpConfigPath.isEmpty()) {
                settings.setMcpConfigFile(new File(mcpConfigPath));
            }
            
            // Save settings to database
            try {
                databaseService.saveSettings(settings);
                saved = true;
                dialogStage.close();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Save Error");
                alert.setHeaderText("Failed to save settings");
                alert.setContentText("An error occurred while saving settings: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean validateInput() {
        StringBuilder errors = new StringBuilder();

        if (llmModelField.getText().trim().isEmpty()) {
            errors.append("- LLM Model is required\n");
        }

        if (mcpConfigPathField.getText().trim().isEmpty()) {
            errors.append("- MCP Config file path is required\n");
        } else {
            File mcpFile = new File(mcpConfigPathField.getText().trim());
            if (!mcpFile.exists()) {
                errors.append("- MCP Config file does not exist\n");
            }
        }

        if (ollamaBaseUrlField.getText().trim().isEmpty()) {
            errors.append("- Ollama Base URL is required\n");
        }

        if (errors.length() > 0) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Please fix the following errors:");
            alert.setContentText(errors.toString());
            alert.showAndWait();
            return false;
        }

        return true;
    }
}
