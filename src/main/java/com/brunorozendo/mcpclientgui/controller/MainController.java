package com.brunorozendo.mcpclientgui.controller;

import com.brunorozendo.mcpclientgui.core.chat.AnthropicChatController;
import com.brunorozendo.mcpclientgui.core.chat.AnthropicConversation;
import com.brunorozendo.mcpclientgui.core.chat.ChatController;
import com.brunorozendo.mcpclientgui.core.chat.ChatSession;
import com.brunorozendo.mcpclientgui.core.chat.ToolExecutor;
import com.brunorozendo.mcpclientgui.control.SystemPromptBuilder;
import com.brunorozendo.mcpclientgui.core.ChatManager;
import com.brunorozendo.mcpclientgui.core.ConnectionManager;
import com.brunorozendo.mcpclientgui.model.*;
import com.brunorozendo.mcpclientgui.service.AnthropicService;
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
import javafx.scene.layout.BorderPane;
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
import java.util.concurrent.CompletionException;

/**
 * Main controller for the MCP Client GUI application.
 * Manages the main user interface and coordinates between various components.
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // UI Constants
    private static final String APP_TITLE = "MCP Assistant";
    private static final String STATUS_READY = "Ready";
    private static final String STATUS_LOADING = "Loading...";
    private static final String STATUS_LOAD_FAILED = "Failed to load application data.";
    private static final String STATUS_NOT_CONFIGURED = "No default model configured. Please go to Settings to add one.";
    private static final String STATUS_NOT_CONNECTED = "Not connected yet. Please wait, or check Settings.";
    private static final String MODEL_PROMPT_TEXT = "Select a model";

    // FXML UI Components
    @FXML private BorderPane rootPane;
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

    // Chat controller (Ollama or Anthropic, chosen by the chat's model name)
    private ChatSession currentChatController;
    private String currentChatControllerModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Phase A: UI setup that needs no data, so the window can show right away
        initializeComponents();
        setupUI();
        rootPane.setDisable(true);
        updateStatus(STATUS_LOADING);

        // Phase B: open the database and load settings off the JavaFX thread
        Thread loader = new Thread(() -> {
            try {
                DatabaseService loadedDatabaseService = DatabaseService.getInstance();
                AppSettings loadedSettings = loadedDatabaseService.loadSettings();
                ChatManager loadedChatManager = new ChatManager();

                // Phase C: data-dependent setup on the JavaFX thread
                Platform.runLater(() -> onDataLoaded(loadedDatabaseService, loadedSettings, loadedChatManager));
            } catch (Throwable e) {
                logger.error("Failed to load application data", e);
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                Platform.runLater(() -> {
                    updateStatus(STATUS_LOAD_FAILED);
                    DialogHelper.showError("Startup error", message);
                });
            }
        }, "app-data-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Completes initialization once the database, settings and chat manager are available.
     * Must run on the JavaFX thread.
     */
    private void onDataLoaded(DatabaseService loadedDatabaseService, AppSettings loadedSettings,
                              ChatManager loadedChatManager) {
        databaseService = loadedDatabaseService;
        appSettings = loadedSettings;
        chatManager = loadedChatManager;

        setupChatListView();
        updateAvailableModels();
        loadApplicationData();
        attemptAutoInitialization();

        rootPane.setDisable(false);
    }

    /**
     * Initializes the UI components that need no data.
     */
    private void initializeComponents() {
        messageRenderer = new MessageRenderer();
        chatListCellFactory = new ChatListCellFactory();

        // Setup chat list cell factory callbacks
        chatListCellFactory.setOnRename(this::handleChatRename);
        chatListCellFactory.setOnDelete(this::handleChatDelete);
    }

    /**
     * Sets up the UI components that need no data.
     */
    private void setupUI() {
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
        // The input is usable as soon as the data is loaded: until the connection is ready,
        // sending keeps the text and explains why (see handleSendMessage).
        setInputEnabled(true);
        if (appSettings.isValid()) {
            initializeConnections();
        } else {
            // Only a missing default model gets here; MCP is optional
            updateStatus(STATUS_NOT_CONFIGURED);
        }
    }

    /**
     * Initializes MCP and AI connections.
     */
    private void initializeConnections() {
        updateStatus("Initializing MCP connections...");

        connectionManager = new ConnectionManager(appSettings);
        connectionManager.initialize().whenComplete((status, error) -> {
            if (error != null) {
                Throwable cause = unwrap(error);
                logger.error("MCP and AI initialization failed", cause);
                Platform.runLater(() -> onConnectionError(describe(cause)));
                return;
            }
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
        // Keep the input usable: sending explains that there is no connection, and saving
        // Settings retries the initialization.
        setInputEnabled(true);
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

        if (connectionManager == null || !connectionManager.isInitialized()) {
            // Keep the typed text so it can be sent once the connection is ready
            updateStatus(appSettings.isValid() ? STATUS_NOT_CONNECTED : STATUS_NOT_CONFIGURED);
            return;
        }

        Chat currentChat = chatManager.getCurrentChat();
        Message userMessage = chatManager.createMessage(currentChat, text, true);

        messageInput.clear();
        scrollToBottom();

        processWithAI(text);
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
     * Fetches the available models from Ollama (tags) and Anthropic (Models API) and updates the
     * application settings. Each provider's list replaces only that provider's models, so an
     * unreachable Ollama server or missing Anthropic credentials leave the other list in place, and
     * the default Anthropic model always stays available.
     */
    private void fetchAndUpdateAvailableModels() {
        if (connectionManager == null || !connectionManager.isInitialized()) {
            logger.warn("Cannot fetch models: connection manager not initialized");
            return;
        }

        // Run in background thread to avoid blocking UI
        Platform.runLater(() -> updateStatus("Fetching available models..."));

        ConnectionManager manager = connectionManager;
        new Thread(() -> {
            List<String> ollamaNames = fetchOllamaModelNames(manager);
            List<String> anthropicNames = fetchAnthropicModelNames(manager);

            // Keep the current entries of a provider whose fetch failed or returned nothing
            List<String> merged = new ArrayList<>();
            List<String> currentNames = new ArrayList<>();
            for (AppSettings.LlmModel model : appSettings.getLlmModels()) {
                currentNames.add(model.getName());
            }
            if (ollamaNames.isEmpty()) {
                currentNames.stream().filter(n -> LlmProvider.of(n) == LlmProvider.OLLAMA).forEach(merged::add);
            } else {
                merged.addAll(ollamaNames);
            }
            if (anthropicNames.isEmpty()) {
                currentNames.stream().filter(n -> LlmProvider.of(n) == LlmProvider.ANTHROPIC).forEach(merged::add);
            } else {
                merged.addAll(anthropicNames);
            }
            if (!merged.contains(LlmProvider.DEFAULT_ANTHROPIC_MODEL_NAME)) {
                merged.add(LlmProvider.DEFAULT_ANTHROPIC_MODEL_NAME);
            }

            // Keep the current default if it is still listed, otherwise make the first one default
            String currentDefaultModel = appSettings.getDefaultLlmModelName();
            String defaultModel = merged.contains(currentDefaultModel) ? currentDefaultModel : merged.get(0);
            List<AppSettings.LlmModel> llmModels = new ArrayList<>();
            for (String name : merged) {
                llmModels.add(new AppSettings.LlmModel(name, name.equals(defaultModel)));
            }

            try {
                appSettings.setLlmModels(llmModels);
                appSettings.setDefaultLlmModelName(defaultModel);
                databaseService.saveSettings(appSettings);
            } catch (Exception e) {
                logger.error("Failed to save the fetched model list", e);
            }

            String status = ollamaNames.isEmpty()
                ? "Loaded " + llmModels.size() + " models (could not fetch models from Ollama)"
                : "Loaded " + llmModels.size() + " available models";
            Platform.runLater(() -> {
                updateAvailableModels();
                updateStatus(status);
                logger.info("Loaded {} models ({} from Ollama, {} from Anthropic)",
                            llmModels.size(), ollamaNames.size(), anthropicNames.size());
            });
        }, "model-list-fetch").start();
    }

    /**
     * Fetches the Ollama model tags. Runs on a background thread.
     *
     * @return The tag names, or an empty list if Ollama is unreachable or has no models
     */
    private List<String> fetchOllamaModelNames(ConnectionManager manager) {
        List<String> names = new ArrayList<>();
        try {
            OllamaApi.TagsResponse tagsResponse = manager.getOllamaApiClient().getAvailableModels();
            if (tagsResponse.models() != null) {
                for (OllamaApi.ModelInfo modelInfo : tagsResponse.models()) {
                    names.add(modelInfo.name());
                }
            }
            if (names.isEmpty()) {
                logger.warn("No models found on Ollama server");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch available models from Ollama", e);
        }
        return names;
    }

    /**
     * Fetches the Anthropic models through the SDK's Models API. Runs on a background thread; this is
     * the first use of the Anthropic client, after start-up has finished.
     *
     * @return Prefixed model names, or an empty list if there are no credentials or the call fails
     */
    private List<String> fetchAnthropicModelNames(ConnectionManager manager) {
        AnthropicService service = manager.getAnthropicService();
        if (!service.mayHaveCredentials()) {
            logger.info("No Anthropic credential configured; keeping the default Anthropic model list");
            return List.of();
        }
        try {
            return service.listModelNames();
        } catch (com.anthropic.errors.AnthropicServiceException e) {
            // Status only: the SDK exception carries no credential, but its headers are not logged either.
            logger.warn("Could not list Anthropic models (HTTP {})", e.statusCode());
        } catch (com.anthropic.errors.AnthropicException e) {
            logger.warn("Could not list Anthropic models: {}", e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // Best-effort listing: an unexpected client error must not stop the Ollama list from loading.
            logger.warn("Could not list Anthropic models: {}", e.getClass().getSimpleName());
        }
        return List.of();
    }

    // ===== AI Processing =====

    /**
     * Processes a user message with the AI.
     */
    private void processWithAI(String userMessage) {
        ensureValidChatController();

        if (currentChatController == null) {
            Chat chat = chatManager.getCurrentChat();
            String model = chat != null ? chat.getLlmModelName() : null;
            logger.warn("Message not sent: no chat controller for model {}", model);
            updateStatus("Message not sent: no chat controller for model "
                + (model != null ? model : "(none)") + ".");
            return;
        }

        setInputEnabled(false);

        currentChatController.processUserMessage(userMessage).whenComplete((result, error) -> {
            // The chat controllers report Exceptions in the chat themselves; only a failure that
            // escaped them (an Error) arrives here.
            Throwable cause = error != null ? unwrap(error) : null;
            if (cause != null) {
                logger.error("Message processing failed", cause);
            }
            Platform.runLater(() -> {
                setInputEnabled(true);
                messageInput.requestFocus();
                if (cause != null) {
                    updateStatus("Error: message processing failed: " + describe(cause));
                }
            });
        });
    }

    /** Returns the cause of a {@link CompletionException}, or the throwable itself. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /** One-line reason: simple class name plus the first line of the message. */
    private static String describe(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message.lines().findFirst().orElse("").strip();
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
            if (LlmProvider.of(modelName) == LlmProvider.ANTHROPIC) {
                currentChatController = createAnthropicChatController(modelName);
                currentChatControllerModel = modelName;
                logger.info("Created chat controller for model: {}", modelName);
                return;
            }

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
     * Creates the chat controller for an Anthropic model. No network call: the SDK client is created
     * on the first request.
     */
    private ChatSession createAnthropicChatController(String modelName) {
        String systemPrompt = SystemPromptBuilder.buildForNativeTools(
            connectionManager.getMcpResources(),
            connectionManager.getMcpPrompts()
        );
        ToolExecutor toolExecutor = new ToolExecutor(connectionManager.getMcpConnectionManager());
        AnthropicConversation conversation = new AnthropicConversation(
            connectionManager.getAnthropicService(),
            LlmProvider.apiModelName(modelName),
            systemPrompt,
            connectionManager.getAnthropicTools(),
            toolExecutor::execute
        );

        Chat chat = chatManager.getCurrentChat();
        return new AnthropicChatController(
            conversation,
            () -> chat != null ? chat.getMessages() : List.of(),
            this::onAIMessageReceived,
            this::onAIThinking,
            this::onAIThinkingFinished
        );
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
