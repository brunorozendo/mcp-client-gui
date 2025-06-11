package com.brunorozendo.mcpclientgui.model;

import java.time.LocalDateTime;

public class Message {
    private Long id;
    private Long chatId;
    private String content;
    private boolean fromUser;
    private LocalDateTime timestamp;
    
    public Message(String content, boolean fromUser, LocalDateTime timestamp) {
        this.content = content;
        this.fromUser = fromUser;
        this.timestamp = timestamp;
    }
    
    public Message(Long id, Long chatId, String content, boolean fromUser, LocalDateTime timestamp) {
        this.id = id;
        this.chatId = chatId;
        this.content = content;
        this.fromUser = fromUser;
        this.timestamp = timestamp;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getChatId() {
        return chatId;
    }
    
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public boolean isFromUser() {
        return fromUser;
    }
    
    public void setFromUser(boolean fromUser) {
        this.fromUser = fromUser;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
