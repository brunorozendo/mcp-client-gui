package com.brunorozendo.mcpclientgui.ui.components;

import com.brunorozendo.mcpclientgui.model.Chat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

/**
 * Factory for creating custom chat list cells with context menus.
 * Handles the visual representation of chats in the list view.
 */
public class ChatListCellFactory implements Callback<ListView<Chat>, ListCell<Chat>> {
    
    // Style constants
    private static final String CHAT_ICON = "💬";
    private static final String ICON_STYLE = "-fx-font-size: 18px;";
    private static final String NAME_STYLE = "-fx-font-size: 14px; -fx-font-weight: normal; -fx-text-fill: white;";
    private static final String MODEL_STYLE = "-fx-font-size: 11px; -fx-text-fill: rgba(255, 255, 255, 0.7); -fx-padding: 0 0 0 26;";
    
    // Layout constants
    private static final int CELL_PADDING_TOP_BOTTOM = 8;
    private static final int CELL_PADDING_LEFT_RIGHT = 12;
    private static final int CELL_SPACING = 2;
    private static final int HEADER_SPACING = 8;
    
    // Callbacks for context menu actions
    private ChatActionCallback renameCallback;
    private ChatActionCallback deleteCallback;
    
    /**
     * Functional interface for chat action callbacks.
     */
    @FunctionalInterface
    public interface ChatActionCallback {
        void onAction(Chat chat);
    }
    
    /**
     * Sets the callback for rename actions.
     * 
     * @param callback The callback to invoke when rename is selected
     */
    public void setOnRename(ChatActionCallback callback) {
        this.renameCallback = callback;
    }
    
    /**
     * Sets the callback for delete actions.
     * 
     * @param callback The callback to invoke when delete is selected
     */
    public void setOnDelete(ChatActionCallback callback) {
        this.deleteCallback = callback;
    }
    
    @Override
    public ListCell<Chat> call(ListView<Chat> listView) {
        ChatListCell cell = new ChatListCell();
        cell.setContextMenu(createContextMenu(cell));
        return cell;
    }
    
    /**
     * Creates a context menu for chat cells.
     * 
     * @param cell The cell to attach the menu to
     * @return The configured context menu
     */
    private ContextMenu createContextMenu(ChatListCell cell) {
        ContextMenu menu = new ContextMenu();
        
        MenuItem renameItem = createRenameMenuItem(cell);
        MenuItem deleteItem = createDeleteMenuItem(cell);
        
        menu.getItems().addAll(renameItem, new SeparatorMenuItem(), deleteItem);
        return menu;
    }
    
    /**
     * Creates the rename menu item.
     * 
     * @param cell The cell containing the chat
     * @return The configured menu item
     */
    private MenuItem createRenameMenuItem(ChatListCell cell) {
        MenuItem item = new MenuItem("Rename");
        item.setOnAction(event -> {
            Chat chat = cell.getItem();
            if (chat != null && renameCallback != null) {
                renameCallback.onAction(chat);
            }
        });
        return item;
    }
    
    /**
     * Creates the delete menu item.
     * 
     * @param cell The cell containing the chat
     * @return The configured menu item
     */
    private MenuItem createDeleteMenuItem(ChatListCell cell) {
        MenuItem item = new MenuItem("Delete");
        item.setOnAction(event -> {
            Chat chat = cell.getItem();
            if (chat != null && deleteCallback != null) {
                deleteCallback.onAction(chat);
            }
        });
        return item;
    }
    
    /**
     * Custom ListCell implementation for chat display.
     */
    private static class ChatListCell extends ListCell<Chat> {
        @Override
        protected void updateItem(Chat chat, boolean empty) {
            super.updateItem(chat, empty);
            
            if (empty || chat == null) {
                clearCell();
            } else {
                displayChat(chat);
            }
        }
        
        private void clearCell() {
            setText(null);
            setGraphic(null);
        }
        
        private void displayChat(Chat chat) {
            VBox container = createChatContainer(chat);
            setGraphic(container);
        }
        
        /**
         * Creates the visual container for a chat.
         * 
         * @param chat The chat to display
         * @return The configured container
         */
        private VBox createChatContainer(Chat chat) {
            VBox container = new VBox(CELL_SPACING);
            container.setPadding(new Insets(CELL_PADDING_TOP_BOTTOM, CELL_PADDING_LEFT_RIGHT,
                                           CELL_PADDING_TOP_BOTTOM, CELL_PADDING_LEFT_RIGHT));
            
            HBox header = createChatHeader(chat);
            container.getChildren().add(header);
            
            // Add model label if available
            if (hasValidModel(chat)) {
                Label modelLabel = createModelLabel(chat.getLlmModelName());
                container.getChildren().add(modelLabel);
            }
            
            return container;
        }
        
        /**
         * Creates the header row with icon and name.
         * 
         * @param chat The chat to display
         * @return The configured header
         */
        private HBox createChatHeader(Chat chat) {
            HBox header = new HBox(HEADER_SPACING);
            header.setAlignment(Pos.CENTER_LEFT);
            
            Label icon = createIconLabel();
            Label name = createNameLabel(chat.getName());
            
            header.getChildren().addAll(icon, name);
            return header;
        }
        
        /**
         * Creates the chat icon label.
         * 
         * @return The configured icon label
         */
        private Label createIconLabel() {
            Label icon = new Label(CHAT_ICON);
            icon.setStyle(ICON_STYLE);
            return icon;
        }
        
        /**
         * Creates the chat name label.
         * 
         * @param name The chat name
         * @return The configured name label
         */
        private Label createNameLabel(String name) {
            Label nameLabel = new Label(name);
            nameLabel.setStyle(NAME_STYLE);
            return nameLabel;
        }
        
        /**
         * Creates the model name label.
         * 
         * @param modelName The model name
         * @return The configured model label
         */
        private Label createModelLabel(String modelName) {
            Label modelLabel = new Label(modelName);
            modelLabel.setStyle(MODEL_STYLE);
            return modelLabel;
        }
        
        /**
         * Checks if the chat has a valid model name.
         * 
         * @param chat The chat to check
         * @return true if the model name is valid
         */
        private boolean hasValidModel(Chat chat) {
            return chat.getLlmModelName() != null && !chat.getLlmModelName().isEmpty();
        }
    }
}
