package com.brunorozendo.mcpclientgui.controller;

import com.brunorozendo.mcpclientgui.model.AppSettings;
import com.brunorozendo.mcpclientgui.service.DatabaseService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the Settings dialog.
 */
public class SettingsController {

    @FXML
    private TextField newModelField;
    
    @FXML
    private Button addModelButton;
    
    @FXML
    private TableView<ModelEntry> modelsTable;
    
    @FXML
    private TableColumn<ModelEntry, String> modelNameColumn;
    
    @FXML
    private TableColumn<ModelEntry, String> defaultColumn;
    
    @FXML
    private TableColumn<ModelEntry, Void> actionsColumn;
    
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
    private ObservableList<ModelEntry> modelsList = FXCollections.observableArrayList();

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
        
        // Setup table columns
        modelNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        defaultColumn.setCellValueFactory(cellData -> {
            return new SimpleStringProperty(cellData.getValue().isDefault() ? "Yes" : "");
        });
        
        // Setup actions column
        actionsColumn.setCellFactory(column -> {
            return new TableCell<ModelEntry, Void>() {
                private final Button setDefaultButton = new Button("Set Default");
                private final Button removeButton = new Button("Remove");
                
                {
                    setDefaultButton.getStyleClass().add("small-button");
                    removeButton.getStyleClass().add("small-button");
                    removeButton.getStyleClass().add("danger-button");
                }
                
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        HBox buttons = new HBox(5);
                        buttons.setAlignment(Pos.CENTER);
                        
                        ModelEntry model = getTableView().getItems().get(getIndex());
                        
                        setDefaultButton.setOnAction(e -> handleSetDefault(model));
                        removeButton.setOnAction(e -> handleRemoveModel(model));
                        
                        // Disable "Set Default" if already default
                        setDefaultButton.setDisable(model.isDefault());
                        
                        // Disable remove if it's the only model or is default
                        removeButton.setDisable(modelsList.size() <= 1 || model.isDefault());
                        
                        buttons.getChildren().addAll(setDefaultButton, removeButton);
                        setGraphic(buttons);
                    }
                }
            };
        });
        
        modelsTable.setItems(modelsList);
        
        // Enable add button only when text is entered
        addModelButton.disableProperty().bind(newModelField.textProperty().isEmpty());
        
        // Add model on Enter key
        newModelField.setOnAction(e -> handleAddModel());
    }

    private void updateFields() {
        if (settings != null) {
            // Populate models table
            modelsList.clear();
            for (AppSettings.LlmModel model : settings.getLlmModels()) {
                modelsList.add(new ModelEntry(model.getName(), model.isDefault()));
            }
            
            mcpConfigPathField.setText(settings.getMcpConfigFile() != null ? settings.getMcpConfigFile().getAbsolutePath() : "");
            ollamaBaseUrlField.setText(settings.getOllamaBaseUrl() != null ? settings.getOllamaBaseUrl() : "http://localhost:11434");
        }
    }

    @FXML
    private void handleAddModel() {
        String modelName = newModelField.getText().trim();
        if (!modelName.isEmpty()) {
            // Check if model already exists
            boolean exists = modelsList.stream().anyMatch(m -> m.getName().equalsIgnoreCase(modelName));
            if (exists) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Duplicate Model");
                alert.setHeaderText(null);
                alert.setContentText("Model '" + modelName + "' already exists!");
                alert.showAndWait();
                return;
            }
            
            // Add new model
            boolean isFirstModel = modelsList.isEmpty();
            ModelEntry newModel = new ModelEntry(modelName, isFirstModel);
            modelsList.add(newModel);
            
            newModelField.clear();
            newModelField.requestFocus();
        }
    }

    private void handleSetDefault(ModelEntry model) {
        // Unset all defaults
        for (ModelEntry m : modelsList) {
            m.setDefault(false);
        }
        // Set new default
        model.setDefault(true);
        modelsTable.refresh();
    }

    private void handleRemoveModel(ModelEntry model) {
        if (modelsList.size() > 1 && !model.isDefault()) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Remove Model");
            confirmation.setHeaderText(null);
            confirmation.setContentText("Are you sure you want to remove '" + model.getName() + "'?");
            
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    modelsList.remove(model);
                }
            });
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
            // Update settings with models
            List<AppSettings.LlmModel> models = new ArrayList<>();
            String defaultModelName = null;
            
            for (ModelEntry entry : modelsList) {
                AppSettings.LlmModel model = new AppSettings.LlmModel(entry.getName(), entry.isDefault());
                models.add(model);
                if (model.isDefault()) {
                    defaultModelName = model.getName();
                }
            }
            
            settings.setLlmModels(models);
            settings.setDefaultLlmModelName(defaultModelName);
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

        if (modelsList.isEmpty()) {
            errors.append("- At least one LLM model is required\n");
        } else {
            // Check if there's a default model
            boolean hasDefault = modelsList.stream().anyMatch(ModelEntry::isDefault);
            if (!hasDefault) {
                errors.append("- One model must be set as default\n");
            }
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
    
    /**
     * Model entry for the table
     */
    public static class ModelEntry {
        private String name;
        private boolean isDefault;
        
        public ModelEntry(String name, boolean isDefault) {
            this.name = name;
            this.isDefault = isDefault;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public boolean isDefault() {
            return isDefault;
        }
        
        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }
    }
}
