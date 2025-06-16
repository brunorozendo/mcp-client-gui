package com.brunorozendo.mcpclientgui.service;

import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP client for interacting with the Ollama REST API.
 * 
 * This client handles:
 * - JSON serialization/deserialization between Java objects and API formats
 * - HTTP communication with configurable timeouts
 * - Error handling and logging
 * 
 * The client uses snake_case JSON formatting to match Ollama's API conventions
 * while allowing Java code to use standard camelCase naming.
 */
public class OllamaApiClient {
    private static final Logger logger = LoggerFactory.getLogger(OllamaApiClient.class);
    
    // API Configuration
    private static final String CHAT_ENDPOINT = "/api/chat";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    
    // Timeout Configuration
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5); // Generous timeout for LLM responses
    
    // HTTP Status Code Ranges
    private static final int STATUS_OK_MIN = 200;
    private static final int STATUS_OK_MAX = 299;
    
    // Client components
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Ollama API client.
     * 
     * @param baseUrl The base URL of the Ollama server (e.g., "http://localhost:11434")
     * @throws NullPointerException if baseUrl is null
     */
    public OllamaApiClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "Base URL cannot be null");
        this.httpClient = createHttpClient();
        this.objectMapper = createObjectMapper();
        
        logger.info("Initialized Ollama API client for: {}", baseUrl);
    }
    
    /**
     * Creates and configures the HTTP client.
     * 
     * @return Configured HttpClient instance
     */
    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT)
                .build();
    }
    
    /**
     * Creates and configures the Jackson ObjectMapper for JSON processing.
     * 
     * @return Configured ObjectMapper instance
     */
    private ObjectMapper createObjectMapper() {
        return new ObjectMapper()
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
     * This method handles the complete request/response cycle including:
     * - Serializing the request to JSON
     * - Sending the HTTP POST request
     * - Deserializing the response
     * - Error handling for failed requests
     *
     * @param request The chat request containing the model, messages, and optional tools
     * @return The chat response containing the model's reply
     * @throws IllegalArgumentException if request is null
     * @throws IOException if JSON serialization/deserialization fails
     * @throws InterruptedException if the HTTP request is interrupted
     * @throws RuntimeException if the API returns an error status
     */
    public OllamaApi.ChatResponse chat(OllamaApi.ChatRequest request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "Chat request cannot be null");
        
        // Serialize request to JSON
        String requestBody = serializeRequest(request);
        
        // Build HTTP request
        HttpRequest httpRequest = buildChatRequest(requestBody);
        
        // Send request and get response
        HttpResponse<String> httpResponse = sendRequest(httpRequest);
        
        // Process response
        return processResponse(httpResponse);
    }
    
    /**
     * Serializes a chat request to JSON.
     * 
     * @param request The request to serialize
     * @return JSON string representation
     * @throws IOException if serialization fails
     */
    private String serializeRequest(OllamaApi.ChatRequest request) throws IOException {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            logger.debug("Serialized chat request for model '{}' with {} messages", 
                        request.model(), 
                        request.messages() != null ? request.messages().size() : 0);
            logger.trace("Request body: {}", requestBody);
            return requestBody;
        } catch (IOException e) {
            logger.error("Failed to serialize chat request", e);
            throw new IOException("Failed to serialize chat request", e);
        }
    }
    
    /**
     * Builds an HTTP request for the chat endpoint.
     * 
     * @param requestBody The JSON request body
     * @return Configured HttpRequest
     */
    private HttpRequest buildChatRequest(String requestBody) {
        URI endpoint = URI.create(baseUrl + CHAT_ENDPOINT);
        
        return HttpRequest.newBuilder()
                .uri(endpoint)
                .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(REQUEST_TIMEOUT)
                .build();
    }
    
    /**
     * Sends an HTTP request and returns the response.
     * 
     * @param httpRequest The request to send
     * @return The HTTP response
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the request is interrupted
     */
    private HttpResponse<String> sendRequest(HttpRequest httpRequest) throws IOException, InterruptedException {
        logger.debug("Sending request to: {}", httpRequest.uri());
        
        try {
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            logger.error("I/O error during HTTP request to {}", httpRequest.uri(), e);
            throw new IOException("Failed to connect to Ollama API", e);
        } catch (InterruptedException e) {
            logger.error("HTTP request interrupted", e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw e;
        }
    }
    
    /**
     * Processes the HTTP response and returns the deserialized chat response.
     * 
     * @param httpResponse The HTTP response to process
     * @return The deserialized chat response
     * @throws IOException if deserialization fails
     * @throws RuntimeException if the response indicates an error
     */
    private OllamaApi.ChatResponse processResponse(HttpResponse<String> httpResponse) throws IOException {
        int statusCode = httpResponse.statusCode();
        String responseBody = httpResponse.body();
        
        logResponse(statusCode, responseBody);
        
        if (isSuccessful(statusCode)) {
            return deserializeResponse(responseBody);
        } else {
            throw createApiException(statusCode, responseBody);
        }
    }
    
    /**
     * Checks if an HTTP status code indicates success.
     * 
     * @param statusCode The HTTP status code
     * @return True if the status indicates success (2xx)
     */
    private boolean isSuccessful(int statusCode) {
        return statusCode >= STATUS_OK_MIN && statusCode <= STATUS_OK_MAX;
    }
    
    /**
     * Deserializes a JSON response to a ChatResponse object.
     * 
     * @param responseBody The JSON response body
     * @return The deserialized ChatResponse
     * @throws IOException if deserialization fails
     */
    private OllamaApi.ChatResponse deserializeResponse(String responseBody) throws IOException {
        try {
            OllamaApi.ChatResponse response = objectMapper.readValue(responseBody, OllamaApi.ChatResponse.class);
            logger.debug("Successfully received response from model '{}'", response.model());
            return response;
        } catch (IOException e) {
            logger.error("Failed to deserialize chat response", e);
            throw new IOException("Failed to parse Ollama API response", e);
        }
    }
    
    /**
     * Creates an exception for API errors.
     * 
     * @param statusCode The HTTP status code
     * @param responseBody The error response body
     * @return RuntimeException with error details
     */
    private RuntimeException createApiException(int statusCode, String responseBody) {
        String errorMessage = String.format(
            "Ollama API request failed with status %d: %s",
            statusCode,
            responseBody != null ? responseBody : "No error details provided"
        );
        
        logger.error(errorMessage);
        return new RuntimeException(errorMessage);
    }
    
    /**
     * Logs the HTTP response based on its status.
     * 
     * @param statusCode The HTTP status code
     * @param responseBody The response body
     */
    private void logResponse(int statusCode, String responseBody) {
        if (isSuccessful(statusCode)) {
            logger.debug("Received successful response (status: {})", statusCode);
            // Use TRACE level for large response bodies to avoid cluttering logs
            if (responseBody != null && responseBody.length() > 1000) {
                logger.trace("Response body ({} chars): {}", responseBody.length(), responseBody);
            } else {
                logger.debug("Response body: {}", responseBody);
            }
        } else {
            logger.error("Received error response (status: {}): {}", statusCode, responseBody);
        }
    }
    
    /**
     * Gets the base URL of the Ollama server.
     * 
     * @return The base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }
    
    /**
     * Tests the connection to the Ollama server.
     * 
     * @return True if the server is reachable, false otherwise
     */
    public boolean testConnection() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean isReachable = isSuccessful(response.statusCode());
            
            if (isReachable) {
                logger.info("Successfully connected to Ollama server at {}", baseUrl);
            } else {
                logger.warn("Ollama server at {} returned status: {}", baseUrl, response.statusCode());
            }
            
            return isReachable;
        } catch (Exception e) {
            logger.error("Failed to connect to Ollama server at {}", baseUrl, e);
            return false;
        }
    }
}
