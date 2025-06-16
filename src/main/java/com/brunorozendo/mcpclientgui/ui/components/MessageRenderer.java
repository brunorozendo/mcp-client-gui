package com.brunorozendo.mcpclientgui.ui.components;

import com.brunorozendo.mcpclientgui.model.Message;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the rendering of chat messages with markdown support and styling.
 * This class is responsible for converting message content to visual components.
 */
public class MessageRenderer {
    
    // Style constants
    private static final String USER_BUBBLE_STYLE = 
        "-fx-background-color: #3d98f4; -fx-background-radius: 18px;";
    private static final String AI_BUBBLE_STYLE = 
        "-fx-background-color: #e7edf4; -fx-background-radius: 18px;";
    private static final String USER_TEXT_COLOR = "white";
    private static final String AI_TEXT_COLOR = "#0d141c";
    private static final String TIME_LABEL_STYLE = 
        "-fx-font-size: 11px; -fx-text-fill: #666666;";
    
    // Layout constants
    private static final int BUBBLE_PADDING = 10;
    private static final int BUBBLE_HORIZONTAL_PADDING = 15;
    private static final int BUBBLE_MAX_WIDTH = 400;
    private static final int MESSAGE_SPACING = 5;
    
    // Format patterns
    private static final String TIME_FORMAT = "HH:mm";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT);
    
    // Markdown processing
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    
    public MessageRenderer() {
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
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
            setStyle("");
        }
        
        private void displayMessage(Message message) {
            VBox messageContainer = createMessageContainer(message);
            setGraphic(messageContainer);
        }
    }
    
    /**
     * Creates the complete visual container for a message.
     * 
     * @param message The message to render
     * @return A VBox containing the styled message
     */
    private VBox createMessageContainer(Message message) {
        VBox container = new VBox(MESSAGE_SPACING);
        container.setPadding(new Insets(BUBBLE_PADDING));
        
        HBox bubbleBox = createMessageBubble(message);
        Label timeLabel = createTimeLabel(message);
        
        applyAlignment(container, bubbleBox, timeLabel, message.isFromUser());
        
        container.getChildren().addAll(bubbleBox, timeLabel);
        return container;
    }
    
    /**
     * Creates the message bubble with formatted content.
     * 
     * @param message The message to display
     * @return An HBox containing the styled message bubble
     */
    private HBox createMessageBubble(Message message) {
        HBox bubbleContainer = new HBox();
        TextFlow textFlow = renderMessageContent(message);
        bubbleContainer.getChildren().add(textFlow);
        return bubbleContainer;
    }
    
    /**
     * Renders the message content with markdown support.
     * 
     * @param message The message containing the content to render
     * @return A TextFlow with formatted content
     */
    private TextFlow renderMessageContent(Message message) {
        String content = message.getContent();
        boolean isUserMessage = message.isFromUser();
        
        TextFlow textFlow = new TextFlow();
        textFlow.setPadding(new Insets(BUBBLE_PADDING, BUBBLE_HORIZONTAL_PADDING, 
                                      BUBBLE_PADDING, BUBBLE_HORIZONTAL_PADDING));
        textFlow.setMaxWidth(BUBBLE_MAX_WIDTH);
        
        String bubbleStyle = isUserMessage ? USER_BUBBLE_STYLE : AI_BUBBLE_STYLE;
        textFlow.setStyle(bubbleStyle);
        
        String textColor = isUserMessage ? USER_TEXT_COLOR : AI_TEXT_COLOR;
        
        // Parse and render markdown
        Node document = markdownParser.parse(content);
        String html = htmlRenderer.render(document);
        
        addFormattedContent(textFlow, html, textColor);
        
        return textFlow;
    }
    
    /**
     * Creates a time label for the message.
     * 
     * @param message The message to get the timestamp from
     * @return A styled label showing the message time
     */
    private Label createTimeLabel(Message message) {
        String timeText = message.getTimestamp().format(TIME_FORMATTER);
        Label timeLabel = new Label(timeText);
        timeLabel.setStyle(TIME_LABEL_STYLE);
        return timeLabel;
    }
    
    /**
     * Applies proper alignment based on message sender.
     * 
     * @param container The main container
     * @param bubbleBox The message bubble box
     * @param timeLabel The time label
     * @param isFromUser Whether the message is from the user
     */
    private void applyAlignment(VBox container, HBox bubbleBox, Label timeLabel, boolean isFromUser) {
        Pos alignment = isFromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
        container.setAlignment(alignment);
        bubbleBox.setAlignment(alignment);
        timeLabel.setAlignment(alignment);
    }
    
    /**
     * Processes HTML content and adds formatted text to the TextFlow.
     * 
     * @param textFlow The TextFlow to add content to
     * @param html The HTML content to process
     * @param baseColor The base text color
     */
    private void addFormattedContent(TextFlow textFlow, String html, String baseColor) {
        // Remove HTML tags while preserving structure
        String processedContent = processHtml(html);
        
        // Create styled text nodes
        if (processedContent.isEmpty()) {
            // Fallback for empty content
            String plainText = html.replaceAll("<[^>]*>", "").trim();
            if (!plainText.isEmpty()) {
                Text text = new Text(plainText);
                text.setStyle("-fx-fill: " + baseColor + ";");
                textFlow.getChildren().add(text);
            }
        } else {
            parseAndStyleContent(processedContent, textFlow, baseColor);
        }
    }
    
    /**
     * Processes HTML content to extract text with formatting markers.
     * 
     * @param html The HTML to process
     * @return Processed text with formatting markers
     */
    private String processHtml(String html) {
        // Handle paragraphs
        String content = html.replaceAll("</p>", "\n")
                            .replaceAll("<p>", "");
        
        // Convert formatting tags to markers
        content = content.replaceAll("<strong>", "§BOLD§")
                        .replaceAll("</strong>", "§/BOLD§")
                        .replaceAll("<em>", "§ITALIC§")
                        .replaceAll("</em>", "§/ITALIC§")
                        .replaceAll("<code>", "§CODE§")
                        .replaceAll("</code>", "§/CODE§");
        
        // Remove remaining HTML tags
        content = content.replaceAll("<[^>]*>", "");
        
        // Clean up extra whitespace
        content = content.trim().replaceAll("\n\n+", "\n\n");
        
        return content;
    }
    
    /**
     * Parses content with formatting markers and creates styled Text nodes.
     * 
     * @param content The content with formatting markers
     * @param textFlow The TextFlow to add nodes to
     * @param baseColor The base text color
     */
    private void parseAndStyleContent(String content, TextFlow textFlow, String baseColor) {
        Pattern pattern = Pattern.compile("(§BOLD§|§/BOLD§|§ITALIC§|§/ITALIC§|§CODE§|§/CODE§)");
        Matcher matcher = pattern.matcher(content);
        
        int lastEnd = 0;
        boolean isBold = false;
        boolean isItalic = false;
        boolean isCode = false;
        
        while (matcher.find()) {
            // Add text before the marker
            if (matcher.start() > lastEnd) {
                String text = content.substring(lastEnd, matcher.start());
                if (!text.isEmpty()) {
                    addStyledText(textFlow, text, baseColor, isBold, isItalic, isCode);
                }
            }
            
            // Process the marker
            String marker = matcher.group();
            switch (marker) {
                case "§BOLD§": isBold = true; break;
                case "§/BOLD§": isBold = false; break;
                case "§ITALIC§": isItalic = true; break;
                case "§/ITALIC§": isItalic = false; break;
                case "§CODE§": isCode = true; break;
                case "§/CODE§": isCode = false; break;
            }
            
            lastEnd = matcher.end();
        }
        
        // Add remaining text
        if (lastEnd < content.length()) {
            String remainingText = content.substring(lastEnd);
            if (!remainingText.isEmpty()) {
                addStyledText(textFlow, remainingText, baseColor, isBold, isItalic, isCode);
            }
        }
    }
    
    /**
     * Adds a styled Text node to the TextFlow.
     * 
     * @param textFlow The TextFlow to add to
     * @param content The text content
     * @param baseColor The base color
     * @param isBold Whether the text should be bold
     * @param isItalic Whether the text should be italic
     * @param isCode Whether the text should be styled as code
     */
    private void addStyledText(TextFlow textFlow, String content, String baseColor, 
                              boolean isBold, boolean isItalic, boolean isCode) {
        Text text = new Text(content);
        
        StringBuilder style = new StringBuilder("-fx-fill: " + baseColor + ";");
        
        if (isBold) {
            style.append(" -fx-font-weight: bold;");
        }
        if (isItalic) {
            style.append(" -fx-font-style: italic;");
        }
        if (isCode) {
            style.append(" -fx-font-family: monospace;");
        }
        
        text.setStyle(style.toString());
        textFlow.getChildren().add(text);
    }
}
