package com.brunorozendo.mcpclientgui.core.chat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.CredentialResolutionException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.service.AnthropicService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs one user turn against the Anthropic Messages API: the streamed request, the manual tool loop
 * over the MCP tools, stop-reason handling and error mapping.
 *
 * This class has no JavaFX dependency; {@link AnthropicChatController} connects it to the UI.
 */
public class AnthropicConversation {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicConversation.class);

    /** Streaming request, so a large output budget does not risk HTTP timeouts. */
    static final long MAX_TOKENS = 64_000L;

    /** Effort is set explicitly: Claude Opus 5.5 defaults to medium, and the choice should be visible. */
    static final BetaOutputConfig.Effort EFFORT = BetaOutputConfig.Effort.MEDIUM;

    /** Same guard as the Ollama loop (ChatController.MAX_CONVERSATION_TURNS). */
    static final int MAX_TOOL_ITERATIONS = 10;

    /** Server-side refusal fallbacks ({@code fallbacks: "default"}) need this beta on the beta endpoint. */
    static final AnthropicBeta FALLBACK_BETA = AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01;

    static final String STATUS_THINKING = "Thinking...";
    static final String STATUS_EXECUTING_TOOL = "Executing tool: %s...";

    static final String ERROR_API_KEY =
        "Anthropic API key is not configured or is invalid. Add your key in Settings " +
        "(or set the ANTHROPIC_API_KEY environment variable) and try again.";
    static final String ERROR_PERMISSION =
        "The Anthropic API key does not have access to this request (HTTP 403). Check the key in Settings.";
    static final String ERROR_MODEL_NOT_FOUND =
        "The Anthropic model '%s' was not found. Pick another model for this chat.";
    static final String ERROR_RATE_LIMIT =
        "The Anthropic API rate limit was reached. Wait a moment and try again.";
    static final String ERROR_SERVER =
        "The Anthropic API is temporarily unavailable (HTTP %d). Try again shortly.";
    static final String ERROR_BAD_REQUEST = "The Anthropic API rejected the request: %s";
    static final String ERROR_API_STATUS = "The Anthropic API returned an error (HTTP %d).";
    static final String ERROR_CONNECTION =
        "Could not reach the Anthropic API. Check your internet connection and try again.";
    static final String ERROR_NO_RESPONSE = "No response received from the AI model. Please try again.";
    static final String MESSAGE_REFUSAL = "Claude declined to answer this request.";
    static final String MESSAGE_REFUSAL_WITH_CATEGORY = "Claude declined to answer this request (category: %s).";
    static final String MESSAGE_CUT_OFF =
        "The answer was cut off because it reached the maximum response length.";
    static final String MESSAGE_TOO_COMPLEX =
        "The conversation has become too complex. Please try rephrasing your request.";

    private static final String INVALID_JSON_KEY = "INVALID_JSON";

    /**
     * Executes an MCP tool. Production code passes {@code toolExecutor::execute}.
     */
    @FunctionalInterface
    public interface ToolInvoker {
        ToolExecutor.ToolResult invoke(String toolName, Map<String, Object> arguments);
    }

    /**
     * Receives what the turn produces. Called on the worker thread.
     */
    public interface Listener {
        /** Assistant text to show to the user. */
        void onAssistantText(String text);

        /** A status-bar update. */
        void onStatus(String status);

        /** An informational message for the chat (cut-off answer, refusal, loop guard). */
        void onNotice(String message);

        /** An error message for the chat. */
        void onError(String message);
    }

    private final AnthropicService service;
    private final String modelId;
    private final String systemPrompt;
    private final List<BetaTool> tools;
    private final ToolInvoker toolInvoker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a conversation runner.
     *
     * @param service The Anthropic service that owns the SDK client
     * @param modelId The Anthropic model id, without the {@code anthropic:} prefix
     * @param systemPrompt The system prompt (kept byte-identical across turns for prompt caching)
     * @param tools The tool definitions, in a fixed order (see {@link #toAnthropicTools(List)})
     * @param toolInvoker Runs MCP tools
     */
    public AnthropicConversation(AnthropicService service, String modelId, String systemPrompt,
                                 List<BetaTool> tools, ToolInvoker toolInvoker) {
        this.service = Objects.requireNonNull(service, "Anthropic service is required");
        this.modelId = Objects.requireNonNull(modelId, "Model id is required");
        this.systemPrompt = systemPrompt;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
        this.toolInvoker = Objects.requireNonNull(toolInvoker, "Tool invoker is required");
    }

    public String getModelId() {
        return modelId;
    }

    /**
     * Runs one user turn to completion: requests, tool calls, and the final answer.
     * Errors are reported through the listener, never thrown.
     *
     * @param history The conversation so far, ending with the new user message
     *                (see {@link #historyFromStoredMessages(List, String)})
     * @param listener Receives text, status updates, notices and errors
     */
    public void run(List<BetaMessageParam> history, Listener listener) {
        try {
            runToolLoop(new ArrayList<>(history), listener);
        } catch (UnauthorizedException e) {
            logApiError("Authentication failed", e);
            listener.onError(ERROR_API_KEY);
        } catch (NoCredentialsException | CredentialResolutionException e) {
            // The SDK found no usable credential (its message lists sources, never values).
            logger.warn("No Anthropic credential available: {}", e.getClass().getSimpleName());
            listener.onError(ERROR_API_KEY);
        } catch (PermissionDeniedException e) {
            logApiError("Permission denied", e);
            listener.onError(ERROR_PERMISSION);
        } catch (NotFoundException e) {
            logApiError("Model or endpoint not found", e);
            listener.onError(String.format(ERROR_MODEL_NOT_FOUND, modelId));
        } catch (RateLimitException e) {
            logApiError("Rate limited", e);
            listener.onError(ERROR_RATE_LIMIT);
        } catch (InternalServerException e) {
            // Includes OverloadedException (529) and the other 5xx classes.
            logApiError("Server error", e);
            listener.onError(String.format(ERROR_SERVER, e.statusCode()));
        } catch (BadRequestException e) {
            logApiError("Bad request", e);
            listener.onError(String.format(ERROR_BAD_REQUEST, e.apiMessage().orElse("HTTP 400")));
        } catch (AnthropicServiceException e) {
            logApiError("API error", e);
            listener.onError(String.format(ERROR_API_STATUS, e.statusCode()));
        } catch (AnthropicIoException e) {
            logger.warn("Could not reach the Anthropic API: {}", e.getClass().getSimpleName(), e);
            listener.onError(ERROR_CONNECTION);
        }
    }

    private void logApiError(String what, AnthropicServiceException e) {
        // Status, error type and request id only: no headers (they carry the credential) and no body.
        logger.warn("Anthropic API: {} (HTTP {}, type {}, request id {})", what, e.statusCode(),
                    e.errorType().map(Object::toString).orElse("unknown"), e.requestId().orElse("none"));
    }

    private void runToolLoop(List<BetaMessageParam> messages, Listener listener) {
        for (int iteration = 1; iteration <= MAX_TOOL_ITERATIONS; iteration++) {
            listener.onStatus(STATUS_THINKING);

            BetaMessage response = streamMessage(buildParams(messages));
            if (response == null) {
                listener.onError(ERROR_NO_RESPONSE);
                return;
            }

            // Check the stop reason before reading content.
            BetaStopReason stopReason = response.stopReason().orElse(null);
            if (BetaStopReason.REFUSAL.equals(stopReason)) {
                // Content may be partial (a mid-stream decline): discard it.
                listener.onNotice(refusalMessage(response.stopDetails().orElse(null)));
                return;
            }

            List<BetaContentBlock> content = contentAfterFallback(response.content());
            String text = joinText(content);

            if (BetaStopReason.MAX_TOKENS.equals(stopReason)) {
                // Never run tools from a cut-off turn: their input may be truncated.
                if (!text.isBlank()) {
                    listener.onAssistantText(text);
                }
                listener.onNotice(MESSAGE_CUT_OFF);
                return;
            }

            if (!text.isBlank()) {
                listener.onAssistantText(text);
            }

            if (BetaStopReason.TOOL_USE.equals(stopReason)) {
                // Replay the assistant turn unchanged (thinking blocks included), then answer every
                // tool_use block in ONE user message.
                messages.add(assistantTurn(content));
                messages.add(BetaMessageParam.ofUserOfContentBlockParams(runTools(content, listener)));
                continue;
            }

            if (BetaStopReason.PAUSE_TURN.equals(stopReason)) {
                // A server-side loop paused; re-send with the assistant turn so it resumes.
                messages.add(assistantTurn(content));
                continue;
            }

            return; // end_turn, stop_sequence, or anything else: the turn is complete
        }

        logger.warn("Reached maximum tool iterations ({})", MAX_TOOL_ITERATIONS);
        listener.onNotice(MESSAGE_TOO_COMPLEX);
    }

    /**
     * Builds the request for the current message list.
     *
     * Thinking is not set, so Claude Opus 5.5 runs adaptive thinking (disabling it, or a budget, is a 400).
     * No temperature / top_p / top_k and no assistant prefill. tool_choice is left at its default (auto).
     */
    MessageCreateParams buildParams(List<BetaMessageParam> messages) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(MAX_TOKENS)
            .outputConfig(BetaOutputConfig.builder().effort(EFFORT).build())
            // Top-level automatic prompt caching: the stable prefix (tools, system, earlier turns) is reused.
            .cacheControl(BetaCacheControlEphemeral.builder().build())
            // Server-side refusal fallbacks, routed by refusal category.
            .addBeta(FALLBACK_BETA)
            .fallbacksDefault()
            .messages(messages);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(systemPrompt);
        }
        for (BetaTool tool : tools) {
            builder.addTool(tool);
        }
        return builder.build();
    }

    private BetaMessage streamMessage(MessageCreateParams params) {
        logger.debug("Calling Anthropic model '{}' with {} messages", modelId, params.messages().size());
        BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
        try (StreamResponse<BetaRawMessageStreamEvent> stream =
                 service.client().beta().messages().createStreaming(params)) {
            stream.stream().forEach(accumulator::accumulate);
        }
        return accumulator.message();
    }

    /**
     * After a mid-output fallback, blocks before the last {@code fallback} block that the fallback model
     * did not see (thinking, redacted thinking, tool calls) must not be echoed back or acted on.
     * Text and the fallback block itself stay, in place.
     */
    static List<BetaContentBlock> contentAfterFallback(List<BetaContentBlock> content) {
        int lastFallback = -1;
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).isFallback()) {
                lastFallback = i;
            }
        }
        if (lastFallback < 0) {
            return content;
        }

        List<BetaContentBlock> kept = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            BetaContentBlock block = content.get(i);
            boolean beforeBoundary = i < lastFallback;
            if (beforeBoundary && (block.isThinking() || block.isRedactedThinking()
                                   || block.isToolUse() || block.isServerToolUse())) {
                continue;
            }
            kept.add(block);
        }
        return kept;
    }

    private static String joinText(List<BetaContentBlock> content) {
        StringBuilder text = new StringBuilder();
        for (BetaContentBlock block : content) {
            block.text().ifPresent(textBlock -> {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(textBlock.text());
            });
        }
        return text.toString().trim();
    }

    private static BetaMessageParam assistantTurn(List<BetaContentBlock> content) {
        List<BetaContentBlockParam> params = new ArrayList<>(content.size());
        for (BetaContentBlock block : content) {
            params.add(block.toParam());
        }
        return BetaMessageParam.ofAssistantOfContentBlockParams(params);
    }

    /**
     * Runs every tool_use block through the MCP execution path and returns one tool_result per call.
     * Failed calls are returned with is_error, never dropped.
     */
    private List<BetaContentBlockParam> runTools(List<BetaContentBlock> content, Listener listener) {
        List<BetaContentBlockParam> results = new ArrayList<>();
        for (BetaContentBlock block : content) {
            if (block.toolUse().isEmpty()) {
                continue;
            }
            BetaToolUseBlock toolUse = block.toolUse().get();
            listener.onStatus(String.format(STATUS_EXECUTING_TOOL, toolUse.name()));

            BetaToolResultBlockParam.Builder result = BetaToolResultBlockParam.builder().toolUseId(toolUse.id());
            Map<String, Object> arguments = parseToolInput(toolUse._input());
            if (arguments == null) {
                logger.warn("Tool '{}' was called with input that is not a JSON object", toolUse.name());
                result.content(invalidJsonPayload(toolUse._input())).isError(true);
            } else {
                ToolExecutor.ToolResult toolResult = toolInvoker.invoke(toolUse.name(), arguments);
                boolean failed = !toolResult.isSuccess() || toolResult.isToolReportedError();
                result.content(toolResult.getOutput());
                if (failed) {
                    result.isError(true);
                }
            }
            results.add(BetaContentBlockParam.ofToolResult(result.build()));
        }
        return results;
    }

    /**
     * Parses a tool_use input as JSON. Returns null unless it is a JSON object.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseToolInput(JsonValue input) {
        if (input == null) {
            return null;
        }
        try {
            Object parsed = input.convert(Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> arguments = new LinkedHashMap<>();
                map.forEach((key, value) -> arguments.put(String.valueOf(key), value));
                return arguments;
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String invalidJsonPayload(JsonValue input) {
        try {
            return objectMapper.writeValueAsString(Map.of(INVALID_JSON_KEY, String.valueOf(input)));
        } catch (JsonProcessingException e) {
            return "{\"" + INVALID_JSON_KEY + "\": \"unreadable input\"}";
        }
    }

    static String refusalMessage(BetaRefusalStopDetails details) {
        if (details != null && details.category().isPresent()) {
            return String.format(MESSAGE_REFUSAL_WITH_CATEGORY, details.category().get().asString());
        }
        return MESSAGE_REFUSAL;
    }

    /**
     * Converts the MCP tools into Anthropic tool definitions: name, description, and the MCP tool's raw
     * JSON schema as input_schema. Sorted by name so the tool list is byte-identical between requests.
     *
     * @param mcpTools The MCP tools (may be null)
     * @return The Anthropic tools
     */
    public static List<BetaTool> toAnthropicTools(List<McpSchema.Tool> mcpTools) {
        if (mcpTools == null) {
            return List.of();
        }
        List<McpSchema.Tool> sorted = new ArrayList<>(mcpTools);
        sorted.sort(Comparator.comparing(McpSchema.Tool::name));

        List<BetaTool> tools = new ArrayList<>(sorted.size());
        for (McpSchema.Tool mcpTool : sorted) {
            BetaTool.Builder tool = BetaTool.builder()
                .name(mcpTool.name())
                .inputSchema(toInputSchema(mcpTool.inputSchema()));
            if (mcpTool.description() != null && !mcpTool.description().isBlank()) {
                tool.description(mcpTool.description());
            }
            tools.add(tool.build());
        }
        return tools;
    }

    private static BetaTool.InputSchema toInputSchema(Map<String, Object> schema) {
        BetaTool.InputSchema.Builder inputSchema = BetaTool.InputSchema.builder();
        if (schema == null) {
            return inputSchema.build();
        }
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "type" -> { /* always "object"; the builder sets it */ }
                case "properties" -> {
                    if (value instanceof Map<?, ?> properties) {
                        BetaTool.InputSchema.Properties.Builder props = BetaTool.InputSchema.Properties.builder();
                        properties.forEach((name, propertySchema) ->
                            props.putAdditionalProperty(String.valueOf(name), JsonValue.from(propertySchema)));
                        inputSchema.properties(props.build());
                    }
                }
                case "required" -> {
                    if (value instanceof List<?> required) {
                        inputSchema.required(required.stream().map(String::valueOf).toList());
                    }
                }
                default -> inputSchema.putAdditionalProperty(key, JsonValue.from(value));
            }
        }
        return inputSchema.build();
    }

    /**
     * Builds the request history from the chat's stored messages plus the new user message.
     *
     * Stored messages are plain user / assistant text. Leading assistant messages (such as the welcome
     * message) are dropped because a conversation must start with a user turn, and consecutive messages
     * from the same side are merged because the API expects alternating turns.
     *
     * @param storedMessages The chat's messages before the new one
     * @param userInput The new user message
     * @return The message list for the request
     */
    public static List<BetaMessageParam> historyFromStoredMessages(List<Message> storedMessages, String userInput) {
        List<Boolean> roles = new ArrayList<>();      // true = user
        List<StringBuilder> texts = new ArrayList<>();

        List<Message> all = new ArrayList<>(storedMessages != null ? storedMessages : List.of());
        all.add(new Message(userInput, true, java.time.LocalDateTime.now()));

        for (Message message : all) {
            String content = message.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            boolean fromUser = message.isFromUser();
            if (roles.isEmpty() && !fromUser) {
                continue; // must start with a user turn
            }
            if (!roles.isEmpty() && roles.get(roles.size() - 1) == fromUser) {
                texts.get(texts.size() - 1).append("\n\n").append(content);
            } else {
                roles.add(fromUser);
                texts.add(new StringBuilder(content));
            }
        }

        List<BetaMessageParam> history = new ArrayList<>(roles.size());
        for (int i = 0; i < roles.size(); i++) {
            String text = texts.get(i).toString();
            history.add(roles.get(i) ? BetaMessageParam.ofUser(text) : BetaMessageParam.ofAssistant(text));
        }
        return history;
    }
}
