package com.brunorozendo.mcpclientgui.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.models.ModelInfo;
import com.brunorozendo.mcpclientgui.model.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Owns the Anthropic SDK client for the application.
 *
 * The client is created lazily on first use (never during start-up), from the API key entered in
 * Settings or, when that is empty, from the SDK's own credential lookup
 * ({@code ANTHROPIC_API_KEY}, {@code ANTHROPIC_AUTH_TOKEN}, an {@code ant auth login} profile).
 *
 * The key is handed straight to the SDK builder and is not kept in any field, logged, or included in
 * an exception message or {@code toString()}.
 */
public class AnthropicService {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicService.class);

    // Environment variables through which the SDK's default lookup can find a credential.
    private static final List<String> CREDENTIAL_ENV_VARS = List.of(
        "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_PROFILE", "ANTHROPIC_FEDERATION_RULE_ID");

    private final Supplier<AnthropicClient> clientFactory;
    private final boolean apiKeyFromSettings;
    private volatile AnthropicClient client;

    /**
     * Creates a service that builds its client from the Settings key, or from the environment when the
     * key is empty.
     *
     * @param apiKeyFromSettings The key entered in Settings, or null/blank to use the SDK's environment lookup
     */
    public AnthropicService(String apiKeyFromSettings) {
        this(clientFactoryFor(apiKeyFromSettings), apiKeyFromSettings != null && !apiKeyFromSettings.isBlank());
    }

    /**
     * Creates a service around a custom client factory (tests point it at a local server).
     *
     * @param clientFactory Builds the client on first use
     * @param apiKeyFromSettings Whether the credential came from the Settings dialog (only affects messages)
     */
    public AnthropicService(Supplier<AnthropicClient> clientFactory, boolean apiKeyFromSettings) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "Client factory cannot be null");
        this.apiKeyFromSettings = apiKeyFromSettings;
    }

    private static Supplier<AnthropicClient> clientFactoryFor(String apiKeyFromSettings) {
        // Capture only what the factory needs; the key lives in the lambda and in the SDK client.
        String key = apiKeyFromSettings == null || apiKeyFromSettings.isBlank() ? null : apiKeyFromSettings.trim();
        if (key != null) {
            return () -> AnthropicOkHttpClient.builder().apiKey(key).build();
        }
        return AnthropicOkHttpClient::fromEnv;
    }

    /**
     * Gets the SDK client, creating it on first call. Call it off the JavaFX thread.
     *
     * @return The client
     */
    public AnthropicClient client() {
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = clientFactory.get();
                    client = current;
                    logger.info("Anthropic client created (credential source: {})",
                                apiKeyFromSettings ? "Settings" : "environment");
                }
            }
        }
        return current;
    }

    /**
     * Whether the credential is the key entered in Settings (rather than the SDK's environment lookup).
     *
     * @return true if a key was entered in Settings
     */
    public boolean isApiKeyFromSettings() {
        return apiKeyFromSettings;
    }

    /**
     * Cheap check, without building the client or touching the network, of whether a credential could be
     * available: a Settings key, the SDK's environment variables, or an {@code ant auth login} profile
     * directory. Only names are checked, never values.
     *
     * @return false when no credential source is present at all
     */
    public boolean mayHaveCredentials() {
        if (apiKeyFromSettings) {
            return true;
        }
        for (String name : CREDENTIAL_ENV_VARS) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return Files.isDirectory(Path.of(System.getProperty("user.home"), ".config", "anthropic"));
    }

    /**
     * Lists the models the credential can use, through the SDK's Models API. Makes network calls:
     * call it off the JavaFX thread. Errors propagate to the caller.
     *
     * @return Stored (prefixed) model names, e.g. {@code anthropic:claude-opus-5-5}
     */
    public List<String> listModelNames() {
        List<String> names = new ArrayList<>();
        for (ModelInfo model : client().models().list().autoPager()) {
            names.add(LlmProvider.anthropicModelName(model.id()));
        }
        return names;
    }

    /**
     * Closes the client, if one was created.
     */
    public void close() {
        AnthropicClient current = client;
        if (current != null) {
            current.close();
            client = null;
        }
    }
}
