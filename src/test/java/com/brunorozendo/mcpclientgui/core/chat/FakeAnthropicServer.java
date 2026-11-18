package com.brunorozendo.mcpclientgui.core.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A local stand-in for the Messages API: answers each POST to /v1/messages with the next scripted
 * response (an SSE stream or an error status) and records the request bodies.
 * Bound to the loopback address on an ephemeral port; nothing leaves the machine.
 */
final class FakeAnthropicServer implements AutoCloseable {

    static final String DUMMY_API_KEY = "test-key-not-a-real-key";

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Scripted(int status, String contentType, String body) {}

    private final HttpServer server;
    private final Deque<Scripted> responses = new ArrayDeque<>();
    private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
    private final List<String> betaHeaders = Collections.synchronizedList(new ArrayList<>());

    FakeAnthropicServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/messages", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    /** A client pointed at this server with a dummy key and no retries. */
    AnthropicClient client() {
        return AnthropicOkHttpClient.builder()
            .baseUrl(baseUrl())
            .apiKey(DUMMY_API_KEY)
            .maxRetries(0)
            .build();
    }

    List<JsonNode> requests() {
        return requests;
    }

    List<String> requestPaths() {
        return requestPaths;
    }

    /** The anthropic-beta header of each request ("" when absent). */
    List<String> betaHeaders() {
        return betaHeaders;
    }

    void enqueueStream(String sse) {
        responses.add(new Scripted(200, "text/event-stream", sse));
    }

    void enqueueError(int status, String errorType, String message) {
        ObjectNode body = JSON.createObjectNode();
        body.put("type", "error");
        ObjectNode error = body.putObject("error");
        error.put("type", errorType);
        error.put("message", message);
        responses.add(new Scripted(status, "application/json", body.toString()));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(JSON.readTree(requestBody));
            requestPaths.add(exchange.getRequestURI().toString());
            betaHeaders.add(String.join(",", exchange.getRequestHeaders().getOrDefault("anthropic-beta", List.of())));

            Scripted next;
            synchronized (responses) {
                next = responses.poll();
            }
            if (next == null) {
                next = new Scripted(500, "application/json",
                    "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"no scripted response\"}}");
            }
            byte[] body = next.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", next.contentType());
            exchange.getResponseHeaders().add("request-id", "req_test");
            exchange.sendResponseHeaders(next.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ===== SSE builders =====

    /** Builds an SSE stream for one assistant message. */
    static final class StreamBuilder {
        private final StringBuilder sse = new StringBuilder();
        private int index = 0;

        StreamBuilder(String model) {
            ObjectNode message = JSON.createObjectNode();
            message.put("id", "msg_test_" + System.nanoTime());
            message.put("type", "message");
            message.put("role", "assistant");
            message.put("model", model);
            message.putArray("content");
            message.putNull("stop_reason");
            message.putNull("stop_sequence");
            ObjectNode usage = message.putObject("usage");
            usage.put("input_tokens", 10);
            usage.put("output_tokens", 1);
            ObjectNode start = JSON.createObjectNode();
            start.put("type", "message_start");
            start.set("message", message);
            event("message_start", start);
        }

        StreamBuilder thinking(String signature) {
            ObjectNode block = JSON.createObjectNode();
            block.put("type", "thinking");
            block.put("thinking", "");
            block.put("signature", "");
            blockStart(block);
            ObjectNode delta = JSON.createObjectNode();
            delta.put("type", "signature_delta");
            delta.put("signature", signature);
            blockDelta(delta);
            blockStop();
            return this;
        }

        StreamBuilder text(String... chunks) {
            ObjectNode block = JSON.createObjectNode();
            block.put("type", "text");
            block.put("text", "");
            blockStart(block);
            for (String chunk : chunks) {
                ObjectNode delta = JSON.createObjectNode();
                delta.put("type", "text_delta");
                delta.put("text", chunk);
                blockDelta(delta);
            }
            blockStop();
            return this;
        }

        StreamBuilder toolUse(String id, String name, String inputJson) {
            ObjectNode block = JSON.createObjectNode();
            block.put("type", "tool_use");
            block.put("id", id);
            block.put("name", name);
            block.putObject("input");
            blockStart(block);
            ObjectNode delta = JSON.createObjectNode();
            delta.put("type", "input_json_delta");
            delta.put("partial_json", inputJson);
            blockDelta(delta);
            blockStop();
            return this;
        }

        /** A fallback switch point (server-side refusal fallback). */
        StreamBuilder fallback(String fromModel, String toModel) {
            ObjectNode block = JSON.createObjectNode();
            block.put("type", "fallback");
            block.putObject("from").put("model", fromModel);
            block.putObject("to").put("model", toModel);
            ObjectNode trigger = block.putObject("trigger");
            trigger.put("type", "refusal");
            trigger.put("category", "cyber");
            blockStart(block);
            blockStop();
            return this;
        }

        /** Ends the message with a stop reason and, for refusals, a stop_details category. */
        String end(String stopReason, String refusalCategory) {
            ObjectNode messageDelta = JSON.createObjectNode();
            messageDelta.put("type", "message_delta");
            ObjectNode delta = messageDelta.putObject("delta");
            delta.put("stop_reason", stopReason);
            delta.putNull("stop_sequence");
            if (refusalCategory != null) {
                ObjectNode details = delta.putObject("stop_details");
                details.put("type", "refusal");
                details.put("category", refusalCategory);
                details.putNull("explanation");
            }
            ObjectNode usage = messageDelta.putObject("usage");
            usage.put("output_tokens", 5);
            event("message_delta", messageDelta);

            ObjectNode stop = JSON.createObjectNode();
            stop.put("type", "message_stop");
            event("message_stop", stop);
            return sse.toString();
        }

        String end(String stopReason) {
            return end(stopReason, null);
        }

        private void blockStart(ObjectNode block) {
            ObjectNode start = JSON.createObjectNode();
            start.put("type", "content_block_start");
            start.put("index", index);
            start.set("content_block", block);
            event("content_block_start", start);
        }

        private void blockDelta(ObjectNode delta) {
            ObjectNode event = JSON.createObjectNode();
            event.put("type", "content_block_delta");
            event.put("index", index);
            event.set("delta", delta);
            event("content_block_delta", event);
        }

        private void blockStop() {
            ObjectNode stop = JSON.createObjectNode();
            stop.put("type", "content_block_stop");
            stop.put("index", index);
            event("content_block_stop", stop);
            index++;
        }

        private void event(String name, JsonNode data) {
            sse.append("event: ").append(name).append('\n')
               .append("data: ").append(data.toString()).append("\n\n");
        }
    }

    static ArrayNode messagesOf(JsonNode request) {
        return (ArrayNode) request.get("messages");
    }
}
