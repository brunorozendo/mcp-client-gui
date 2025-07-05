package com.brunorozendo.mcpclientgui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data models for interacting with the Ollama API.
 * 
 * This class contains Java records that map to the JSON structures used by Ollama's
 * chat completion API. These models ensure type-safe serialization/deserialization
 * when communicating with Ollama services.
 * 
 * The API follows a similar structure to OpenAI's chat completion API, with support
 * for tool/function calling capabilities.
 */
public class OllamaApi {

    // Constants for message roles
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
    
    // Constants for tool types
    public static final String TOOL_TYPE_FUNCTION = "function";
    
    // Constants for JSON schema types
    public static final String TYPE_OBJECT = "object";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_ARRAY = "array";

    /**
     * Represents a message in a chat conversation.
     * 
     * Messages can be from different roles:
     * - system: Initial instructions to the model
     * - user: Messages from the human user
     * - assistant: Responses from the AI model
     * - tool: Results from tool/function calls
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("images") List<String> images,
            @JsonProperty("tool_calls") List<ToolCall> tool_calls
    ) {
        /**
         * Creates a simple text message without images or tool calls.
         * 
         * @param role The message role (system, user, assistant, or tool)
         * @param content The text content of the message
         */
        public Message(String role, String content) {
            this(role, content, null, null);
        }
        
        /**
         * Validates that the message has required fields.
         * 
         * @throws NullPointerException if role is null
         */
        public Message {
            Objects.requireNonNull(role, "Message role cannot be null");
        }
        
        /**
         * Checks if this message contains tool calls.
         * 
         * @return True if the message has tool calls
         */
        public boolean hasToolCalls() {
            return tool_calls != null && !tool_calls.isEmpty();
        }
        
