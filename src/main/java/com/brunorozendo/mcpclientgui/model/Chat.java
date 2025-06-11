package com.brunorozendo.mcpclientgui.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Chat {
    private Long id;
    private String name;
    private ObservableList<Message> messages;
    
    public Chat(String name) {
        this.name = name;
        this.messages = FXCollections.observableArrayList();
    }
    
    public Chat(Long id, String name) {
        this.id = id;
        this.name = name;
        this.messages = FXCollections.observableArrayList();
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public ObservableList<Message> getMessages() {
        return messages;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
