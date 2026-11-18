package com.brunorozendo.mcpclientgui.model;

/**
 * The LLM providers the application can talk to.
 *
 * The provider is derived from the stored model name: {@code anthropic:<model id>} is an Anthropic
 * model, everything else is an Ollama model (stored either as {@code ollama:<name>} or, as the
 * Ollama tag fetch writes them, as the bare tag name).
 */
public enum LlmProvider {
    OLLAMA("ollama:"),
    ANTHROPIC("anthropic:");

    /** The Anthropic model id the application uses by default. */
    public static final String DEFAULT_ANTHROPIC_MODEL_ID = "claude-opus-5-5";

    /** The stored (prefixed) name of the default Anthropic model. */
    public static final String DEFAULT_ANTHROPIC_MODEL_NAME = ANTHROPIC.prefix + DEFAULT_ANTHROPIC_MODEL_ID;

    private final String prefix;

    LlmProvider(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Gets the prefix that marks a stored model name as belonging to this provider.
     *
     * @return The prefix, including the trailing colon
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Determines the provider of a stored model name.
     *
     * @param fullModelName The stored model name (may be null)
     * @return {@link #ANTHROPIC} for {@code anthropic:} names, otherwise {@link #OLLAMA}
     */
    public static LlmProvider of(String fullModelName) {
        if (fullModelName != null && fullModelName.startsWith(ANTHROPIC.prefix)) {
            return ANTHROPIC;
        }
        return OLLAMA;
    }

    /**
     * Strips the provider prefix and returns the name the provider's API expects.
     *
     * @param fullModelName The stored model name
     * @return The model name without its provider prefix
     */
    public static String apiModelName(String fullModelName) {
        LlmProvider provider = of(fullModelName);
        if (fullModelName.startsWith(provider.prefix)) {
            return fullModelName.substring(provider.prefix.length());
        }
        return fullModelName;
    }

    /**
     * Builds the stored name of an Anthropic model.
     *
     * @param modelId The Anthropic model id (e.g. {@code claude-opus-5-5})
     * @return The prefixed name (e.g. {@code anthropic:claude-opus-5-5})
     */
    public static String anthropicModelName(String modelId) {
        return ANTHROPIC.prefix + modelId;
    }
}
