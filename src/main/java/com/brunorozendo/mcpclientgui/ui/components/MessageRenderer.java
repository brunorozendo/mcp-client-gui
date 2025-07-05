package com.brunorozendo.mcpclientgui.ui.components;

import com.brunorozendo.mcpclientgui.model.Message;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the rendering of chat messages with markdown support and styling using WebView.
 * This class is responsible for converting message content to visual components.
 */
public class MessageRenderer {
    private static final Logger logger = LoggerFactory.getLogger(MessageRenderer.class);

    // Layout constants
    private static final int BUBBLE_PADDING = 10;
    private static final int BUBBLE_MAX_WIDTH = 600;
    private static final int MESSAGE_SPACING = 5;
    
    // Format patterns
    private static final String TIME_FORMAT = "HH:mm";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT);
    
    // Markdown processing
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    
    // HTML template for messages
    private static final String HTML_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    background-color: white;
                    margin: 0;
                    padding: 0;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Helvetica Neue", Helvetica, Arial, sans-serif;
                    font-size: 14px;
                    line-height: 1.5;
                    color: #000000;
                    background-color: white;
                    padding: 8px;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    -webkit-user-select: text;
                    user-select: text;
                }
                .message-bubble {
                    padding: 10px 15px;
                    border-radius: 18px;
                    max-width: 100%%;
                    display: inline-block;
                    word-wrap: break-word;
                }
                .user-message {
                    background-color: #e7edf4;
                    color: #0d141c !important;
                    margin-left: auto;
                    text-align: left;
                }
                .user-message * {
                    color: #0d141c !important;
                }
                .ai-message {
                    background-color: #e7edf4;
                    color: #0d141c !important;
                    margin-right: auto;
                    text-align: left;
                }
                .ai-message * {
                    color: #0d141c !important;
                }
                .message-container {
                    display: flex;
                    margin-bottom: 4px;
                }
                .message-container.user {
                    justify-content: flex-end;
                }
                .message-container.ai {
                    justify-content: flex-start;
                }
                /* Markdown styles */
                h1, h2, h3, h4, h5, h6 {
                    margin-top: 12px;
                    margin-bottom: 8px;
                    font-weight: 600;
                    color: inherit !important;
                }
                h1 { font-size: 1.5em; }
                h2 { font-size: 1.3em; }
                h3 { font-size: 1.1em; }
                h4, h5, h6 { font-size: 1em; }
                p {
                    margin-bottom: 8px;
                    color: inherit !important;
                }
                p:last-child {
                    margin-bottom: 0;
                }
                code {
                    font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
                    font-size: 0.9em;
                    padding: 2px 4px;
                    border-radius: 3px;
                    color: inherit !important;
                }
                .user-message code {
                    background-color: rgba(255, 255, 255, 0.2);
                }
                .ai-message code {
                    background-color: rgba(0, 0, 0, 0.05);
                }
                pre {
                    margin: 8px 0;
                    padding: 12px;
                    border-radius: 6px;
                    overflow-x: auto;
                    line-height: 1.4;
                }
                .user-message pre {
                    background-color: rgba(255, 255, 255, 0.1);
                }
                .ai-message pre {
                    background-color: rgba(0, 0, 0, 0.03);
                }
                pre code {
                    background-color: transparent;
                    padding: 0;
                    font-size: 0.9em;
                    color: inherit !important;
                }
                ul, ol {
                    margin: 8px 0;
                    padding-left: 24px;
                    color: inherit !important;
                }
                li {
                    margin-bottom: 4px;
                    color: inherit !important;
                }
                blockquote {
                    margin: 8px 0;
                    padding-left: 16px;
                    border-left: 4px solid;
                    color: inherit !important;
                }
                .user-message blockquote {
                    border-left-color: rgba(255, 255, 255, 0.5);
                }
                .ai-message blockquote {
                    border-left-color: rgba(0, 0, 0, 0.2);
                }
                table {
                    margin: 8px 0;
                    border-collapse: collapse;
                    width: 100%%;
                }
                th, td {
                    padding: 8px;
                    text-align: left;
                    border: 1px solid;
                    color: inherit !important;
                }
                .user-message th, .user-message td {
                    border-color: rgba(255, 255, 255, 0.3);
                }
                .ai-message th, .ai-message td {
                    border-color: rgba(0, 0, 0, 0.1);
                }
                th {
                    font-weight: 600;
                }
                .user-message th {
                    background-color: rgba(255, 255, 255, 0.1);
                }
                .ai-message th {
                    background-color: rgba(0, 0, 0, 0.03);
                }
                a {
                    text-decoration: underline;
                    color: inherit !important;
                }
                .user-message a {
                    color: #0d141c !important;
                }
                .ai-message a {
                    color: #3d98f4 !important;
                }
                a:hover {
                    opacity: 0.8;
                }
                hr {
                    margin: 12px 0;
                    border: none;
                    height: 1px;
                }
                .user-message hr {
                    background-color: rgba(255, 255, 255, 0.3);
                }
                .ai-message hr {
                    background-color: rgba(0, 0, 0, 0.1);
                }
                img {
                    max-width: 100%%;
                    height: auto;
                    border-radius: 6px;
                    margin: 8px 0;
                }
                /* Ensure text is always visible */
                strong, b {
                    font-weight: bold;
                    color: inherit !important;
                }
                em, i {
                    font-style: italic;
                    color: inherit !important;
                }
                /* Selection styles */
                ::selection {
                    background-color: rgba(52, 152, 219, 0.3);
                }
                ::-moz-selection {
                    background-color: rgba(52, 152, 219, 0.3);
                }
                /* Debug - ensure visibility */
                body:empty::after {
                    content: "No content to display";
                    color: #999;
                }
            </style>
        </head>
        <body>
            <div class="message-container %s">
                <div class="message-bubble %s">
                    %s
                </div>
            </div>
        </body>
        </html>
        """;
    
    // Store all messages HTML for continuous display
    private final List<String> messageHtmlHistory;
    
    public MessageRenderer() {
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .build();
        this.messageHtmlHistory = new ArrayList<>();
    }
    
    /**
     * Creates a custom list cell for displaying messages.
     * 
     * @return A new ListCell configured for message display
     */
    public ListCell<Message> createMessageCell() {
        return new MessageListCell();
    }
    
    /**
     * Custom ListCell implementation for rendering messages.
     */
    private class MessageListCell extends ListCell<Message> {
        private final WebView webView;
        private final WebEngine webEngine;
        private final Label timeLabel;
        private final VBox container;
        
        public MessageListCell() {
            // Initialize WebView
            webView = new WebView();
            webEngine = webView.getEngine();
            
            // Configure WebView
            webView.setContextMenuEnabled(true);
            webView.setPrefHeight(100); // Initial height, will be adjusted
            webView.setMaxWidth(BUBBLE_MAX_WIDTH);
            
            // Set WebView background to white
            webView.setStyle("-fx-background-color: white;");
            
            // Enable JavaScript for proper rendering
            webEngine.setJavaScriptEnabled(true);
            
            // Time label
            timeLabel = new Label();
            timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
            
            // Container
            container = new VBox(MESSAGE_SPACING);
            container.setPadding(new Insets(BUBBLE_PADDING));
            
            // Make cell transparent
            setStyle("-fx-background-color: transparent;");
        }
        
        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);
            
            if (empty || message == null) {
                clearCell();
            } else {
                displayMessage(message);
            }
        }
        
        private void clearCell() {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: transparent;");
        }
        
        private void displayMessage(Message message) {
            // Set alignment based on message sender
            HBox messageBox = new HBox();
            if (message.isFromUser()) {
                messageBox.setAlignment(Pos.CENTER_RIGHT);
                container.setAlignment(Pos.CENTER_RIGHT);
            } else {
                messageBox.setAlignment(Pos.CENTER_LEFT);
                container.setAlignment(Pos.CENTER_LEFT);
            }
            
            // Render message content
            String html = renderMessageToHtml(message);
            
            // Debug: Log the HTML content
            logger.info("Rendering HTML: " + html.substring(0, Math.min(html.length(), 200)) + "...");
            
            webEngine.loadContent(html, "text/html");
            
            // Adjust WebView height based on content
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    Platform.runLater(() -> {
                        try {
                            // Get document height
                            Object result = webEngine.executeScript(
                                "Math.max(document.body.scrollHeight, document.body.offsetHeight, " +
                                "document.documentElement.clientHeight, document.documentElement.scrollHeight, " +
                                "document.documentElement.offsetHeight)"
                            );
                            if (result instanceof Number) {
                                double height = ((Number) result).doubleValue();
                                webView.setPrefHeight(height + 20); // Add some padding
                            }
                        } catch (Exception e) {
                            System.err.println("Error calculating WebView height: " + e.getMessage());
                            webView.setPrefHeight(150); // Fallback height
                        }
                    });
                }
            });
            
            // Update time label
            String timeText = message.getTimestamp().format(TIME_FORMATTER);
            timeLabel.setText(timeText);
            
            // Add to container
            messageBox.getChildren().add(webView);
            container.getChildren().clear();
            container.getChildren().addAll(messageBox, timeLabel);
            
            setGraphic(container);
        }
    }
    
    /**
     * Renders a message to HTML with markdown support.
     * 
     * @param message The message to render
     * @return HTML string
     */
    private String renderMessageToHtml(Message message) {
        String content = message.getContent();
        boolean isUserMessage = message.isFromUser();
        
        // Ensure content is not null or empty
        if (content == null || content.trim().isEmpty()) {
            content = "​"; // Zero-width space to ensure bubble renders
        }
        
        // Parse markdown to HTML
        Node document = markdownParser.parse(content);
        String htmlContent = htmlRenderer.render(document);
        
        // Create full HTML with styling
        String containerClass = isUserMessage ? "user" : "ai";
        String bubbleClass = isUserMessage ? "user-message" : "ai-message";
        
        return String.format(HTML_TEMPLATE, containerClass, bubbleClass, htmlContent);
    }
}
