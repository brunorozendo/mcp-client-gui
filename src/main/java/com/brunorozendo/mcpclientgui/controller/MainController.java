package com.brunorozendo.mcpclientgui.controller;

import com.brunorozendo.mcpclientgui.control.GuiChatController;
import com.brunorozendo.mcpclientgui.control.McpConnectionManager;
import com.brunorozendo.mcpclientgui.control.SystemPromptBuilder;
import com.brunorozendo.mcpclientgui.model.*;
import com.brunorozendo.mcpclientgui.service.DatabaseService;
import com.brunorozendo.mcpclientgui.service.McpConfigLoader;
import com.brunorozendo.mcpclientgui.service.OllamaApiClient;
import com.brunorozendo.mcpclientgui.util.SchemaConverter;
import io.modelcontextprotocol.spec.McpSchema;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Main controller for the MCP Client GUI application.
 * 
 * This controller manages the main user interface, including:
 * - Chat list and selection
 * - Message display and input
 * - Model selection and configuration
 * - Integration with MCP servers and LLM models
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // UI Constants
    private static final String TIME_FORMAT_PATTERN = "HH:mm";
    private static final String DEFAULT_CHAT_NAME_PREFIX = "Chat ";
    private static final String APP_TITLE_PREFIX = "MCP Assistant";
    private static final String STATUS_READY = "Ready";
    private static final int MAX_CONTENT_PREVIEW_LENGTH = 50;
    
    // Message display constants
    private static final int MESSAGE_BUBBLE_PADDING = 10;
    private static final int MESSAGE_BUBBLE_MAX_WIDTH = 400;
    private static final String USER_MESSAGE_STYLE = "-fx-background-color: #3d98f4; -fx-background-radius: 18px;";
    private static final String AI_MESSAGE_STYLE = "-fx-background-color: #e7edf4; -fx-background-radius: 18px;";
    private static final String USER_TEXT_COLOR = "white";
    private static final String AI_TEXT_COLOR = "#0d141c";
    
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

    // Data management
    private final ObservableList<Chat> chats = FXCollections.observableArrayList();
    private Chat currentChat;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT_PATTERN);

    // Services and controllers
    private DatabaseService databaseService;
    private AppSettings appSettings = new AppSettings();
    private McpConnectionManager mcpConnectionManager;
    private OllamaApiClient ollamaApiClient;
    private GuiChatController chatController;
    
    // State management
    private boolean isInitialized = false;
    private String currentChatControllerModel = null;
    
    // MCP capabilities cache
    private List<McpSchema.Tool> allMcpTools;
    private List<McpSchema.Resource> allMcpResources;
    private List<McpSchema.Prompt> allMcpPrompts;

    /**
     * Initializes the controller after the FXML has been loaded.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeServices();
        initializeUI();
        loadApplicationData();
        autoInitializeIfConfigured();
    }
    
    /**
     * Initializes core services required by the application.
     */
    private void initializeServices() {
        databaseService = DatabaseService.getInstance();
        appSettings = databaseService.loadSettings();
    }
    
    /**
     * Sets up all UI components and their behaviors.
     */
    private void initializeUI() {
        setupChatList();
        setupMessageList();
        setupMessageInput();
        setupModelComboBox();
    }
    
    /**
     * Loads persisted data from the database.
     */
    private void loadApplicationData() {
        loadChatsFromDatabase();
    }
    
    /**
     * Attempts to auto-initialize MCP and AI connections if settings are valid.
     */
    private void autoInitializeIfConfigured() {
        if (appSettings.isValid()) {
            initializeMcpAndAI();
        } else {
            updateStatusLabel("Not configured. Please go to Settings to configure MCP and LLM.");
        }
    }

    // ===== Chat List Management =====
    
    /**
     * Sets up the chat list view with custom cell factory and selection handling.
     */
    private void setupChatList() {
        chatListView.setItems(chats);
        chatListView.setCellFactory(listView -> createChatListCell());
        chatListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldChat, newChat) -> {
                if (newChat != null) {
                    loadChat(newChat);
                }
            }
        );
    }
    
    /**
     * Creates a custom list cell for displaying chats with context menu.
     */
    private ListCell<Chat> createChatListCell() {
        ChatListCell cell = new ChatListCell();
        cell.setContextMenu(createChatContextMenu(cell));
        return cell;
    }
    
    /**
     * Creates a context menu for chat items with rename and delete options.
     */
    private ContextMenu createChatContextMenu(ChatListCell cell) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem renameItem = new MenuItem("Rename");
        renameItem.setOnAction(e -> {
            Chat chat = cell.getItem();
            if (chat != null) {
                showRenameDialog(chat);
            }
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            Chat chat = cell.getItem();
            if (chat != null) {
                showDeleteConfirmation(chat);
            }
        });

        contextMenu.getItems().addAll(renameItem, new SeparatorMenuItem(), deleteItem);
        return contextMenu;
    }

    // ===== Message List Management =====
    
    /**
     * Sets up the message list view with custom cell factory.
     */
    private void setupMessageList() {
        messageListView.setCellFactory(listView -> new MessageListCell());
        messageListView.setPlaceholder(new Label("Select a chat to start messaging"));
    }

    // ===== Message Input Management =====
    
    /**
     * Sets up the message input area with keyboard shortcuts and initial state.
     */
    private void setupMessageInput() {
        messageInput.setWrapText(true);
        messageInput.addEventFilter(KeyEvent.KEY_PRESSED, this::handleMessageInputKeyPress);
        
        // Initially disable input until properly configured
        setMessageInputEnabled(false);
    }
    
    /**
     * Handles key press events in the message input area.
     */
    private void handleMessageInputKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            handleSendMessage();
        }
    }
    
    /**
     * Enables or disables the message input controls.
     */
    private void setMessageInputEnabled(boolean enabled) {
        messageInput.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }
    
    // ===== Model Selection Management =====
    
    /**
     * Sets up the model selection combo box.
     */
    private void setupModelComboBox() {
        modelComboBox.setPromptText("Select a model");
        updateModelComboBox();
        modelComboBox.setDisable(true);
        modelComboBox.setOnAction(e -> handleModelChange());
    }
    
    /**
     * Updates the model combo box with available models from settings.
     */
    private void updateModelComboBox() {
        String currentSelection = modelComboBox.getValue();
        
        modelComboBox.getItems().clear();
        for (AppSettings.LlmModel model : appSettings.getLlmModels()) {
            modelComboBox.getItems().add(model.getName());
        }
        
        // Restore selection if it still exists
        if (currentSelection != null && modelComboBox.getItems().contains(currentSelection)) {
            modelComboBox.setValue(currentSelection);
        }
    }

    /**
     * Handles changes to the selected model.
     */
    private void handleModelChange() {
        if (currentChat == null || modelComboBox.getValue() == null) {
            return;
        }

        String newModel = modelComboBox.getValue();
        String oldModel = currentChat.getLlmModelName();

        if (!newModel.equals(oldModel)) {
            updateChatModel(newModel);
        }
    }
    
    /**
     * Updates the current chat's model and refreshes related components.
     */
    private void updateChatModel(String newModel) {
        currentChat.setLlmModelName(newModel);
        databaseService.saveChat(currentChat);
        
        updateTitle();
        chatListView.refresh();
        
        if (isInitialized && mcpConnectionManager != null && ollamaApiClient != null) {
            createChatControllerForModel(newModel);
            updateStatusLabel("Switched to model: " + newModel);
        }
    }

    // ===== Action Handlers =====
    
    /**
     * Handles the creation of a new chat.
     */
    @FXML
    private void handleNewChat() {
        String defaultModelName = appSettings.getDefaultLlmModelName();
        if (defaultModelName == null || defaultModelName.isEmpty()) {
            showNotConfiguredAlert();
            return;
        }

        Chat newChat = createNewChat(defaultModelName);
        selectChat(newChat);
        
        if (chatController != null) {
            chatController.clearHistory();
        }
    }
    
    /**
     * Creates a new chat with the specified model.
     */
    private Chat createNewChat(String modelName) {
        String chatName = DEFAULT_CHAT_NAME_PREFIX + (chats.size() + 1);
        Chat newChat = new Chat(chatName, modelName);
        newChat = databaseService.saveChat(newChat);
        chats.add(newChat);
        return newChat;
    }
    
    /**
     * Selects a chat in the list view.
     */
    private void selectChat(Chat chat) {
        chatListView.getSelectionModel().select(chat);
    }

    /**
     * Handles sending a message from the input field.
     */
    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty() || currentChat == null) {
            return;
        }
        
        Message userMessage = createUserMessage(text);
        addMessageToChat(userMessage);
        messageInput.clear();
        scrollToLatestMessage();
        
        processMessageWithAI(text);
    }
    
    /**
     * Creates a new user message.
     */
    private Message createUserMessage(String content) {
        Message message = new Message(content, true, LocalDateTime.now());
        if (currentChat.getId() != null) {
            message = databaseService.saveMessage(message, currentChat.getId());
        }
        return message;
    }
    
    /**
     * Adds a message to the current chat.
     */
    private void addMessageToChat(Message message) {
        currentChat.getMessages().add(message);
    }
    
    /**
     * Scrolls the message list to show the latest message.
     */
    private void scrollToLatestMessage() {
        Platform.runLater(() -> {
            int messageCount = currentChat.getMessages().size();
            if (messageCount > 0) {
                messageListView.scrollTo(messageCount - 1);
            }
        });
    }
    
    /**
     * Processes a user message with the AI if initialized.
     */
    private void processMessageWithAI(String text) {
        if (!isInitialized || mcpConnectionManager == null || ollamaApiClient == null) {
            return;
        }
        
        ensureChatControllerForCurrentChat();
        
        if (chatController != null) {
            setMessageInputEnabled(false);
            
            chatController.processUserMessage(text).whenComplete((result, throwable) -> {
                Platform.runLater(() -> {
                    setMessageInputEnabled(true);
                    messageInput.requestFocus();
                });
            });
        }
    }

    /**
     * Opens the settings dialog.
     */
    @FXML
    private void handleSettings() {
        try {
            openSettingsDialog();
        } catch (IOException e) {
            logger.error("Error loading settings dialog", e);
            showErrorAlert("Error", "Failed to load settings dialog: " + e.getMessage());
        }
    }
    
    /**
     * Opens and displays the settings dialog.
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
            handleSettingsSaved();
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
    private void handleSettingsSaved() {
        appSettings = databaseService.loadSettings();
        updateModelComboBox();
        initializeMcpAndAI();
    }

    // ===== MCP and AI Initialization =====
    
    /**
     * Initializes MCP connections and AI client in a background thread.
     */
    private void initializeMcpAndAI() {
        if (!appSettings.isValid()) {
            updateStatusLabel("Invalid settings. Please check your configuration.");
            return;
        }

        updateStatusLabel("Initializing MCP connections...");
        
        new Thread(this::performMcpInitialization).start();
    }
    
    /**
     * Performs the actual MCP initialization process.
     */
    private void performMcpInitialization() {
        try {
            // Load MCP Configuration
            McpConfigLoader configLoader = new McpConfigLoader();
            McpConfig mcpConfig = configLoader.load(appSettings.getMcpConfigFile());

            // Initialize MCP Connection Manager
            mcpConnectionManager = new McpConnectionManager();
            mcpConnectionManager.initializeClients(mcpConfig);

            // Initialize Ollama API Client
            ollamaApiClient = new OllamaApiClient(appSettings.getOllamaBaseUrl());

            // Fetch capabilities from MCP servers
            fetchMcpCapabilities();
            
            Platform.runLater(this::onMcpInitializationComplete);
        } catch (Exception e) {
            logger.error("Error initializing MCP and AI", e);
            Platform.runLater(() -> onMcpInitializationError(e));
        }
    }
    
    /**
     * Fetches all capabilities from connected MCP servers.
     */
    private void fetchMcpCapabilities() {
        allMcpTools = mcpConnectionManager.getAllTools();
        allMcpResources = mcpConnectionManager.getAllResources();
        allMcpPrompts = mcpConnectionManager.getAllPrompts();
    }
    
    /**
     * Called when MCP initialization completes successfully.
     */
    private void onMcpInitializationComplete() {
        isInitialized = true;
        setMessageInputEnabled(true);
        updateStatusLabel(String.format("Ready! Connected to %d tools from MCP servers.", allMcpTools.size()));
        updateTitle();
    }
    
    /**
     * Called when MCP initialization fails.
     */
    private void onMcpInitializationError(Exception e) {
        updateStatusLabel("Error: " + e.getMessage());
        showErrorAlert("Initialization Error", "Failed to initialize MCP and AI: " + e.getMessage());
    }

    // ===== AI Message Handling =====
    
    /**
     * Handles receiving a message from the AI.
     */
    private void onAIMessageReceived(Message message) {
        if (currentChat == null) {
            return;
        }
        
        if (currentChat.getId() != null) {
            message = databaseService.saveMessage(message, currentChat.getId());
        }
        
        addMessageToChat(message);
        scrollToLatestMessage();
    }

    /**
     * Updates the status when AI is thinking.
     */
    private void onThinking(String thinkingText) {
        Platform.runLater(() -> updateStatusLabel(thinkingText));
    }

    /**
     * Called when AI finishes thinking.
     */
    private void onThinkingFinished() {
        Platform.runLater(() -> updateStatusLabel(STATUS_READY));
    }

    // ===== Chat Controller Management =====
    
    /**
     * Parses the Ollama model name from the full model string.
     */
    private String parseOllamaModelName(String llmModelString) {
        final String OLLAMA_PREFIX = "ollama:";
        if (llmModelString.startsWith(OLLAMA_PREFIX)) {
            return llmModelString.substring(OLLAMA_PREFIX.length());
        }
        return llmModelString;
    }

    /**
     * Ensures the chat controller is configured for the current chat's model.
     */
    private void ensureChatControllerForCurrentChat() {
        if (currentChat == null || currentChat.getLlmModelName() == null) {
            return;
        }

        String chatModel = currentChat.getLlmModelName();
        
        if (chatController == null || !chatModel.equals(currentChatControllerModel)) {
            createChatControllerForModel(chatModel);
        }
    }

    /**
     * Creates a new chat controller for the specified model.
     */
    private void createChatControllerForModel(String modelName) {
        if (ollamaApiClient == null || mcpConnectionManager == null || allMcpTools == null) {
            return;
        }

        try {
            // Convert MCP tools to Ollama format and build system prompt
            List<OllamaApi.Tool> ollamaTools = SchemaConverter.convertMcpToolsToOllamaTools(allMcpTools);
            String systemPrompt = SystemPromptBuilder.build(allMcpTools, allMcpResources, allMcpPrompts);

            // Create new chat controller
            String ollamaModelName = parseOllamaModelName(modelName);
            chatController = new GuiChatController(
                    ollamaModelName,
                    ollamaApiClient,
                    mcpConnectionManager,
                    systemPrompt,
                    ollamaTools
            );

            configureChatControllerCallbacks();
            currentChatControllerModel = modelName;

            logger.info("Created chat controller for model: {}", modelName);
        } catch (Exception e) {
            logger.error("Error creating chat controller for model: {}", modelName, e);
            showErrorAlert("Model Error", "Failed to initialize model " + modelName + ": " + e.getMessage());
        }
    }
    
    /**
     * Configures callbacks for the chat controller.
     */
    private void configureChatControllerCallbacks() {
        chatController.setOnMessageReceived(this::onAIMessageReceived);
        chatController.setOnThinking(this::onThinking);
        chatController.setOnThinkingFinished(this::onThinkingFinished);
    }

    // ===== Chat Loading and Management =====
    
    /**
     * Loads and displays a chat.
     */
    private void loadChat(Chat chat) {
        currentChat = chat;
        updateTitle();
        
        loadChatMessages(chat);
        updateModelSelection(chat);
        scrollToLatestMessage();
        
        if (isInitialized && mcpConnectionManager != null && ollamaApiClient != null) {
            ensureChatControllerForCurrentChat();
            
            if (chatController != null) {
                chatController.clearHistory();
                // TODO: Rebuild chat history from messages if needed
            }
        }
    }
    
    /**
     * Loads messages for a chat from the database if needed.
     */
    private void loadChatMessages(Chat chat) {
        if (chat.getId() != null && !chat.hasMessages()) {
            List<Message> messages = databaseService.getMessagesForChat(chat.getId());
            chat.getMessages().addAll(messages);
        }
        messageListView.setItems(chat.getMessages());
    }
    
    /**
     * Updates the model selection for a chat.
     */
    private void updateModelSelection(Chat chat) {
        modelComboBox.setDisable(false);
        modelComboBox.setValue(chat.getLlmModelName());
    }

    /**
     * Loads all chats from the database.
     */
    private void loadChatsFromDatabase() {
        List<Chat> savedChats = databaseService.getAllChats();
        
        // Handle migration for chats without models
        migrateChatsWithoutModels(savedChats);
        
        chats.addAll(savedChats);
        
        // Create welcome chat if no chats exist
        if (chats.isEmpty()) {
            createWelcomeChat();
        }
        
        // Select first chat
        if (!chats.isEmpty()) {
            chatListView.getSelectionModel().select(0);
        }
    }
    
    /**
     * Migrates chats that don't have a model assigned.
     */
    private void migrateChatsWithoutModels(List<Chat> savedChats) {
        String defaultModelName = appSettings.getDefaultLlmModelName();
        
        for (Chat chat : savedChats) {
            if (chat.getLlmModelName() == null || chat.getLlmModelName().isEmpty()) {
                if (defaultModelName != null && !defaultModelName.isEmpty()) {
                    chat.setLlmModelName(defaultModelName);
                    databaseService.saveChat(chat);
                }
            }
        }
    }
    
    /**
     * Creates a welcome chat for first-time users.
     */
    private void createWelcomeChat() {
        String defaultModelName = appSettings.getDefaultLlmModelName();
        if (defaultModelName == null || defaultModelName.isEmpty()) {
            defaultModelName = "qwen3:8b"; // Fallback
        }

        Chat welcomeChat = new Chat("Welcome Chat", defaultModelName);
        welcomeChat = databaseService.saveChat(welcomeChat);

        Message welcomeMessage = new Message(
            "Welcome to MCP Client GUI! Configure your settings to get started.", 
            false, 
            LocalDateTime.now()
        );
        databaseService.saveMessage(welcomeMessage, welcomeChat.getId());
        welcomeChat.getMessages().add(welcomeMessage);

        chats.add(welcomeChat);
    }

    // ===== UI Update Methods =====
    
    /**
     * Updates the status label with the given text.
     */
    private void updateStatusLabel(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }
    
    /**
     * Updates the title label based on the current state.
     */
    private void updateTitle() {
        String title = APP_TITLE_PREFIX;
        if (currentChat != null) {
            title += " - " + currentChat.getName();
        } else if (isInitialized) {
            title += " - Ready!";
        }
        titleLabel.setText(title);
    }

    // ===== Dialog Methods =====
    
    /**
     * Shows the rename dialog for a chat.
     */
    private void showRenameDialog(Chat chat) {
        TextInputDialog dialog = new TextInputDialog(chat.getName());
        dialog.setTitle("Rename Chat");
        dialog.setHeaderText(null);
        dialog.setContentText("Enter new name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                renameChat(chat, newName.trim());
            }
        });
    }
    
    /**
     * Renames a chat and updates the UI.
     */
    private void renameChat(Chat chat, String newName) {
        chat.setName(newName);
        databaseService.saveChat(chat);
        chatListView.refresh();
        
        if (chat == currentChat) {
            updateTitle();
        }
    }

    /**
     * Shows the delete confirmation dialog for a chat.
     */
    private void showDeleteConfirmation(Chat chat) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Chat");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to delete '" + chat.getName() + "'? This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteChat(chat);
            }
        });
    }
    
    /**
     * Deletes a chat and updates the UI.
     */
    private void deleteChat(Chat chat) {
        // Delete from database
        if (chat.getId() != null) {
            databaseService.deleteChat(chat.getId());
        }

        // Remove from list
        chats.remove(chat);

        // Clear view if this was the current chat
        if (chat == currentChat) {
            clearCurrentChatView();
        }

        // Select another chat if available
        if (!chats.isEmpty()) {
            chatListView.getSelectionModel().select(0);
        }
    }
    
    /**
     * Clears the view when no chat is selected.
     */
    private void clearCurrentChatView() {
        currentChat = null;
        messageListView.setItems(FXCollections.observableArrayList());
        titleLabel.setText(APP_TITLE_PREFIX);
        modelComboBox.setValue(null);
        modelComboBox.setDisable(true);
    }

    /**
     * Shows an alert indicating the application is not configured.
     */
    private void showNotConfiguredAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Not Configured");
        alert.setHeaderText(null);
        alert.setContentText("Please configure the LLM model and MCP settings first by clicking the Settings button.");
        alert.showAndWait();
    }

    /**
     * Shows an error alert with the specified title and message.
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ===== Inner Classes =====
    
    /**
     * Custom cell for displaying chats in the list view.
     */
    private class ChatListCell extends ListCell<Chat> {
        @Override
        protected void updateItem(Chat chat, boolean empty) {
            super.updateItem(chat, empty);

            if (empty || chat == null) {
                setText(null);
                setGraphic(null);
            } else {
                setGraphic(createChatCellContent(chat));
            }
        }
        
        /**
         * Creates the visual content for a chat cell.
         */
        private VBox createChatCellContent(Chat chat) {
            VBox vbox = new VBox(2);
            vbox.setPadding(new Insets(8, 12, 8, 12));

            HBox hbox = createChatHeader(chat);
            vbox.getChildren().add(hbox);
            
            // Add model label if available
            if (chat.getLlmModelName() != null && !chat.getLlmModelName().isEmpty()) {
                Label modelLabel = createModelLabel(chat.getLlmModelName());
                vbox.getChildren().add(modelLabel);
            }
            
            return vbox;
        }
        
        /**
         * Creates the header row for a chat cell.
         */
        private HBox createChatHeader(Chat chat) {
            HBox hbox = new HBox(8);
            hbox.setAlignment(Pos.CENTER_LEFT);

            Label icon = new Label("💬");
            icon.setStyle("-fx-font-size: 18px;");

            Label nameLabel = new Label(chat.getName());
            nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: normal; -fx-text-fill: white;");

            hbox.getChildren().addAll(icon, nameLabel);
            return hbox;
        }
        
        /**
         * Creates a label displaying the model name.
         */
        private Label createModelLabel(String modelName) {
            Label modelLabel = new Label(modelName);
            modelLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255, 255, 255, 0.7); -fx-padding: 0 0 0 26;");
            return modelLabel;
        }
    }

    /**
     * Custom cell for displaying messages in the list view.
     */
    private class MessageListCell extends ListCell<Message> {
        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                setGraphic(createMessageCell(message));
            }
        }
        
        /**
         * Creates the visual content for a message cell.
         */
        private VBox createMessageCell(Message message) {
            VBox messageBox = new VBox(5);
            messageBox.setPadding(new Insets(MESSAGE_BUBBLE_PADDING));

            HBox bubbleContainer = createMessageBubble(message);
            Label timeLabel = createTimeLabel(message);

            // Align based on sender
            if (message.isFromUser()) {
                alignRight(messageBox, bubbleContainer, timeLabel);
            } else {
                alignLeft(messageBox, bubbleContainer, timeLabel);
            }

            messageBox.getChildren().addAll(bubbleContainer, timeLabel);
            return messageBox;
        }
        
        /**
         * Creates the message bubble with rendered content.
         */
        private HBox createMessageBubble(Message message) {
            HBox bubbleContainer = new HBox();
            TextFlow textFlow = renderMarkdown(message.getContent(), message.isFromUser());
            bubbleContainer.getChildren().add(textFlow);
            return bubbleContainer;
        }
        
        /**
         * Creates a time label for the message.
         */
        private Label createTimeLabel(Message message) {
            Label timeLabel = new Label(message.getTimestamp().format(timeFormatter));
            timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
            return timeLabel;
        }
        
        /**
         * Aligns components to the right for user messages.
         */
        private void alignRight(VBox messageBox, HBox bubbleContainer, Label timeLabel) {
            bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
            timeLabel.setAlignment(Pos.CENTER_RIGHT);
            messageBox.setAlignment(Pos.CENTER_RIGHT);
        }
        
        /**
         * Aligns components to the left for AI messages.
         */
        private void alignLeft(VBox messageBox, HBox bubbleContainer, Label timeLabel) {
            bubbleContainer.setAlignment(Pos.CENTER_LEFT);
            timeLabel.setAlignment(Pos.CENTER_LEFT);
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }
    }

    // ===== Markdown Rendering =====
    
    /**
     * Converts markdown text to JavaFX nodes for display.
     */
    private TextFlow renderMarkdown(String markdownText, boolean isUserMessage) {
        // Create parser and parse markdown
        Parser parser = Parser.builder().build();
        org.commonmark.node.Node document = parser.parse(markdownText);

        // Create TextFlow for formatted text
        TextFlow textFlow = new TextFlow();
        textFlow.setPadding(new Insets(MESSAGE_BUBBLE_PADDING, 15, MESSAGE_BUBBLE_PADDING, 15));
        textFlow.setMaxWidth(MESSAGE_BUBBLE_MAX_WIDTH);

        // Apply style based on message sender
        String style = isUserMessage ? USER_MESSAGE_STYLE : AI_MESSAGE_STYLE;
        textFlow.setStyle(style);

        // Determine text color
        String textColor = isUserMessage ? USER_TEXT_COLOR : AI_TEXT_COLOR;

        // Convert to HTML and process
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String html = renderer.render(document);
        processHtmlToTextFlow(html, textFlow, textColor);

        return textFlow;
    }

    /**
     * Processes HTML content and adds styled Text nodes to the TextFlow.
     * This is a simplified processor that handles basic markdown formatting.
     */
    private void processHtmlToTextFlow(String html, TextFlow textFlow, String baseTextColor) {
        // Replace common markdown elements with styled text
        String[] paragraphs = html.split("<p>");

        for (int i = 0; i < paragraphs.length; i++) {
            if (paragraphs[i].trim().isEmpty()) continue;

            String paragraph = paragraphs[i].replaceAll("</p>.*", "");
            processFormattedText(paragraph, textFlow, baseTextColor);

            // Add newline between paragraphs
            if (i < paragraphs.length - 1) {
                textFlow.getChildren().add(new Text("\n"));
            }
        }

        // Fallback if no content was added
        if (textFlow.getChildren().isEmpty()) {
            Text fallbackText = new Text(html.replaceAll("<[^>]*>", ""));
            fallbackText.setStyle("-fx-fill: " + baseTextColor + ";");
            textFlow.getChildren().add(fallbackText);
        }
    }
    
    /**
     * Processes text with HTML formatting tags and creates styled Text nodes.
     */
    private void processFormattedText(String htmlText, TextFlow textFlow, String baseTextColor) {
        // Process strong/bold tags
        String processedText = processHtmlTag(htmlText, "strong", "§BOLD_START§", "§BOLD_END§");
        
        // Process em/italic tags
        processedText = processHtmlTag(processedText, "em", "§ITALIC_START§", "§ITALIC_END§");
        
        // Process code tags
        processedText = processHtmlTag(processedText, "code", "§CODE_START§", "§CODE_END§");
        
        // Remove remaining HTML tags
        String cleanText = processedText.replaceAll("<[^>]*>", "");
        
        // Create styled text nodes
        createStyledTextNodes(cleanText, textFlow, baseTextColor);
    }
    
    /**
     * Processes a specific HTML tag and replaces it with custom markers.
     */
    private String processHtmlTag(String text, String tagName, String startMarker, String endMarker) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        
        StringBuilder result = new StringBuilder();
        int currentPos = 0;
        
        while (currentPos < text.length()) {
            int tagStart = text.indexOf(openTag, currentPos);
            if (tagStart == -1) {
                result.append(text.substring(currentPos));
                break;
            }
            
            result.append(text.substring(currentPos, tagStart));
            result.append(startMarker);
            
            int tagEnd = text.indexOf(closeTag, tagStart);
            if (tagEnd == -1) {
                result.append(text.substring(tagStart + openTag.length()));
                break;
            }
            
            result.append(text.substring(tagStart + openTag.length(), tagEnd));
            result.append(endMarker);
            
            currentPos = tagEnd + closeTag.length();
        }
        
        return result.toString();
    }
    
    /**
     * Creates styled Text nodes based on formatting markers.
     */
    private void createStyledTextNodes(String markedText, TextFlow textFlow, String baseTextColor) {
        String[] parts = markedText.split("(§BOLD_START§|§BOLD_END§|§ITALIC_START§|§ITALIC_END§|§CODE_START§|§CODE_END§)");
        
        boolean isBold = false;
        boolean isItalic = false;
        boolean isCode = false;
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            
            // Check for format changes
            if (markedText.contains("§BOLD_START§" + part)) {
                isBold = true;
            } else if (markedText.contains(part + "§BOLD_END§")) {
                isBold = false;
            } else if (markedText.contains("§ITALIC_START§" + part)) {
                isItalic = true;
            } else if (markedText.contains(part + "§ITALIC_END§")) {
                isItalic = false;
            } else if (markedText.contains("§CODE_START§" + part)) {
                isCode = true;
            } else if (markedText.contains(part + "§CODE_END§")) {
                isCode = false;
            }
            
            // Create styled text
            Text text = new Text(part);
            String style = buildTextStyle(baseTextColor, isBold, isItalic, isCode);
            text.setStyle(style);
            textFlow.getChildren().add(text);
        }
    }
    
    /**
     * Builds a style string based on formatting flags.
     */
    private String buildTextStyle(String baseColor, boolean isBold, boolean isItalic, boolean isCode) {
        StringBuilder style = new StringBuilder("-fx-fill: " + baseColor + ";");
        
        if (isBold) {
            style.append(" -fx-font-weight: bold;");
        }
        if (isItalic) {
            style.append(" -fx-font-style: italic;");
        }
        if (isCode) {
            style.append(" -fx-font-family: monospace; -fx-background-color: rgba(0,0,0,0.1); -fx-padding: 2;");
        }
        
        return style.toString();
    }
}
