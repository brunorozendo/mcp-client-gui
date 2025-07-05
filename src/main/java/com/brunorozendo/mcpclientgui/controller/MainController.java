package com.brunorozendo.mcpclientgui.controller;

import com.brunorozendo.mcpclientgui.core.chat.ChatController;
import com.brunorozendo.mcpclientgui.control.SystemPromptBuilder;
import com.brunorozendo.mcpclientgui.core.ChatManager;
import com.brunorozendo.mcpclientgui.core.ConnectionManager;
import com.brunorozendo.mcpclientgui.model.*;
import com.brunorozendo.mcpclientgui.service.DatabaseService;
import com.brunorozendo.mcpclientgui.ui.components.ChatListCellFactory;
import com.brunorozendo.mcpclientgui.ui.components.MessageRenderer;
import com.brunorozendo.mcpclientgui.ui.handlers.DialogHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Main controller for the MCP Client GUI application.
 * Manages the main user interface and coordinates between various components.
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // UI Constants
    private static final String APP_TITLE = "MCP Assistant";
    private static final String STATUS_READY = "Ready";
    private static final String STATUS_NOT_CONFIGURED = "Not configured. Please go to Settings to configure MCP and LLM.";
    private static final String MODEL_PROMPT_TEXT = "Select a model";

    // FXML UI Components
    @FXML private ListView<Chat> chatListView;
    @FXML private Label titleLabel;
    @FXML private ListView<Message> messageListView;
    @FXML private TextArea messageInput;
    @FXML private Button sendButton;
    @FXML private Button newChatButton;
    @FXML private Button settingsButton;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> modelComboBox;

    // Loading animation
    private ImageView loadingImageView;

    // Core components
    private ChatManager chatManager;
    private ConnectionManager connectionManager;
    private DatabaseService databaseService;
    private AppSettings appSettings;

    // UI components
    private MessageRenderer messageRenderer;
    private ChatListCellFactory chatListCellFactory;

    // Chat controller
    private ChatController currentChatController;
    private String currentChatControllerModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeComponents();
        setupUI();
        loadApplicationData();
        attemptAutoInitialization();
    }

    /**
     * Initializes all core components.
     */
    private void initializeComponents() {
        databaseService = DatabaseService.getInstance();
        appSettings = databaseService.loadSettings();
        chatManager = new ChatManager();
        messageRenderer = new MessageRenderer();
        chatListCellFactory = new ChatListCellFactory();

        // Setup chat list cell factory callbacks
        chatListCellFactory.setOnRename(this::handleChatRename);
        chatListCellFactory.setOnDelete(this::handleChatDelete);
    }

    /**
     * Sets up all UI components.
     */
    private void setupUI() {
        setupChatListView();
        setupMessageListView();
        setupMessageInput();
        setupModelSelection();
        setupLoadingAnimation();
        updateTitle(null);
    }

    /**
     * Sets up the chat list view.
     */
    private void setupChatListView() {
        chatListView.setItems(chatManager.getChats());
        chatListView.setCellFactory(chatListCellFactory);
        chatListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldChat, newChat) -> {
                if (newChat != null) {
                    selectChat(newChat);
                }
            }
        );
    }

    /**
     * Sets up the message list view.
     */
    private void setupMessageListView() {
        messageListView.setCellFactory(listView -> messageRenderer.createMessageCell());
        messageListView.setPlaceholder(new Label("Select a chat to start messaging"));
    }

    /**
     * Sets up the message input area.
     */
    private void setupMessageInput() {
        messageInput.setWrapText(true);
        messageInput.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
        setInputEnabled(false);
    }

    /**
     * Sets up the model selection combo box.
     */
    private void setupModelSelection() {
        modelComboBox.setPromptText(MODEL_PROMPT_TEXT);
        modelComboBox.setDisable(true);
        modelComboBox.setOnAction(e -> handleModelChange());
        updateAvailableModels();
    }

    /**
     * Sets up the loading animation.
     */
    private void setupLoadingAnimation() {
        try {
            // Load the loading GIF
            Image loadingImage = new Image(getClass().getResourceAsStream("/imgs/loading.gif"));
            loadingImageView = new ImageView(loadingImage);
            loadingImageView.setFitHeight(16);
            loadingImageView.setFitWidth(16);
            loadingImageView.setVisible(false); // Initially hidden

            // Add the loading image to the status bar
            if (statusLabel != null && statusLabel.getParent() instanceof HBox) {
                HBox statusBar = (HBox) statusLabel.getParent();
                statusBar.getChildren().add(0, loadingImageView);
                statusBar.setSpacing(8); // Add some spacing between the image and text
            }
        } catch (Exception e) {
            logger.error("Failed to load loading animation", e);
        }
    }

    /**
     * Loads application data from storage.
     */
    private void loadApplicationData() {
        String defaultModel = appSettings.getDefaultLlmModelName();
        chatManager.loadAllChats(defaultModel);

        // Select first chat if available
        if (!chatManager.getChats().isEmpty()) {
            chatListView.getSelectionModel().selectFirst();
        }
    }

    /**
     * Attempts to automatically initialize connections if configured.
     */
    private void attemptAutoInitialization() {
        if (appSettings.isValid()) {
            initializeConnections();
        } else {
            updateStatus(STATUS_NOT_CONFIGURED);
        }
    }

    /**
     * Initializes MCP and AI connections.
     */
    private void initializeConnections() {
        updateStatus("Initializing MCP connections...");

        connectionManager = new ConnectionManager(appSettings);
        connectionManager.initialize().thenAccept(status -> {
            Platform.runLater(() -> {
                if (status.isSuccess()) {
                    onConnectionSuccess(status);
                } else {
                    onConnectionError(status.getMessage());
                }
            });
        });
    }

    /**
     * Handles successful connection initialization.
     */
    private void onConnectionSuccess(ConnectionManager.ConnectionStatus status) {
        setInputEnabled(true);
        updateStatus(status.getMessage());
// Fetch available models from Ollama
        fetchAndUpdateAvailableModels();
        // Update chat controller if a chat is selected
        Chat currentChat = chatManager.getCurrentChat();
        if (currentChat != null && currentChat.getLlmModelName() != null) {
            createChatController(currentChat.getLlmModelName());
        }
    }

    /**
     * Handles connection initialization errors.
     */
    private void onConnectionError(String errorMessage) {
        updateStatus("Error: " + errorMessage);
        DialogHelper.showError("Initialization Error", 
            "Failed to initialize MCP and AI: " + errorMessage);
    }

    // ===== Action Handlers =====

    @FXML
    private void handleNewChat() {
        String defaultModel = appSettings.getDefaultLlmModelName();
        if (defaultModel == null || defaultModel.isEmpty()) {
            DialogHelper.showNotConfiguredWarning();
            return;
        }

        Chat newChat = chatManager.createNewChat(defaultModel);
        selectChat(newChat);
    }

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty() || chatManager.getCurrentChat() == null) {
            return;
        }

        Chat currentChat = chatManager.getCurrentChat();
        Message userMessage = chatManager.createMessage(currentChat, text, true);

        messageInput.clear();
        scrollToBottom();

        if (connectionManager != null && connectionManager.isInitialized()) {
            processWithAI(text);
        }
    }

    @FXML
    private void handleSettings() {
        try {
            openSettingsDialog();
        } catch (IOException e) {
            logger.error("Error loading settings dialog", e);
            DialogHelper.showError("Failed to load settings dialog: " + e.getMessage());
        }
    }

    /**
     * Handles chat rename requests.
     */
    private void handleChatRename(Chat chat) {
        DialogHelper.showRenameDialog(chat).ifPresent(newName -> {
            chatManager.renameChat(chat, newName);
            chatListView.refresh();
            if (chat == chatManager.getCurrentChat()) {
                updateTitle(chat);
            }
        });
    }

    /**
     * Handles chat delete requests.
     */
    private void handleChatDelete(Chat chat) {
        if (DialogHelper.showDeleteConfirmation(chat)) {
            boolean wasCurrentChat = chat == chatManager.getCurrentChat();
            chatManager.deleteChat(chat);

            if (wasCurrentChat) {
                clearCurrentChatView();
                // Select another chat if available
                if (!chatManager.getChats().isEmpty()) {
                    chatListView.getSelectionModel().selectFirst();
                }
            }
        }
    }

    /**
     * Handles model selection changes.
     */
    private void handleModelChange() {
        Chat currentChat = chatManager.getCurrentChat();
        if (currentChat == null || modelComboBox.getValue() == null) {
            return;
        }

        String newModel = modelComboBox.getValue();
        String oldModel = currentChat.getLlmModelName();

        if (!newModel.equals(oldModel)) {
            chatManager.updateChatModel(currentChat, newModel);
            updateTitle(currentChat);
            chatListView.refresh();

            if (connectionManager != null && connectionManager.isInitialized()) {
                createChatController(newModel);
                updateStatus("Switched to model: " + newModel);
            }
        }
    }

    /**
     * Handles key press events in the message input.
     */
    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
            event.consume();
            handleSendMessage();
        }
    }

    // ===== UI Helper Methods =====

    /**
     * Selects and loads a chat.
     */
    private void selectChat(Chat chat) {
        chatManager.setCurrentChat(chat);
        updateTitle(chat);
        messageListView.setItems(chat.getMessages());

        modelComboBox.setDisable(false);
        modelComboBox.setValue(chat.getLlmModelName());

        scrollToBottom();

        // Update chat controller if initialized
        if (connectionManager != null && connectionManager.isInitialized()) {
            createChatController(chat.getLlmModelName());
        }
    }

    /**
     * Clears the current chat view.
     */
    private void clearCurrentChatView() {
        chatManager.setCurrentChat(null);
        messageListView.getItems().clear();
        updateTitle(null);
        modelComboBox.setValue(null);
        modelComboBox.setDisable(true);
    }

    /**
     * Updates the title based on the current chat.
     */
    private void updateTitle(Chat chat) {
        if (chat != null) {
            titleLabel.setText(chat.getName());
        }else{
            titleLabel.setText(APP_TITLE);
        }

    }

    /**
     * Updates the status label.
     */
    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);

            // Show/hide loading animation based on status
            if (loadingImageView != null) {
                boolean isThinking = status != null && status.contains("Thinking...");
                loadingImageView.setVisible(isThinking);
            }
        }
    }

    /**
     * Updates the available models in the combo box.
     */
    private void updateAvailableModels() {
        String currentSelection = modelComboBox.getValue();

        modelComboBox.getItems().clear();
        for (AppSettings.LlmModel model : appSettings.getLlmModels()) {
            modelComboBox.getItems().add(model.getName());
        }

        // Restore selection if still available
        if (currentSelection != null && modelComboBox.getItems().contains(currentSelection)) {
            modelComboBox.setValue(currentSelection);
        }
    }

    /**
     * Enables or disables message input controls.
     */
    private void setInputEnabled(boolean enabled) {
        messageInput.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    /**
     * Scrolls the message list to the bottom.
     */
    private void scrollToBottom() {
        Platform.runLater(() -> {
            Chat currentChat = chatManager.getCurrentChat();
            if (currentChat != null && currentChat.hasMessages()) {
                messageListView.scrollTo(currentChat.getMessageCount() - 1);
            }
        });
    }

    /**
     * Fetches available models from Ollama and updates the application settings.
     */
    private void fetchAndUpdateAvailableModels() {
        if (connectionManager == null || !connectionManager.isInitialized()) {
            logger.warn("Cannot fetch models: connection manager not initialized");
            return;
        }

        // Run in background thread to avoid blocking UI
        Platform.runLater(() -> updateStatus("Fetching available models..."));

        new Thread(() -> {
            try {
                OllamaApi.TagsResponse tagsResponse = connectionManager.getOllamaApiClient().getAvailableModels();

                if (tagsResponse.models() != null && !tagsResponse.models().isEmpty()) {
                    // Convert Ollama models to LlmModel objects
                    List<AppSettings.LlmModel> llmModels = new ArrayList<>();
                    String currentDefaultModel = appSettings.getDefaultLlmModelName();
                    boolean foundCurrentDefault = false;

                    for (OllamaApi.ModelInfo modelInfo : tagsResponse.models()) {
                        String modelName = modelInfo.name();
                        boolean isDefault = modelName.equals(currentDefaultModel);
                        if (isDefault) {
                            foundCurrentDefault = true;
                        }
                        llmModels.add(new AppSettings.LlmModel(modelName, isDefault));
                    }

                    // If no current default was found and we have models, make the first one default
                    if (!foundCurrentDefault && !llmModels.isEmpty()) {
                        llmModels.get(0).setDefault(true);
                        appSettings.setDefaultLlmModelName(llmModels.get(0).getName());
                    }

                    // Update settings with new models
                    appSettings.setLlmModels(llmModels);
                    databaseService.saveSettings(appSettings);

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        updateAvailableModels();
                        updateStatus("Loaded " + llmModels.size() + " available models");
                        logger.info("Successfully loaded {} models from Ollama", llmModels.size());
                    });
                } else {
                    Platform.runLater(() -> {
                        updateStatus("No models found on Ollama server");
                        logger.warn("No models found on Ollama server");
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("Failed to fetch models from Ollama");
                    logger.error("Failed to fetch available models from Ollama", e);
                });
            }
        }).start();
    }

    // ===== AI Processing =====

    /**
     * Processes a user message with the AI.
     */
    private void processWithAI(String userMessage) {
        ensureValidChatController();

        if (currentChatController != null) {
            setInputEnabled(false);

            currentChatController.processUserMessage(userMessage).whenComplete((result, error) -> {
                Platform.runLater(() -> {
                    setInputEnabled(true);
                    messageInput.requestFocus();
                });
            });
        }
    }

    /**
     * Ensures the chat controller is valid for the current chat.
     */
    private void ensureValidChatController() {
        Chat currentChat = chatManager.getCurrentChat();
        if (currentChat == null || currentChat.getLlmModelName() == null) {
            return;
        }

        String modelName = currentChat.getLlmModelName();
        if (currentChatController == null || !modelName.equals(currentChatControllerModel)) {
            createChatController(modelName);
        }
    }

    /**
     * Creates a new chat controller for the specified model.
     */
    private void createChatController(String modelName) {
        if (connectionManager == null || !connectionManager.isInitialized()) {
            return;
        }

        try {
            String ollamaModel = extractOllamaModelName(modelName);
            String systemPrompt = SystemPromptBuilder.build(
                connectionManager.getMcpTools(),
                connectionManager.getMcpResources(),
                connectionManager.getMcpPrompts()
            );

            currentChatController = new ChatController.Builder()
                .modelName(ollamaModel)
                .ollamaClient(connectionManager.getOllamaApiClient())
                .mcpManager(connectionManager.getMcpConnectionManager())
                .systemPrompt(systemPrompt)
                .tools(connectionManager.getOllamaTools())
                .onMessageReceived(this::onAIMessageReceived)
                .onStatusUpdate(this::onAIThinking)
                .onProcessingFinished(this::onAIThinkingFinished)
                .build();

            currentChatControllerModel = modelName;

            logger.info("Created chat controller for model: {}", modelName);
        } catch (Exception e) {
            logger.error("Error creating chat controller", e);
            DialogHelper.showError("Model Error", 
                "Failed to initialize model " + modelName + ": " + e.getMessage());
        }
    }


    /**
     * Handles AI message reception.
     */
    private void onAIMessageReceived(Message message) {
        Chat currentChat = chatManager.getCurrentChat();
        if (currentChat != null) {
            chatManager.createMessage(currentChat, message.getContent(), false);
            scrollToBottom();
        }
    }

    /**
     * Updates status when AI is thinking.
     */
    private void onAIThinking(String status) {
        Platform.runLater(() -> updateStatus(status));
    }

    /**
     * Updates status when AI finishes thinking.
     */
    private void onAIThinkingFinished() {
        Platform.runLater(() -> updateStatus(STATUS_READY));
    }

    /**
     * Extracts the Ollama model name from the full model string.
     */
    private String extractOllamaModelName(String fullModelName) {
        final String OLLAMA_PREFIX = "ollama:";
        if (fullModelName.startsWith(OLLAMA_PREFIX)) {
            return fullModelName.substring(OLLAMA_PREFIX.length());
        }
        return fullModelName;
    }

    // ===== Settings Dialog =====

    /**
     * Opens the settings dialog.
     */
    private void openSettingsDialog() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
        VBox settingsRoot = loader.load();

        SettingsController settingsController = loader.getController();
        Stage settingsStage = createSettingsStage(settingsRoot);

        settingsController.setDialogStage(settingsStage);
        settingsController.setSettings(appSettings);

        settingsStage.showAndWait();

        if (settingsController.isSaved()) {
            onSettingsSaved();
        }
    }

    /**
     * Creates the settings dialog stage.
     */
    private Stage createSettingsStage(VBox content) {
        Stage stage = new Stage();
        stage.setTitle("Settings");
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(settingsButton.getScene().getWindow());
        stage.setScene(new Scene(content));
        stage.setResizable(false);
        return stage;
    }

    /**
     * Handles actions after settings are saved.
     */
    private void onSettingsSaved() {
        appSettings = databaseService.loadSettings();
        updateAvailableModels();
        initializeConnections();
    }
}