        /**
         * Checks if this message contains images.
         * 
         * @return True if the message has images
         */
        public boolean hasImages() {
            return images != null && !images.isEmpty();
        }
    }

    /**
     * Represents a request from the LLM to call a tool/function.
     * 
     * Tool calls allow the model to request execution of external functions
     * to gather information or perform actions.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(
            @JsonProperty("function") FunctionCall function
    ) {
        /**
         * Validates that the tool call has required fields.
         * 
         * @throws NullPointerException if function is null
         */
        public ToolCall {
            Objects.requireNonNull(function, "Tool call must have a function");
        }
    }

    /**
     * Details of a function to be called.
     * 
     * Contains the function name and its arguments as key-value pairs.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCall(
            @JsonProperty("name") String name,
            @JsonProperty("arguments") Map<String, Object> arguments
    ) {
        /**
         * Validates that the function call has required fields.
         * 
         * @throws NullPointerException if name is null
         */
        public FunctionCall {
            Objects.requireNonNull(name, "Function name cannot be null");
        }
        
        /**
         * Gets an argument value by name.
         * 
         * @param key The argument name
         * @return The argument value, or null if not present
         */
        public Object getArgument(String key) {
            return arguments != null ? arguments.get(key) : null;
        }
        
        /**
         * Checks if the function has any arguments.
         * 
         * @return True if arguments are present
         */
        public boolean hasArguments() {
            return arguments != null && !arguments.isEmpty();
        }
    }

    /**
     * Defines a tool that the LLM can use.
     * 
     * Tools are functions that the model can call to perform actions or
     * retrieve information. Currently, only function-type tools are supported.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            @JsonProperty("type") String type,
            @JsonProperty("function") OllamaFunction function
    ) {
        /**
         * Creates a function-type tool.
         * 
         * @param function The function definition
         * @return A new tool instance
         */
        public static Tool function(OllamaFunction function) {
            return new Tool(TOOL_TYPE_FUNCTION, function);
        }
        
        /**
         * Validates that the tool has required fields.
         * 
         * @throws NullPointerException if type or function is null
         */
        public Tool {
            Objects.requireNonNull(type, "Tool type cannot be null");
            Objects.requireNonNull(function, "Tool function cannot be null");
        }
    }

    /**
     * Defines a function within a tool.
     * 
     * Contains the function's name, description, and parameter schema.
     * The parameter schema defines what arguments the function accepts.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OllamaFunction(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("parameters") JsonSchema parameters
    ) {
        /**
         * Validates that the function has required fields.
         * 
         * @throws NullPointerException if name is null
         */
        public OllamaFunction {
            Objects.requireNonNull(name, "Function name cannot be null");
        }
    }

    /**
     * Represents a JSON Schema definition.
     * 
     * Used to define the structure and constraints of function parameters.
     * Supports common JSON Schema features like types, properties, required fields,
     * enums, and nested schemas.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonSchema(
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("properties") Map<String, JsonSchema> properties,
            @JsonProperty("items") JsonSchema items,
            @JsonProperty("required") List<String> required,
            @JsonProperty("enum") List<Object> enumValues,
            @JsonProperty("format") String format
    ) {
        /**
         * Creates a simple schema with just type and description.
         * 
         * @param type The JSON type (string, number, object, array, etc.)
         * @param description Human-readable description
         */
        public JsonSchema(String type, String description) {
            this(type, description, null, null, null, null, null);
        }
        
        /**
         * Creates an object schema with properties.
         * 
         * @param description Description of the object
         * @param properties Map of property names to their schemas
         * @param required List of required property names
         * @return A new object schema
         */
        public static JsonSchema object(String description, 
                                      Map<String, JsonSchema> properties, 
                                      List<String> required) {
            return new JsonSchema(TYPE_OBJECT, description, properties, null, required, null, null);
        }
        
        /**
         * Creates an array schema.
         * 
         * @param description Description of the array
         * @param items Schema for array items
         * @return A new array schema
         */
        public static JsonSchema array(String description, JsonSchema items) {
            return new JsonSchema(TYPE_ARRAY, description, null, items, null, null, null);
        }
        
        /**
         * Creates a string schema with enum values.
         * 
         * @param description Description of the string
         * @param enumValues Allowed values
         * @return A new string enum schema
         */
        public static JsonSchema stringEnum(String description, List<Object> enumValues) {
            return new JsonSchema(TYPE_STRING, description, null, null, null, enumValues, null);
        }
    }

    /**
     * Request body for a chat completion API call.
     * 
     * Contains the model to use, conversation history, and optional parameters
     * like tools, streaming settings, and model options.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<Message> messages,
            @JsonProperty("stream") boolean stream,
            @JsonProperty("tools") List<Tool> tools,
            @JsonProperty("format") String format,
            @JsonProperty("options") Map<String, Object> options,
            @JsonProperty("keep_alive") String keep_alive
    ) {
        /**
         * Creates a standard non-streaming chat request.
         * 
         * @param model The model identifier (e.g., "llama2", "codellama")
         * @param messages The conversation history
         * @param stream Whether to stream the response
         * @param tools Available tools for the model to use
         */
        public ChatRequest(String model, List<Message> messages, boolean stream, List<Tool> tools) {
            this(model, messages, stream, tools, null, null, null);
        }
        
        /**
         * Creates a simple chat request without tools.
         * 
         * @param model The model identifier
         * @param messages The conversation history
         * @return A new chat request
         */
        public static ChatRequest simple(String model, List<Message> messages) {
            return new ChatRequest(model, messages, false, null);
        }

        /**
         * Validates that the request has required fields.
         * 
         * @throws NullPointerException if model or messages is null
         */
        public ChatRequest {
            Objects.requireNonNull(model, "Model name cannot be null");
            Objects.requireNonNull(messages, "Messages cannot be null");
        }
    }

    /**
     * Response from a chat completion API call.
     * 
     * Contains the model's response message and various performance metrics
     * about the generation process.
     */
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
    ) {
        /**
         * Gets the total generation time in seconds.
         * 
         * @return Generation time in seconds, or null if not available
         */
        public Double getTotalDurationSeconds() {
            return totalDuration != null ? totalDuration / 1_000_000_000.0 : null;
        }
        
        /**
         * Gets the tokens per second for prompt evaluation.
         * 
         * @return Prompt evaluation speed in tokens/sec, or null if not calculable
         */
        public Double getPromptTokensPerSecond() {
            if (promptEvalCount != null && promptEvalCount > 0 && 
                promptEvalDuration != null && promptEvalDuration > 0) {
                return promptEvalCount / (promptEvalDuration / 1_000_000_000.0);
            }
            return null;
        }
        
        /**
         * Gets the tokens per second for generation.
         * 
         * @return Generation speed in tokens/sec, or null if not calculable
         */
        public Double getGenerationTokensPerSecond() {
            if (evalCount != null && evalCount > 0 && 
                evalDuration != null && evalDuration > 0) {
                return evalCount / (evalDuration / 1_000_000_000.0);
            }
            return null;
        }
    }

    /**
     * Response from the /api/tags endpoint containing available models.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TagsResponse(
            @JsonProperty("models") List<ModelInfo> models
    ) {}

    /**
     * Information about a single model from the /api/tags endpoint.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelInfo(
            @JsonProperty("name") String name,
            @JsonProperty("model") String model,
            @JsonProperty("modified_at") String modifiedAt,
            @JsonProperty("size") Long size,
            @JsonProperty("digest") String digest,
            @JsonProperty("details") ModelDetails details
    ) {}

    /**
     * Detailed information about a model.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelDetails(
            @JsonProperty("parent_model") String parentModel,
            @JsonProperty("format") String format,
            @JsonProperty("family") String family,
            @JsonProperty("families") List<String> families,
            @JsonProperty("parameter_size") String parameterSize,
            @JsonProperty("quantization_level") String quantizationLevel
    ) {}
}
