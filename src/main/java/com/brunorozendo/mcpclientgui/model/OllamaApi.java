package com.brunorozendo.mcpclientgui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Contains Java records that map to the JSON structures used by the Ollama API.
 * This ensures type-safe interaction with the Ollama service.
 */
public class OllamaApi {

    // A single message in a conversation, can be from 'system', 'user', 'assistant', or 'tool'.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("images") List<String> images,
            @JsonProperty("tool_calls") List<ToolCall> tool_calls
    ) {
        // Convenience constructor for simple text messages
        public Message(String role, String content) {
            this(role, content, null, null);
        }
    }

    // Represents a request from the LLM to call a tool.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(
            @JsonProperty("function") FunctionCall function
    ) {}

    // The details of the function to be called.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCall(
            @JsonProperty("name") String name,
            // Arguments are expected as a map, which Jackson deserializes from the JSON object.
            @JsonProperty("arguments") Map<String, Object> arguments
    ) {}

    // The definition of a tool that the LLM can use.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            @JsonProperty("type") String type, // Should always be "function"
            @JsonProperty("function") OllamaFunction function
    ) {}

    // The function definition within a tool, including its parameters.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OllamaFunction(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("parameters") JsonSchema parameters
    ) {}

    // Represents a JSON schema, used here to define the parameters of a tool's function.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonSchema(
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("properties") Map<String, JsonSchema> properties,
            @JsonProperty("items") JsonSchema items, // For arrays
            @JsonProperty("required") List<String> required,
            @JsonProperty("enum") List<Object> enumValues,
            @JsonProperty("format") String format
    ) {
        // Convenience constructor for a simple schema with just type and description.
        public JsonSchema(String type, String description) {
            this(type, description, null, null, null, null, null);
        }
    }

    // The main request body for a chat completion request to the Ollama API.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<Message> messages,
            @JsonProperty("stream") boolean stream,
            @JsonProperty("tools") List<Tool> tools,
            @JsonProperty("format") String format, // e.g., "json"
            @JsonProperty("options") Map<String, Object> options,
            @JsonProperty("keep_alive") String keep_alive
    ) {
        // Convenience constructor for a standard, non-streaming request.
        public ChatRequest(String model, List<Message> messages, boolean stream, List<Tool> tools) {
            this(model, messages, stream, tools, null, null, null);
        }
    }

    // The response body from a chat completion request.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatResponse(
            @JsonProperty("model") String model,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("message") Message message,
            @JsonProperty("done") boolean done,
            @JsonProperty("total_duration") Long totalDuration,
            @JsonProperty("load_duration") Long loadDuration,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("prompt_eval_duration") Long promptEvalDuration,
            @JsonProperty("eval_count") Integer evalCount,
            @JsonProperty("eval_duration") Long evalDuration,
            @JsonProperty("done_reason") String done_reason
    ) {}
}
