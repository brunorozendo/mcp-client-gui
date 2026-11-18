package com.brunorozendo.mcpclientgui.core.chat;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaTool;
import com.brunorozendo.mcpclientgui.model.Message;
import com.brunorozendo.mcpclientgui.service.AnthropicService;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Anthropic integration end to end against {@link FakeAnthropicServer}: real SDK client,
 * real streaming and accumulation, no network beyond the loopback address, no real key, no GUI.
 */
class AnthropicConversationTest {

    private static final String MODEL = "claude-opus-5-5";

    private FakeAnthropicServer server;
    private AnthropicService service;
    private final List<String> texts = Collections.synchronizedList(new ArrayList<>());
    private final List<String> notices = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> toolCalls = Collections.synchronizedList(new ArrayList<>());

    private final AnthropicConversation.Listener listener = new AnthropicConversation.Listener() {
        @Override public void onAssistantText(String text) { texts.add(text); }
        @Override public void onStatus(String status) { }
        @Override public void onNotice(String message) { notices.add(message); }
        @Override public void onError(String message) { errors.add(message); }
    };

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeAnthropicServer();
        service = new AnthropicService(server::client, true);
    }

    @AfterEach
    void stopServer() {
        service.close();
        server.close();
    }

    private AnthropicConversation conversation(List<BetaTool> tools, AnthropicConversation.ToolInvoker invoker) {
        return new AnthropicConversation(service, MODEL, "You are a test assistant.", tools, invoker);
    }

    private static List<BetaMessageParam> userTurn(String text) {
        return AnthropicConversation.historyFromStoredMessages(List.of(), text);
    }

    private static List<BetaTool> weatherTools() {
        McpSchema.Tool weather = McpSchema.Tool.builder()
            .name("get_weather")
            .description("Get the weather for a city")
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string", "description", "City name")),
                "required", List.of("city")))
            .build();
        McpSchema.Tool time = McpSchema.Tool.builder()
            .name("get_time")
            .description("Get the time in a city")
            .inputSchema(Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))))
            .build();
        // Deliberately unsorted: the conversion must sort by name.
        return AnthropicConversation.toAnthropicTools(List.of(weather, time));
    }

    // (a) a streamed text answer
    @Test
    void streamsTextAnswer() {
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL)
            .thinking("sig-1")
            .text("Hello", ", world", "!")
            .end("end_turn"));

        conversation(List.of(), (name, args) -> { throw new AssertionError("no tool expected"); })
            .run(userTurn("Say hello"), listener);

        assertEquals(List.of("Hello, world!"), texts);
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertTrue(notices.isEmpty(), () -> "unexpected notices: " + notices);
        assertEquals(1, server.requests().size());
        assertTrue(server.requestPaths().get(0).startsWith("/v1/messages"));
        assertTrue(server.requests().get(0).path("stream").asBoolean(), "request must be streamed");
    }

    // (b) tool_use -> tool_result round trip
    @Test
    void toolUseRoundTripSendsAllResultsInOneUserMessage() {
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL)
            .thinking("sig-tools")
            .text("Let me check.")
            .toolUse("toolu_1", "get_weather", "{\"city\":\"Paris\"}")
            .toolUse("toolu_2", "get_time", "{\"city\":\"Lisbon\"}")
            .end("tool_use"));
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL)
            .text("Paris is sunny; Lisbon's clock is unknown.")
            .end("end_turn"));

        AnthropicConversation.ToolInvoker invoker = (name, args) -> {
            toolCalls.add(name + ":" + args.get("city"));
            if (name.equals("get_time")) {
                return ToolExecutor.ToolResult.error(name, "time server down");
            }
            return ToolExecutor.ToolResult.success(name, "Sunny, 22C");
        };
        conversation(weatherTools(), invoker).run(userTurn("Weather in Paris and time in Lisbon?"), listener);

        assertEquals(List.of("get_weather:Paris", "get_time:Lisbon"), toolCalls);
        assertEquals(List.of("Let me check.", "Paris is sunny; Lisbon's clock is unknown."), texts);
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(2, server.requests().size());

        JsonNode messages = FakeAnthropicServer.messagesOf(server.requests().get(1));
        assertEquals(3, messages.size(), "user, replayed assistant, one user message with all results");

        // The assistant turn is replayed with every block, thinking (and its signature) included.
        JsonNode assistant = messages.get(1);
        assertEquals("assistant", assistant.get("role").asText());
        JsonNode assistantContent = assistant.get("content");
        assertEquals(4, assistantContent.size());
        assertEquals("thinking", assistantContent.get(0).get("type").asText());
        assertEquals("sig-tools", assistantContent.get(0).get("signature").asText());
        assertEquals("text", assistantContent.get(1).get("type").asText());
        assertEquals("tool_use", assistantContent.get(2).get("type").asText());
        assertEquals("Paris", assistantContent.get(2).get("input").get("city").asText());
        assertEquals("tool_use", assistantContent.get(3).get("type").asText());

        // Both results in ONE user message, matched by id; the failure carries is_error.
        JsonNode results = messages.get(2);
        assertEquals("user", results.get("role").asText());
        JsonNode resultBlocks = results.get("content");
        assertEquals(2, resultBlocks.size());
        assertEquals("tool_result", resultBlocks.get(0).get("type").asText());
        assertEquals("toolu_1", resultBlocks.get(0).get("tool_use_id").asText());
        assertFalse(resultBlocks.get(0).path("is_error").asBoolean(false));
        assertEquals("toolu_2", resultBlocks.get(1).get("tool_use_id").asText());
        assertTrue(resultBlocks.get(1).get("is_error").asBoolean());
    }

    @Test
    void midStreamFallbackDropsPreBoundaryThinkingAndToolCalls() {
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL)
            .thinking("sig-before")
            .text("Starting.")
            .toolUse("toolu_before", "get_time", "{\"city\":\"Oslo\"}")
            .fallback(MODEL, "claude-opus-4-8")
            .text("Continuing on the fallback model.")
            .toolUse("toolu_after", "get_weather", "{\"city\":\"Paris\"}")
            .end("tool_use"));
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder("claude-opus-4-8").text("Done.").end("end_turn"));

        conversation(weatherTools(), (name, args) -> {
            toolCalls.add(name + ":" + args.get("city"));
            return ToolExecutor.ToolResult.success(name, "ok");
        }).run(userTurn("Go"), listener);

        // Only the call after the fallback boundary runs
        assertEquals(List.of("get_weather:Paris"), toolCalls);

        JsonNode assistantContent = FakeAnthropicServer.messagesOf(server.requests().get(1)).get(1).get("content");
        List<String> types = new ArrayList<>();
        assistantContent.forEach(block -> types.add(block.get("type").asText()));
        assertEquals(List.of("text", "fallback", "text", "tool_use"), types);

        JsonNode results = FakeAnthropicServer.messagesOf(server.requests().get(1)).get(2).get("content");
        assertEquals(1, results.size());
        assertEquals("toolu_after", results.get(0).get("tool_use_id").asText());
    }

    @Test
    void toolInputIsParsedAsAJsonObjectOnly() {
        Map<String, Object> parsed = AnthropicConversation.parseToolInput(
            JsonValue.from(Map.of("city", "Paris", "days", 3, "units", Map.of("temp", "C"))));
        assertEquals("Paris", parsed.get("city"));
        assertEquals(3, ((Number) parsed.get("days")).intValue());
        assertEquals(Map.of("temp", "C"), parsed.get("units"));

        // Anything that is not a JSON object is rejected (and answered with an is_error result).
        assertNull(AnthropicConversation.parseToolInput(JsonValue.from(List.of(1, 2))));
        assertNull(AnthropicConversation.parseToolInput(JsonValue.from("{\"city\":\"Paris\"}")));
    }

    // (c) refusal stop reason
    @Test
    void refusalShowsCategoryAndDiscardsPartialText() {
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL)
            .text("Partial answer that must not be shown")
            .end("refusal", "cyber"));

        conversation(List.of(), (name, args) -> { throw new AssertionError("no tool expected"); })
            .run(userTurn("Something declined"), listener);

        assertTrue(texts.isEmpty(), "partial output of a refused turn must be discarded");
        assertEquals(List.of("Claude declined to answer this request (category: cyber)."), notices);
    }

    @Test
    void maxTokensSaysTheAnswerWasCutOff() {
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL)
            .text("A long answer that")
            .end("max_tokens"));

        conversation(List.of(), (name, args) -> { throw new AssertionError("no tool expected"); })
            .run(userTurn("Write a lot"), listener);

        assertEquals(List.of("A long answer that"), texts);
        assertEquals(List.of(AnthropicConversation.MESSAGE_CUT_OFF), notices);
    }

    // (d) HTTP 401 mapped to the API-key message
    @Test
    void unauthorizedIsMappedToTheApiKeyMessage() {
        server.enqueueError(401, "authentication_error", "invalid x-api-key");

        conversation(List.of(), (name, args) -> { throw new AssertionError("no tool expected"); })
            .run(userTurn("Hello"), listener);

        assertEquals(List.of(AnthropicConversation.ERROR_API_KEY), errors);
        assertTrue(errors.get(0).startsWith("Anthropic API key is not configured"));
        assertFalse(errors.get(0).contains(FakeAnthropicServer.DUMMY_API_KEY), "the key must never be shown");
    }

    @Test
    void notFoundAndRateLimitHaveTheirOwnMessages() {
        server.enqueueError(404, "not_found_error", "model not found");
        server.enqueueError(429, "rate_limit_error", "slow down");
        AnthropicConversation conversation =
            conversation(List.of(), (name, args) -> { throw new AssertionError("no tool expected"); });

        conversation.run(userTurn("Hello"), listener);
        conversation.run(userTurn("Hello again"), listener);

        assertEquals(List.of(String.format(AnthropicConversation.ERROR_MODEL_NOT_FOUND, MODEL),
                             AnthropicConversation.ERROR_RATE_LIMIT), errors);
    }

    // (e) the request body
    @Test
    void requestBodyCarriesModelBudgetEffortCachingFallbacksAndTools() {
        server.enqueueStream(new FakeAnthropicServer.StreamBuilder(MODEL).text("ok").end("end_turn"));

        conversation(weatherTools(), (name, args) -> { throw new AssertionError("no tool expected"); })
            .run(userTurn("Hi"), listener);

        JsonNode body = server.requests().get(0);
        assertEquals(MODEL, body.get("model").asText());
        assertEquals(64000, body.get("max_tokens").asLong());
        assertEquals("medium", body.get("output_config").get("effort").asText());
        assertEquals("ephemeral", body.get("cache_control").get("type").asText());
        assertEquals("default", body.get("fallbacks").asText());
        assertEquals("You are a test assistant.", body.get("system").asText());

        // Adaptive thinking by omission; no sampling parameters, no forced tool choice.
        assertFalse(body.has("thinking"), "thinking must be omitted (adaptive)");
        assertFalse(body.has("temperature"));
        assertFalse(body.has("top_p"));
        assertFalse(body.has("top_k"));
        assertFalse(body.has("tool_choice"));

        // Tools: sorted by name, schema taken from the MCP tool.
        JsonNode tools = body.get("tools");
        assertEquals(2, tools.size());
        assertEquals("get_time", tools.get(0).get("name").asText());
        JsonNode weather = tools.get(1);
        assertEquals("get_weather", weather.get("name").asText());
        assertEquals("Get the weather for a city", weather.get("description").asText());
        assertEquals("object", weather.get("input_schema").get("type").asText());
        assertEquals("string", weather.get("input_schema").get("properties").get("city").get("type").asText());
        assertEquals("city", weather.get("input_schema").get("required").get(0).asText());

        // One user message, and no prefilled assistant turn at the end.
        JsonNode messages = body.get("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(messages.size() - 1).get("role").asText());

        // Server-side fallbacks ride on their beta header.
        assertTrue(server.betaHeaders().get(0).contains("server-side-fallback-2026-07-01"), server.betaHeaders().get(0));
    }

    @Test
    void storedHistoryStartsWithUserAndAlternates() {
        LocalDateTime now = LocalDateTime.now();
        List<Message> stored = List.of(
            new Message("Welcome to MCP Client GUI!", false, now),
            new Message("first question", true, now),
            new Message("first answer", false, now),
            new Message("tool progress note", false, now),
            new Message("unanswered question", true, now));

        List<BetaMessageParam> history = AnthropicConversation.historyFromStoredMessages(stored, "new question");

        assertEquals(3, history.size());
        assertTrue(history.get(0).isUser());
        assertTrue(history.get(1).isAssistant());
        assertTrue(history.get(2).isUser());
        assertEquals("first answer\n\ntool progress note", history.get(1).asAssistant().content().asString());
        assertEquals("unanswered question\n\nnew question", history.get(2).asUser().content().asString());
    }
}
