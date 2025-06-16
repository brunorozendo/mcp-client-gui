package com.brunorozendo.mcpclientgui.ui.handlers;

import com.brunorozendo.mcpclientgui.model.Chat;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import java.util.Optional;

/**
 * Utility class for displaying various dialogs to the user.
 * Centralizes all dialog creation and display logic.
 */
public class DialogHelper {
    
    // Dialog titles
    private static final String RENAME_TITLE = "Rename Chat";
    private static final String DELETE_TITLE = "Delete Chat";
    private static final String ERROR_TITLE = "Error";
    private static final String WARNING_TITLE = "Warning";
    private static final String NOT_CONFIGURED_TITLE = "Not Configured";
    
    // Dialog messages
    private static final String RENAME_PROMPT = "Enter new name:";
    private static final String DELETE_CONFIRMATION_FORMAT = 
        "Are you sure you want to delete '%s'? This action cannot be undone.";
    private static final String NOT_CONFIGURED_MESSAGE = 
        "Please configure the LLM model and MCP settings first by clicking the Settings button.";
    
    /**
     * Shows a dialog to rename a chat.
     * 
     * @param chat The chat to rename
     * @return An Optional containing the new name if provided, empty otherwise
     */
    public static Optional<String> showRenameDialog(Chat chat) {
        TextInputDialog dialog = new TextInputDialog(chat.getName());
        dialog.setTitle(RENAME_TITLE);
        dialog.setHeaderText(null);
        dialog.setContentText(RENAME_PROMPT);
        
        return dialog.showAndWait()
                    .map(String::trim)
                    .filter(name -> !name.isEmpty());
    }
    
    /**
     * Shows a confirmation dialog for deleting a chat.
     * 
     * @param chat The chat to be deleted
     * @return true if the user confirmed deletion, false otherwise
     */
    public static boolean showDeleteConfirmation(Chat chat) {
        Alert alert = createAlert(AlertType.CONFIRMATION, DELETE_TITLE);
        alert.setContentText(String.format(DELETE_CONFIRMATION_FORMAT, chat.getName()));
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * Shows an error dialog with the specified message.
     * 
     * @param message The error message to display
     */
    public static void showError(String message) {
        showError(ERROR_TITLE, message);
    }
    
    /**
     * Shows an error dialog with the specified title and message.
     * 
     * @param title The dialog title
     * @param message The error message to display
     */
    public static void showError(String title, String message) {
        Alert alert = createAlert(AlertType.ERROR, title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Shows a warning dialog indicating the application is not configured.
     */
    public static void showNotConfiguredWarning() {
        Alert alert = createAlert(AlertType.WARNING, NOT_CONFIGURED_TITLE);
        alert.setContentText(NOT_CONFIGURED_MESSAGE);
        alert.showAndWait();
    }
    
    /**
     * Shows an information dialog with the specified message.
     * 
     * @param title The dialog title
     * @param message The information message to display
     */
    public static void showInformation(String title, String message) {
        Alert alert = createAlert(AlertType.INFORMATION, title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Shows a confirmation dialog with custom title and message.
     * 
     * @param title The dialog title
     * @param message The confirmation message
     * @return true if the user clicked OK, false otherwise
     */
    public static boolean showConfirmation(String title, String message) {
        Alert alert = createAlert(AlertType.CONFIRMATION, title);
        alert.setContentText(message);
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * Creates a basic alert with common settings.
     * 
     * @param type The alert type
     * @param title The alert title
     * @return The configured alert
     */
    private static Alert createAlert(AlertType type, String title) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert;
    }
}
