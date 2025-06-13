package com.brunorozendo.mcpclientgui.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Chat {
    private Long id;
    private String name;
    private String llmModelName;
    private ObservableList<Message> messages;
    
    public Chat(String name) {
        this.name = name;
        this.messages = FXCollections.observableArrayList();
    }
    
    public Chat(String name, String llmModelName) {
        this.name = name;
        this.llmModelName = llmModelName;
        this.messages = FXCollections.observableArrayList();
    }
    
    public Chat(Long id, String name) {
        this.id = id;
        this.name = name;
        this.messages = FXCollections.observableArrayList();
    }
    
    public Chat(Long id, String name, String llmModelName) {
        this.id = id;
        this.name = name;
        this.llmModelName = llmModelName;
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
    
    public String getLlmModelName() {
        return llmModelName;
    }
    
    public void setLlmModelName(String llmModelName) {
        this.llmModelName = llmModelName;
    }
    
    public ObservableList<Message> getMessages() {
        return messages;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
