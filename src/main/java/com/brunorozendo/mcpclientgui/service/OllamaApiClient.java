package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A client for interacting with the Ollama REST API.
 */
public class OllamaApiClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaApiClient.class);
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaApiClient(String baseUrl) {
        this.baseUrl = baseUrl;

        // Configure a reusable HttpClient with a connection timeout.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        // Configure a reusable ObjectMapper for JSON serialization/deserialization.
        this.objectMapper = new ObjectMapper()
                // Convert Java camelCase to JSON snake_case (e.g., toolCalls -> tool_calls)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                // Don't include null fields in the output JSON
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                // Don't fail if we try to serialize an empty Java object
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * Sends a chat request to the Ollama API and returns the response.
     *
     * @param request The chat request object.
     * @return The chat response from the API.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    public OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws Exception {
        String requestBody = objectMapper.writeValueAsString(request);
        logger.debug("Ollama Request to {}: {}", baseUrl + "/api/chat", requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5)) // Set a generous timeout for the LLM to respond
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Log response status and body for debugging
        logResponse(httpResponse);

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            return objectMapper.readValue(httpResponse.body(), OllamaApi.ChatResponse.class);
        } else {
            String errorMessage = "Ollama API request failed with status " + httpResponse.statusCode() +
                    ": " + httpResponse.body();
            logger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    private void logResponse(HttpResponse<String> httpResponse) {
        int statusCode = httpResponse.statusCode();
        String body = httpResponse.body();

        if (statusCode >= 200 && statusCode < 300) {
            logger.debug("Ollama Response Status: {}", statusCode);
            // Use TRACE for successful large bodies to avoid cluttering logs
            logger.trace("Ollama Response Body: {}", body);
        } else {
            logger.error("Ollama Error Response (Status {}): {}", statusCode, body);
        }
    }
}
