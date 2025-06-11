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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize database service
        databaseService = DatabaseService.getInstance();

        // Load app settings from database
        appSettings = databaseService.loadSettings();

        setupChatList();
        setupMessageList();
        setupMessageInput();
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

    @FXML
    private void handleNewChat() {
        Chat newChat = new Chat("Chat " + (chats.size() + 1));

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
            if (chatController != null && isInitialized) {
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
                String ollamaModelName = parseOllamaModelName(appSettings.getLlmModel());
                ollamaApiClient = new OllamaApiClient(appSettings.getOllamaBaseUrl());

                // 4. Fetch all capabilities from MCP servers
                List<McpSchema.Tool> allMcpTools = mcpConnectionManager.getAllTools();
                List<McpSchema.Resource> allMcpResources = mcpConnectionManager.getAllResources();
                List<McpSchema.Prompt> allMcpPrompts = mcpConnectionManager.getAllPrompts();

                // 5. Prepare for the LLM: Convert MCP tools to Ollama format and build a system prompt
                List<OllamaApi.Tool> ollamaTools = SchemaConverter.convertMcpToolsToOllamaTools(allMcpTools);
                String systemPrompt = SystemPromptBuilder.build(allMcpTools, allMcpResources, allMcpPrompts);

                // 6. Initialize GUI Chat Controller
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

        // Scroll to bottom
        Platform.runLater(() -> {
            if (!chat.getMessages().isEmpty()) {
                messageListView.scrollTo(chat.getMessages().size() - 1);
            }
        });

        // Clear chat controller history and reload if necessary
        if (chatController != null) {
            chatController.clearHistory();
            // TODO: Rebuild chat history from messages if needed
        }
    }

    private void loadChatsFromDatabase() {
        // Load all chats from database
        List<Chat> savedChats = databaseService.getAllChats();
        chats.addAll(savedChats);

        // If no chats exist, create a welcome chat
        if (chats.isEmpty()) {
            Chat welcomeChat = new Chat("Welcome Chat");
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
                HBox hbox = new HBox(10);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.setPadding(new Insets(8, 12, 8, 12));

                Label icon = new Label("💬");
                icon.setStyle("-fx-font-size: 20px;");

                Label nameLabel = new Label(chat.getName());
                nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: normal; -fx-text-fill: white;");

                hbox.getChildren().addAll(icon, nameLabel);
                setGraphic(hbox);
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
