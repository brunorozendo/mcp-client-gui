package com.brunorozendo.mcpclientgui.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal MCP server over stdio for tests: newline-delimited JSON-RPC on stdin/stdout. It offers one
 * tool, {@code echo}, and no resources or prompts. Only protocol messages are written to stdout.
 */
public final class FakeMcpStdioServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeMcpStdioServer() {
    }

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        // Anything else that prints must not corrupt the protocol stream.
        System.setOut(System.err);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode request;
            try {
                request = MAPPER.readTree(line);
            } catch (IOException e) {
                System.err.println("fake MCP server: unreadable line: " + e.getMessage());
                continue;
            }
            JsonNode id = request.get("id");
            if (id == null || id.isNull() || !request.has("method")) {
                continue; // notification or response: nothing to answer
            }
            ObjectNode response = handle(request.get("method").asText(), request.path("params"), id);
            out.println(MAPPER.writeValueAsString(response));
            out.flush();
        }
    }

    private static ObjectNode handle(String method, JsonNode params, JsonNode id) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode result = MAPPER.createObjectNode();
        switch (method) {
            case "initialize" -> {
                result.put("protocolVersion", params.path("protocolVersion").asText());
                ObjectNode capabilities = result.putObject("capabilities");
                capabilities.putObject("tools");
                capabilities.putObject("resources");
                capabilities.putObject("prompts");
                ObjectNode serverInfo = result.putObject("serverInfo");
                serverInfo.put("name", "fake-mcp-server");
                serverInfo.put("version", "1.0.0");
            }
            case "tools/list" -> {
                ObjectNode tool = result.putArray("tools").addObject();
                tool.put("name", "echo");
                tool.put("description", "Echoes the given text.");
                ObjectNode schema = tool.putObject("inputSchema");
                schema.put("type", "object");
                schema.putObject("properties").putObject("text").put("type", "string");
                schema.putArray("required").add("text");
            }
            case "resources/list" -> result.putArray("resources");
            case "prompts/list" -> result.putArray("prompts");
            case "tools/call" -> {
                String text = params.path("arguments").path("text").asText();
                ArrayNode content = result.putArray("content");
                ObjectNode item = content.addObject();
                item.put("type", "text");
                item.put("text", "echo: " + text);
                result.put("isError", false);
            }
            case "ping" -> {
                // empty result
            }
            default -> {
                ObjectNode error = response.putObject("error");
                error.put("code", -32601);
                error.put("message", "Method not found: " + method);
                return response;
            }
        }
        response.set("result", result);
        return response;
    }
}
