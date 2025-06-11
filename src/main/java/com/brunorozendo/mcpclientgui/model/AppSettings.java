package com.brunorozendo.mcpclientgui.model;

import java.io.File;

/**
 * Model for storing application settings.
 */
public class AppSettings {
    private String llmModel;
    private File mcpConfigFile;
    private String ollamaBaseUrl;
    
    public AppSettings() {
        this.llmModel = "llama3.2";  // Default model
        this.ollamaBaseUrl = "http://localhost:11434";  // Default URL
    }
    
    public String getLlmModel() {
        return llmModel;
    }
    
    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }
    
    public File getMcpConfigFile() {
        return mcpConfigFile;
    }
    
    public void setMcpConfigFile(File mcpConfigFile) {
        this.mcpConfigFile = mcpConfigFile;
    }
    
    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }
    
    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }
    
    public boolean isValid() {
        return llmModel != null && !llmModel.trim().isEmpty() && 
               mcpConfigFile != null && mcpConfigFile.exists();
    }
}
