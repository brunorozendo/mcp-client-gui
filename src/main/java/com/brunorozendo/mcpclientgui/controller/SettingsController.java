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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the Settings dialog.
 * 
 * Manages application settings including:
 * - Language Learning Model (LLM) configuration
 * - MCP configuration file path
 * - Ollama API base URL
 */
public class SettingsController {
    
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    
    // UI Constants
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_INDICATOR = "Yes";
    private static final String MODEL_EXISTS_WARNING = "Model '%s' already exists!";
    private static final int BUTTON_SPACING = 5;
    
    // FXML UI Components
    @FXML private TextField newModelField;
    @FXML private Button addModelButton;
    @FXML private TableView<ModelEntry> modelsTable;
    @FXML private TableColumn<ModelEntry, String> modelNameColumn;
    @FXML private TableColumn<ModelEntry, String> defaultColumn;
    @FXML private TableColumn<ModelEntry, Void> actionsColumn;
    @FXML private TextField mcpConfigPathField;
    @FXML private Button browseMcpConfigButton;
    @FXML private TextField ollamaBaseUrlField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    // State management
    private AppSettings settings;
    private boolean saved = false;
    private Stage dialogStage;
    private final DatabaseService databaseService = DatabaseService.getInstance();
    private final ObservableList<ModelEntry> modelsList = FXCollections.observableArrayList();

    /**
     * Sets the dialog stage for this settings window.
     * 
     * @param dialogStage The stage containing this settings dialog
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Sets the application settings to be edited.
     * 
     * @param settings The current application settings
     */
    public void setSettings(AppSettings settings) {
        this.settings = settings;
        updateFields();
    }

    /**
     * Checks if the settings were saved.
     * 
     * @return True if the user saved the settings, false if cancelled
     */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Initializes the controller after FXML loading.
     */
    @FXML
    private void initialize() {
        setDefaultValues();
        setupModelsTable();
        setupEventHandlers();
    }
    
    /**
     * Sets default values for input fields.
     */
    private void setDefaultValues() {
        ollamaBaseUrlField.setText(DEFAULT_OLLAMA_URL);
    }
    
