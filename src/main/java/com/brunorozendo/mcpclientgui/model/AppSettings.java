package com.brunorozendo.mcpclientgui.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Model for storing application settings.
 */
public class AppSettings {
    private List<LlmModel> llmModels;
    private String defaultLlmModelName;
    private File mcpConfigFile;
    private String ollamaBaseUrl;
    
    public AppSettings() {
        this.llmModels = new ArrayList<>();
        // Add a default model
        LlmModel defaultModel = new LlmModel("qwen3:14b", true);
        this.llmModels.add(defaultModel);
        this.defaultLlmModelName = defaultModel.getName();
        this.ollamaBaseUrl = "http://localhost:11434";  // Default URL
    }
    
    public List<LlmModel> getLlmModels() {
        return llmModels;
    }
    
    public void setLlmModels(List<LlmModel> llmModels) {
        this.llmModels = llmModels;
    }
    
    public String getDefaultLlmModelName() {
        return defaultLlmModelName;
    }
    
    public void setDefaultLlmModelName(String defaultLlmModelName) {
        this.defaultLlmModelName = defaultLlmModelName;
    }
    
    public LlmModel getDefaultLlmModel() {
        return llmModels.stream()
                .filter(m -> m.getName().equals(defaultLlmModelName))
                .findFirst()
                .orElse(null);
    }
    
    public LlmModel getLlmModelByName(String name) {
        return llmModels.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElse(null);
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
        return llmModels != null && !llmModels.isEmpty() && 
               defaultLlmModelName != null && !defaultLlmModelName.trim().isEmpty() &&
               mcpConfigFile != null && mcpConfigFile.exists();
    }
    
    /**
     * Inner class representing an LLM model
     */
    public static class LlmModel {
        private String name;
        private boolean isDefault;
        
        public LlmModel() {}
        
        public LlmModel(String name, boolean isDefault) {
            this.name = name;
            this.isDefault = isDefault;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public boolean isDefault() {
            return isDefault;
        }
        
        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }
        
        @Override
        public String toString() {
            return name + (isDefault ? " (Default)" : "");
        }
    }
}
