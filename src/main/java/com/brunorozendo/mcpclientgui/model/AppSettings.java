package com.brunorozendo.mcpclientgui.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-wide settings for the MCP Client GUI.
 * 
 * This class manages configuration for:
 * - Available Language Learning Models (LLMs)
 * - The default LLM model to use for new chats
 * - MCP (Model Context Protocol) configuration file location
 * - Ollama API endpoint URL
 */
public class AppSettings {
    
    // Default values
    private static final String DEFAULT_MODEL_NAME = "qwen3:14b";
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    
    private List<LlmModel> llmModels;
    private String defaultLlmModelName;
    private File mcpConfigFile;
    private String ollamaBaseUrl;
    
    /**
     * Creates new application settings with default values.
     * Initializes with a default LLM model and Ollama URL.
     */
    public AppSettings() {
        this.llmModels = new ArrayList<>();
        this.ollamaBaseUrl = DEFAULT_OLLAMA_URL;
        
        // Add a default model to ensure there's always at least one available
        LlmModel defaultModel = new LlmModel(DEFAULT_MODEL_NAME, true);
        this.llmModels.add(defaultModel);
        this.defaultLlmModelName = defaultModel.getName();
    }
    
    /**
     * Gets the list of available LLM models.
     * 
     * @return List of LLM models (never null)
     */
    public List<LlmModel> getLlmModels() {
        return llmModels;
    }
    
    /**
     * Sets the list of available LLM models.
     * 
     * @param llmModels List of models to set (cannot be null)
     */
    public void setLlmModels(List<LlmModel> llmModels) {
        this.llmModels = Objects.requireNonNull(llmModels, "LLM models list cannot be null");
    }
    
    /**
     * Gets the name of the default LLM model.
     * 
     * @return The default model name, or null if not set
     */
    public String getDefaultLlmModelName() {
        return defaultLlmModelName;
    }
    
    /**
     * Sets the name of the default LLM model.
     * 
     * @param defaultLlmModelName The model name to set as default
     */
    public void setDefaultLlmModelName(String defaultLlmModelName) {
        this.defaultLlmModelName = defaultLlmModelName;
    }
    
    /**
     * Gets the default LLM model object.
     * 
     * @return The default model, or null if not found
     */
    public LlmModel getDefaultLlmModel() {
        return findModelByName(defaultLlmModelName).orElse(null);
    }
    
    /**
     * Finds an LLM model by its name.
     * 
     * @param name The model name to search for
     * @return The model if found, or null
     */
    public LlmModel getLlmModelByName(String name) {
        return findModelByName(name).orElse(null);
    }
    
    /**
     * Finds an LLM model by its name, returning an Optional.
     * 
     * @param name The model name to search for
     * @return Optional containing the model if found
     */
    private Optional<LlmModel> findModelByName(String name) {
        if (name == null || llmModels == null) {
            return Optional.empty();
        }
        return llmModels.stream()
                .filter(model -> name.equals(model.getName()))
                .findFirst();
    }
    
    /**
     * Gets the MCP configuration file.
     * 
     * @return The MCP config file, or null if not set
     */
    public File getMcpConfigFile() {
        return mcpConfigFile;
    }
    
    /**
     * Sets the MCP configuration file.
     * 
     * @param mcpConfigFile The config file to set
     */
    public void setMcpConfigFile(File mcpConfigFile) {
        this.mcpConfigFile = mcpConfigFile;
    }
    
    /**
     * Gets the Ollama API base URL.
     * 
     * @return The Ollama URL
     */
    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }
    
    /**
     * Sets the Ollama API base URL.
     * 
     * @param ollamaBaseUrl The URL to set (e.g., "http://localhost:11434")
     */
    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }
    
    /**
     * Validates if the settings are complete and valid for use.
     * 
     * @return True if all required settings are present and valid
     */
    public boolean isValid() {
        return hasValidModels() && hasValidMcpConfig();
    }
    
    /**
     * Checks if the model configuration is valid.
     * 
     * @return True if models are configured properly
     */
    private boolean hasValidModels() {
        return llmModels != null && 
               !llmModels.isEmpty() && 
               defaultLlmModelName != null && 
               !defaultLlmModelName.trim().isEmpty();
    }
    
    /**
     * Checks if the MCP configuration is valid.
     * 
     * @return True if MCP config file exists
     */
    private boolean hasValidMcpConfig() {
        return mcpConfigFile != null && mcpConfigFile.exists();
    }
    
    /**
     * Represents a Language Learning Model configuration.
     * 
     * Each model has a name (e.g., "qwen3:14b") and can be marked as the default
     * model for new chats. Only one model should be marked as default at a time.
     */
    public static class LlmModel {
        private String name;
        private boolean isDefault;
        
        /**
         * Default constructor for serialization.
         */
        public LlmModel() {}
        
        /**
         * Creates a new LLM model configuration.
         * 
         * @param name The model name (e.g., "qwen3:14b")
         * @param isDefault Whether this is the default model
         */
        public LlmModel(String name, boolean isDefault) {
            this.name = Objects.requireNonNull(name, "Model name cannot be null");
            this.isDefault = isDefault;
        }
        
        /**
         * Gets the model name.
         * 
         * @return The model name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Sets the model name.
         * 
         * @param name The model name (cannot be null)
         */
        public void setName(String name) {
            this.name = Objects.requireNonNull(name, "Model name cannot be null");
        }
        
        /**
         * Checks if this is the default model.
         * 
         * @return True if this is the default model
         */
        public boolean isDefault() {
            return isDefault;
        }
        
        /**
         * Sets whether this is the default model.
         * 
         * @param isDefault True to make this the default model
         */
        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            LlmModel llmModel = (LlmModel) obj;
            return isDefault == llmModel.isDefault &&
                   Objects.equals(name, llmModel.name);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, isDefault);
        }
        
        @Override
        public String toString() {
            return name + (isDefault ? " (Default)" : "");
        }
    }
}