    /**
     * Sets up the models table with columns and cell factories.
     */
    private void setupModelsTable() {
        // Configure model name column
        modelNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        
        // Configure default indicator column
        defaultColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().isDefault() ? DEFAULT_INDICATOR : "")
        );
        
        // Configure actions column
        actionsColumn.setCellFactory(column -> new ModelActionsCell());
        
        // Set the table items
        modelsTable.setItems(modelsList);
    }
    
    /**
     * Sets up event handlers for UI components.
     */
    private void setupEventHandlers() {
        // Enable add button only when text is entered
        addModelButton.disableProperty().bind(newModelField.textProperty().isEmpty());
        
        // Add model on Enter key
        newModelField.setOnAction(e -> handleAddModel());
    }

    /**
     * Updates all fields with values from the current settings.
     */
    private void updateFields() {
        if (settings == null) {
            return;
        }
        
        populateModelsTable();
        updateMcpConfigPath();
        updateOllamaUrl();
    }
    
    /**
     * Populates the models table with current LLM models.
     */
    private void populateModelsTable() {
        modelsList.clear();
        for (AppSettings.LlmModel model : settings.getLlmModels()) {
            modelsList.add(new ModelEntry(model.getName(), model.isDefault()));
        }
    }
    
    /**
     * Updates the MCP config path field.
     */
    private void updateMcpConfigPath() {
        if (settings.getMcpConfigFile() != null) {
            mcpConfigPathField.setText(settings.getMcpConfigFile().getAbsolutePath());
        } else {
            mcpConfigPathField.setText("");
        }
    }
    
    /**
     * Updates the Ollama URL field.
     */
    private void updateOllamaUrl() {
        String url = settings.getOllamaBaseUrl();
        ollamaBaseUrlField.setText(url != null ? url : DEFAULT_OLLAMA_URL);
    }

    /**
     * Handles adding a new model.
     */
    @FXML
    private void handleAddModel() {
        String modelName = newModelField.getText().trim();
        if (modelName.isEmpty()) {
            return;
        }
        
        if (isModelNameDuplicate(modelName)) {
            showDuplicateModelWarning(modelName);
            return;
        }
        
        addNewModel(modelName);
    }
    
    /**
     * Checks if a model name already exists.
     */
    private boolean isModelNameDuplicate(String modelName) {
        return modelsList.stream()
            .anyMatch(m -> m.getName().equalsIgnoreCase(modelName));
    }
    
    /**
     * Shows a warning dialog for duplicate model names.
     */
    private void showDuplicateModelWarning(String modelName) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Duplicate Model");
        alert.setHeaderText(null);
        alert.setContentText(String.format(MODEL_EXISTS_WARNING, modelName));
        alert.showAndWait();
    }
    
    /**
     * Adds a new model to the list.
     */
    private void addNewModel(String modelName) {
        boolean isFirstModel = modelsList.isEmpty();
        ModelEntry newModel = new ModelEntry(modelName, isFirstModel);
        modelsList.add(newModel);
        
        newModelField.clear();
        newModelField.requestFocus();
        
        logger.debug("Added new model: {}", modelName);
    }

    /**
     * Sets a model as the default.
     */
    private void handleSetDefault(ModelEntry model) {
        // Unset all defaults
        modelsList.forEach(m -> m.setDefault(false));
        
        // Set new default
        model.setDefault(true);
        modelsTable.refresh();
        
        logger.debug("Set default model: {}", model.getName());
    }

    /**
     * Handles removing a model from the list.
     */
    private void handleRemoveModel(ModelEntry model) {
        if (!canRemoveModel(model)) {
            return;
        }

        Alert confirmation = showRemoveConfirmation(model);
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                modelsList.remove(model);
                logger.debug("Removed model: {}", model.getName());
            }
        });
    }
    
    /**
     * Checks if a model can be removed.
     */
    private boolean canRemoveModel(ModelEntry model) {
        return modelsList.size() > 1 && !model.isDefault();
    }
    
    /**
     * Shows a confirmation dialog for removing a model.
     */
    private Alert showRemoveConfirmation(ModelEntry model) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Remove Model");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Are you sure you want to remove '" + model.getName() + "'?");
        return confirmation;
    }

    /**
     * Opens a file chooser to browse for the MCP configuration file.
     */
    @FXML
    private void handleBrowseMcpConfig() {
        FileChooser fileChooser = createMcpFileChooser();
        
        File selectedFile = fileChooser.showOpenDialog(dialogStage);
        if (selectedFile != null) {
            mcpConfigPathField.setText(selectedFile.getAbsolutePath());
            logger.debug("Selected MCP config file: {}", selectedFile.getAbsolutePath());
        }
    }
    
    /**
     * Creates a file chooser configured for MCP config files.
     */
    private FileChooser createMcpFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select MCP Configuration File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        // Set initial directory
        File initialDir = determineInitialDirectory();
        fileChooser.setInitialDirectory(initialDir);
        
        return fileChooser;
    }
    
    /**
     * Determines the initial directory for the file chooser.
     */
    private File determineInitialDirectory() {
        if (settings.getMcpConfigFile() != null) {
            File parentDir = settings.getMcpConfigFile().getParentFile();
            if (parentDir != null && parentDir.exists()) {
                return parentDir;
            }
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * Handles saving the settings.
     */
    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }
        
        updateSettingsFromUI();
        
        try {
            databaseService.saveSettings(settings);
            saved = true;
            dialogStage.close();
            logger.info("Settings saved successfully");
        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            showSaveError(e);
        }
    }
    
    /**
     * Updates the settings object with values from the UI.
     */
    private void updateSettingsFromUI() {
        // Update models
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
        
        // Update other settings
        settings.setOllamaBaseUrl(ollamaBaseUrlField.getText().trim());
        
        String mcpConfigPath = mcpConfigPathField.getText().trim();
        if (!mcpConfigPath.isEmpty()) {
            settings.setMcpConfigFile(new File(mcpConfigPath));
        }
    }
    
    /**
     * Shows an error dialog when saving fails.
     */
    private void showSaveError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Save Error");
        alert.setHeaderText("Failed to save settings");
        alert.setContentText("An error occurred while saving settings: " + e.getMessage());
        alert.showAndWait();
    }

    /**
     * Handles cancelling the settings dialog.
     */
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    /**
     * Validates all input fields.
     */
    private boolean validateInput() {
        List<String> errors = new ArrayList<>();
        
        validateModels(errors);
        validateMcpConfig(errors);
        validateOllamaUrl(errors);
        
        if (!errors.isEmpty()) {
            showValidationErrors(errors);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates model configuration.
     */
    private void validateModels(List<String> errors) {
        if (modelsList.isEmpty()) {
            errors.add("At least one LLM model is required");
        } else {
            boolean hasDefault = modelsList.stream().anyMatch(ModelEntry::isDefault);
            if (!hasDefault) {
                errors.add("One model must be set as default");
            }
        }
    }
    
    /**
     * Validates MCP configuration file.
     */
    private void validateMcpConfig(List<String> errors) {
        String mcpPath = mcpConfigPathField.getText().trim();
        if (mcpPath.isEmpty()) {
            errors.add("MCP Config file path is required");
        } else {
            File mcpFile = new File(mcpPath);
            if (!mcpFile.exists()) {
                errors.add("MCP Config file does not exist");
            }
        }
    }
    
    /**
     * Validates Ollama URL.
     */
    private void validateOllamaUrl(List<String> errors) {
        if (ollamaBaseUrlField.getText().trim().isEmpty()) {
            errors.add("Ollama Base URL is required");
        }
    }
    
    /**
     * Shows validation errors to the user.
     */
    private void showValidationErrors(List<String> errors) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Please fix the following errors:");
        alert.setContentText(String.join("\n- ", errors));
        alert.showAndWait();
    }
    
    /**
     * Custom table cell for model actions (Set Default and Remove buttons).
     */
    private class ModelActionsCell extends TableCell<ModelEntry, Void> {
        private final Button setDefaultButton = new Button("Set Default");
        private final Button removeButton = new Button("Remove");
        private final HBox buttonsBox = new HBox(BUTTON_SPACING);
        
        public ModelActionsCell() {
            setupButtons();
        }
        
        private void setupButtons() {
            setDefaultButton.getStyleClass().add("small-button");
            removeButton.getStyleClass().addAll("small-button", "danger-button");
            
            buttonsBox.setAlignment(Pos.CENTER);
            buttonsBox.getChildren().addAll(setDefaultButton, removeButton);
        }
        
        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty) {
                setGraphic(null);
            } else {
                ModelEntry model = getTableView().getItems().get(getIndex());
                configureButtons(model);
                setGraphic(buttonsBox);
            }
        }
        
        private void configureButtons(ModelEntry model) {
            // Configure Set Default button
            setDefaultButton.setOnAction(e -> handleSetDefault(model));
            setDefaultButton.setDisable(model.isDefault());
            
            // Configure Remove button
            removeButton.setOnAction(e -> handleRemoveModel(model));
            removeButton.setDisable(!canRemoveModel(model));
        }
    }
    
    /**
     * Model entry for the settings table.
     * Represents an LLM model configuration in the UI.
     */
    public static class ModelEntry {
        private String name;
        private boolean isDefault;
        
        /**
         * Creates a new model entry.
         * 
         * @param name The model name
         * @param isDefault Whether this is the default model
         */
        public ModelEntry(String name, boolean isDefault) {
            this.name = name;
            this.isDefault = isDefault;
        }
        
        /**
         * Gets the model name.
         * 
         * @return The model name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Sets the model name.
         * 
         * @param name The model name to set
         */
        public void setName(String name) {
            this.name = name;
        }
        
        /**
         * Checks if this is the default model.
         * 
         * @return True if this is the default model
         */
        public boolean isDefault() {
            return isDefault;
        }
        
        /**
         * Sets whether this is the default model.
         * 
         * @param isDefault True to make this the default model
         */
        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }
        
        @Override
        public String toString() {
            return name + (isDefault ? " (Default)" : "");
        }
    }
}
