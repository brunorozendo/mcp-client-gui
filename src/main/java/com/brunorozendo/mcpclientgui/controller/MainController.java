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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private ListView<Chat> chatListView;

    @FXML
    private Label titleLabel;

    @FXML
    private ListView<Message> messageListView;

    @FXML
    private TextArea messageInput;

    @FXML
    private Button sendButton;

    @FXML
    private Button newChatButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Label statusLabel;
    
    @FXML
    private ComboBox<String> modelComboBox;

    private ObservableList<Chat> chats = FXCollections.observableArrayList();
    private Chat currentChat;
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // Database service
    private DatabaseService databaseService;

    // MCP and AI related components
    private AppSettings appSettings = new AppSettings();
    private McpConnectionManager mcpConnectionManager;
    private OllamaApiClient ollamaApiClient;
    private GuiChatController chatController;
    private boolean isInitialized = false;
    private String currentChatControllerModel = null;
    
    // MCP data cached for creating chat controllers
    private List<McpSchema.Tool> allMcpTools;
    private List<McpSchema.Resource> allMcpResources;
    private List<McpSchema.Prompt> allMcpPrompts;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize database service
        databaseService = DatabaseService.getInstance();

        // Load app settings from database
        appSettings = databaseService.loadSettings();

        setupChatList();
        setupMessageList();
        setupMessageInput();
        setupModelComboBox();
        loadChatsFromDatabase();

        // Auto-initialize if settings are valid
        if (appSettings.isValid()) {
            initializeMcpAndAI();
        } else {
            updateStatusLabel("Not configured. Please go to Settings to configure MCP and LLM.");
        }
    }

    private void setupChatList() {
        chatListView.setItems(chats);
        chatListView.setCellFactory(listView -> {
            ChatListCell cell = new ChatListCell();

            // Create context menu for chat items
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
            cell.setContextMenu(contextMenu);

            return cell;
        });

        chatListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChat, newChat) -> {
            if (newChat != null) {
                loadChat(newChat);
            }
        });
    }

    private void setupMessageList() {
        messageListView.setCellFactory(listView -> new MessageListCell());
        messageListView.setPlaceholder(new Label("Select a chat to start messaging"));
    }

    private void setupMessageInput() {
        messageInput.setWrapText(true);
        messageInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });

        // Initially disable input until properly configured
        messageInput.setDisable(true);
        sendButton.setDisable(true);
    }
    
    private void setupModelComboBox() {
        // Set placeholder text
        modelComboBox.setPromptText("Select a model");
        
        // Populate with available models
        updateModelComboBox();
        
        // Initially disable until a chat is selected
        modelComboBox.setDisable(true);
        
        // Handle model selection changes
        modelComboBox.setOnAction(e -> handleModelChange());
    }
    
    private void updateModelComboBox() {
        // Store current selection
        String currentSelection = modelComboBox.getValue();
        
        // Clear and repopulate
        modelComboBox.getItems().clear();
        
        // Add all available models
        for (AppSettings.LlmModel model : appSettings.getLlmModels()) {
            modelComboBox.getItems().add(model.getName());
        }
        
        // Restore selection if it still exists
        if (currentSelection != null && modelComboBox.getItems().contains(currentSelection)) {
            modelComboBox.setValue(currentSelection);
        }
    }
    
    private void handleModelChange() {
        if (currentChat == null || modelComboBox.getValue() == null) {
            return;
        }
        
        String newModel = modelComboBox.getValue();
        String oldModel = currentChat.getLlmModelName();
        
        // Only update if the model actually changed
        if (!newModel.equals(oldModel)) {
            // Update the chat's model
            currentChat.setLlmModelName(newModel);
            
            // Save to database
            databaseService.saveChat(currentChat);
            
            // Update the title
            titleLabel.setText("MCP Assistant - " + currentChat.getName());
            
            // Refresh the chat list to show the updated model
            chatListView.refresh();
            
            // Create new chat controller for the new model
            if (isInitialized && mcpConnectionManager != null && ollamaApiClient != null) {
                createChatControllerForModel(newModel);
                
                // Show status update
                updateStatusLabel("Switched to model: " + newModel);
            }
        }
    }

    @FXML
    private void handleNewChat() {
        // Get default model from settings
        String defaultModelName = appSettings.getDefaultLlmModelName();
        if (defaultModelName == null || defaultModelName.isEmpty()) {
            showNotConfiguredAlert();
            return;
        }
        
        Chat newChat = new Chat("Chat " + (chats.size() + 1), defaultModelName);

        // Save to database
        newChat = databaseService.saveChat(newChat);

        chats.add(newChat);
        chatListView.getSelectionModel().select(newChat);

        // Clear chat controller history for new chat
        if (chatController != null) {
            chatController.clearHistory();
        }
    }

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (!text.isEmpty() && currentChat != null) {
            // Create user message
            Message userMessage = new Message(text, true, LocalDateTime.now());

            // Save to database if chat has an ID
            if (currentChat.getId() != null) {
                userMessage = databaseService.saveMessage(userMessage, currentChat.getId());
            }

            // Add to chat
            currentChat.getMessages().add(userMessage);
            messageInput.clear();

            // Scroll to bottom
            Platform.runLater(() -> messageListView.scrollTo(currentChat.getMessages().size() - 1));

            // Process with chat controller if initialized
            if (isInitialized && mcpConnectionManager != null && ollamaApiClient != null) {
                // Ensure chat controller is using the correct model for this chat
                ensureChatControllerForCurrentChat();
                
                if (chatController != null) {
                    // Disable input during processing
                    messageInput.setDisable(true);
                    sendButton.setDisable(true);

                    chatController.processUserMessage(text).whenComplete((result, throwable) -> {
                        Platform.runLater(() -> {
                            messageInput.setDisable(false);
                            sendButton.setDisable(false);
                            messageInput.requestFocus();
                        });
                    });
                }
            }
        }
    }

    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
            VBox settingsRoot = loader.load();

            SettingsController settingsController = loader.getController();

            Stage settingsStage = new Stage();
            settingsStage.setTitle("Settings");
            settingsStage.initModality(Modality.WINDOW_MODAL);
            settingsStage.initOwner(settingsButton.getScene().getWindow());
            settingsStage.setScene(new Scene(settingsRoot));
            settingsStage.setResizable(false);

            settingsController.setDialogStage(settingsStage);
            settingsController.setSettings(appSettings);

            settingsStage.showAndWait();

            if (settingsController.isSaved()) {
                // Reload settings to get updated models
                appSettings = databaseService.loadSettings();
                
                // Update the model ComboBox with new models
                updateModelComboBox();
                
                // Re-initialize MCP and AI
                initializeMcpAndAI();
            }

        } catch (IOException e) {
            logger.error("Error loading settings dialog", e);
            showErrorAlert("Error", "Failed to load settings dialog: " + e.getMessage());
        }
    }

    private void initializeMcpAndAI() {
        if (!appSettings.isValid()) {
            updateStatusLabel("Invalid settings. Please check your configuration.");
            return;
        }

        updateStatusLabel("Initializing MCP connections...");

        // Run initialization in background thread
        new Thread(() -> {
            try {
                // 1. Load MCP Configuration
                McpConfigLoader configLoader = new McpConfigLoader();
                McpConfig mcpConfig = configLoader.load(appSettings.getMcpConfigFile());

                // 2. Initialize MCP Connection Manager
                mcpConnectionManager = new McpConnectionManager();
                mcpConnectionManager.initializeClients(mcpConfig);

                // 3. Initialize Ollama API Client
                ollamaApiClient = new OllamaApiClient(appSettings.getOllamaBaseUrl());

                // 4. Fetch all capabilities from MCP servers
                List<McpSchema.Tool> allMcpTools = mcpConnectionManager.getAllTools();
                List<McpSchema.Resource> allMcpResources = mcpConnectionManager.getAllResources();
                List<McpSchema.Prompt> allMcpPrompts = mcpConnectionManager.getAllPrompts();

                // Store MCP data for later use
                this.allMcpTools = allMcpTools;
                this.allMcpResources = allMcpResources;
                this.allMcpPrompts = allMcpPrompts;

                Platform.runLater(() -> {
                    isInitialized = true;
                    messageInput.setDisable(false);
                    sendButton.setDisable(false);
                    updateStatusLabel("Ready! Connected to " + allMcpTools.size() + " tools from MCP servers.");
                    titleLabel.setText("MCP Assistant - Ready!");
                });

            } catch (Exception e) {
                logger.error("Error initializing MCP and AI", e);
                Platform.runLater(() -> {
                    updateStatusLabel("Error: " + e.getMessage());
                    showErrorAlert("Initialization Error", "Failed to initialize MCP and AI: " + e.getMessage());
                });
            }
        }).start();
    }

    private void onAIMessageReceived(Message message) {
        if (currentChat != null) {
            // Save to database if chat has an ID
            if (currentChat.getId() != null) {
                message = databaseService.saveMessage(message, currentChat.getId());
            }

            currentChat.getMessages().add(message);
            Platform.runLater(() -> messageListView.scrollTo(currentChat.getMessages().size() - 1));
        }
    }

    private void onThinking(String thinkingText) {
        Platform.runLater(() -> updateStatusLabel(thinkingText));
    }

    private void onThinkingFinished() {
        Platform.runLater(() -> updateStatusLabel("Ready"));
    }

    private String parseOllamaModelName(String llmModelString) {
        if (llmModelString.startsWith("ollama:")) {
            return llmModelString.substring("ollama:".length());
        }
        return llmModelString;
    }
    
    private void ensureChatControllerForCurrentChat() {
        if (currentChat == null || currentChat.getLlmModelName() == null) {
            return;
        }
        
        String chatModel = currentChat.getLlmModelName();
        
        // Check if we need to create a new chat controller or if the current one matches
        if (chatController == null || !chatModel.equals(currentChatControllerModel)) {
            createChatControllerForModel(chatModel);
        }
    }
    
    private void createChatControllerForModel(String modelName) {
        if (ollamaApiClient == null || mcpConnectionManager == null || allMcpTools == null) {
            return;
        }
        
        try {
            // Prepare for the LLM: Convert MCP tools to Ollama format and build a system prompt
            List<OllamaApi.Tool> ollamaTools = SchemaConverter.convertMcpToolsToOllamaTools(allMcpTools);
            String systemPrompt = SystemPromptBuilder.build(allMcpTools, allMcpResources, allMcpPrompts);

            // Create new chat controller with the specified model
            String ollamaModelName = parseOllamaModelName(modelName);
            chatController = new GuiChatController(
                    ollamaModelName,
                    ollamaApiClient,
                    mcpConnectionManager,
                    systemPrompt,
                    ollamaTools
            );

            // Set up callbacks for GUI updates
            chatController.setOnMessageReceived(this::onAIMessageReceived);
            chatController.setOnThinking(this::onThinking);
            chatController.setOnThinkingFinished(this::onThinkingFinished);
            
            currentChatControllerModel = modelName;
            
            logger.info("Created chat controller for model: " + modelName);
        } catch (Exception e) {
            logger.error("Error creating chat controller for model: " + modelName, e);
            showErrorAlert("Model Error", "Failed to initialize model " + modelName + ": " + e.getMessage());
        }
    }

    private void updateStatusLabel(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    private void showNotConfiguredAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Not Configured");
        alert.setHeaderText(null);
        alert.setContentText("Please configure the LLM model and MCP settings first by clicking the Settings button.");
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void loadChat(Chat chat) {
        currentChat = chat;
        titleLabel.setText("MCP Assistant - " + chat.getName());

        // Load messages from database if not already loaded
        if (chat.getId() != null && chat.getMessages().isEmpty()) {
            List<Message> messages = databaseService.getMessagesForChat(chat.getId());
            chat.getMessages().addAll(messages);
        }

        messageListView.setItems(chat.getMessages());
        
        // Update model ComboBox
        modelComboBox.setDisable(false);
        modelComboBox.setValue(chat.getLlmModelName());

        // Scroll to bottom
        Platform.runLater(() -> {
            if (!chat.getMessages().isEmpty()) {
                messageListView.scrollTo(chat.getMessages().size() - 1);
            }
        });

        // Ensure we have the right chat controller for this chat's model
        if (isInitialized && mcpConnectionManager != null && ollamaApiClient != null) {
            ensureChatControllerForCurrentChat();
            
            // Clear chat controller history
            if (chatController != null) {
                chatController.clearHistory();
                // TODO: Rebuild chat history from messages if needed
            }
        }
    }

    private void loadChatsFromDatabase() {
        // Load all chats from database
        List<Chat> savedChats = databaseService.getAllChats();
        
        // Handle migration: set default model for chats without a model
        String defaultModelName = appSettings.getDefaultLlmModelName();
        for (Chat chat : savedChats) {
            if (chat.getLlmModelName() == null || chat.getLlmModelName().isEmpty()) {
                if (defaultModelName != null && !defaultModelName.isEmpty()) {
                    chat.setLlmModelName(defaultModelName);
                    databaseService.saveChat(chat);
                }
            }
        }
        
        chats.addAll(savedChats);

        // If no chats exist, create a welcome chat
        if (chats.isEmpty()) {
            if (defaultModelName == null || defaultModelName.isEmpty()) {
                defaultModelName = "llama3.2"; // Fallback
            }
            
            Chat welcomeChat = new Chat("Welcome Chat", defaultModelName);
            welcomeChat = databaseService.saveChat(welcomeChat);

            Message welcomeMessage = new Message("Welcome to MCP Client GUI! Configure your settings to get started.", false, LocalDateTime.now());
            databaseService.saveMessage(welcomeMessage, welcomeChat.getId());
            welcomeChat.getMessages().add(welcomeMessage);

            chats.add(welcomeChat);
        }

        // Select first chat if available
        if (!chats.isEmpty()) {
            chatListView.getSelectionModel().select(0);
        }
    }

    private void showRenameDialog(Chat chat) {
        TextInputDialog dialog = new TextInputDialog(chat.getName());
        dialog.setTitle("Rename Chat");
        dialog.setHeaderText(null);
        dialog.setContentText("Enter new name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                chat.setName(newName.trim());
                databaseService.saveChat(chat);

                // Refresh the list view
                chatListView.refresh();

                // Update title if this is the current chat
                if (chat == currentChat) {
                    titleLabel.setText("MCP Assistant - " + chat.getName());
                }
            }
        });
    }

    private void showDeleteConfirmation(Chat chat) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Chat");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to delete '" + chat.getName() + "'? This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Delete from database
                if (chat.getId() != null) {
                    databaseService.deleteChat(chat.getId());
                }

                // Remove from list
                chats.remove(chat);

                // If this was the current chat, clear the view
                if (chat == currentChat) {
                    currentChat = null;
                    messageListView.setItems(FXCollections.observableArrayList());
                    titleLabel.setText("MCP Assistant");
                    modelComboBox.setValue(null);
                    modelComboBox.setDisable(true);
                }

                // Select another chat if available
                if (!chats.isEmpty()) {
                    chatListView.getSelectionModel().select(0);
                }
            }
        });
    }

    // Custom cell for chat list
    private class ChatListCell extends ListCell<Chat> {
        @Override
        protected void updateItem(Chat chat, boolean empty) {
            super.updateItem(chat, empty);

            if (empty || chat == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(2);
                vbox.setPadding(new Insets(8, 12, 8, 12));

                HBox hbox = new HBox(8);
                hbox.setAlignment(Pos.CENTER_LEFT);

                Label icon = new Label("💬");
                icon.setStyle("-fx-font-size: 18px;");

                Label nameLabel = new Label(chat.getName());
                nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: normal; -fx-text-fill: white;");

                hbox.getChildren().addAll(icon, nameLabel);
                
                // Add model label if available
                if (chat.getLlmModelName() != null && !chat.getLlmModelName().isEmpty()) {
                    Label modelLabel = new Label(chat.getLlmModelName());
                    modelLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255, 255, 255, 0.7); -fx-padding: 0 0 0 26;");
                    vbox.getChildren().addAll(hbox, modelLabel);
                } else {
                    vbox.getChildren().add(hbox);
                }
                
                setGraphic(vbox);
            }
        }
    }

    // Custom cell for message list
    private class MessageListCell extends ListCell<Message> {
        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                VBox messageBox = new VBox(5);
                messageBox.setPadding(new Insets(10));

                // Message bubble
                HBox bubbleContainer = new HBox();
                TextFlow textFlow = new TextFlow(new Text(message.getContent()));
                textFlow.setPadding(new Insets(10, 15, 10, 15));
                textFlow.setMaxWidth(400);

                // Time label
                Label timeLabel = new Label(message.getTimestamp().format(timeFormatter));
                timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

                if (message.isFromUser()) {
                    textFlow.setStyle("-fx-background-color: #3d98f4; -fx-background-radius: 18px;");
                    textFlow.getChildren().forEach(node -> ((Text)node).setStyle("-fx-fill: white;"));
                    bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
                    timeLabel.setAlignment(Pos.CENTER_RIGHT);
                    messageBox.setAlignment(Pos.CENTER_RIGHT);
                } else {
                    textFlow.setStyle("-fx-background-color: #e7edf4; -fx-background-radius: 18px;");
                    textFlow.getChildren().forEach(node -> ((Text)node).setStyle("-fx-fill: #0d141c;"));
                    bubbleContainer.setAlignment(Pos.CENTER_LEFT);
                    timeLabel.setAlignment(Pos.CENTER_LEFT);
                    messageBox.setAlignment(Pos.CENTER_LEFT);
                }

                bubbleContainer.getChildren().add(textFlow);
                messageBox.getChildren().addAll(bubbleContainer, timeLabel);

                setGraphic(messageBox);
            }
        }
    }
}
