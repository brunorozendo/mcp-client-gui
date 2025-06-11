package com.brunorozendo.mcpclientgui.control;

import com.brunorozendo.mcpclientgui.util.SchemaConverter;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A utility class to construct a detailed system prompt for the LLM,
 * informing it about the available MCP capabilities (tools, resources, and prompts).
 */
public class SystemPromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SystemPromptBuilder.class);

    /**
     * Builds a comprehensive system prompt string.
     *
     * @param tools     List of available MCP tools.
     * @param resources List of available MCP resources.
     * @param prompts   List of available MCP prompts.
     * @return A formatted string to be used as the system prompt.
     */
    public static String build(List<McpSchema.Tool> tools, List<McpSchema.Resource> resources, List<McpSchema.Prompt> prompts) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful AI assistant with access to a set of capabilities provided by Model Context Protocol (MCP) servers.\n");
        sb.append("You can use the tools provided. When you decide to call a tool, you must respond with a JSON object containing the tool call.\n");
        sb.append("You also have access to a list of resources and prompts for context.\n");

        boolean hasCapabilities = !tools.isEmpty() || !resources.isEmpty() || !prompts.isEmpty();

        if (!hasCapabilities) {
            sb.append("\nNo external capabilities (tools, resources, or prompts) are currently available.");
            logger.warn("No MCP capabilities discovered. The LLM will operate without them.");
            return sb.toString();
        }

        sb.append("\nHere are the available capabilities:\n");

        if (!tools.isEmpty()) {
            sb.append("\n--- AVAILABLE TOOLS ---\n");
            sb.append("You can call the following tools. For each tool, the name, description, and parameters are provided.\n\n");
            tools.forEach(tool -> sb.append(formatTool(tool)).append("\n"));
        }

        if (!resources.isEmpty()) {
            sb.append("\n--- AVAILABLE RESOURCES ---\n");
            sb.append("The following resources are available for context. You can refer to them in your responses.\n\n");
            resources.forEach(resource -> sb.append(formatResource(resource)).append("\n"));
        }

        if (!prompts.isEmpty()) {
            sb.append("\n--- AVAILABLE PROMPTS ---\n");
            sb.append("The following prompt templates are available for use.\n\n");
            prompts.forEach(prompt -> sb.append(formatPrompt(prompt)).append("\n"));
        }

        return sb.toString();
    }

    private static String formatTool(McpSchema.Tool tool) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tool: %s\n", tool.name()));
        sb.append(String.format("  Description: %s\n", tool.description()));
        if (tool.inputSchema() != null && tool.inputSchema().properties() != null && !tool.inputSchema().properties().isEmpty()) {
            sb.append("  Parameters:\n");
            OllamaApi.JsonSchema ollamaSchema = SchemaConverter.convertMcpSchemaRecursive(tool.inputSchema());
            sb.append(formatJsonSchema(ollamaSchema, "    "));
        } else {
            sb.append("  Parameters: None\n");
        }
        return sb.toString();
    }

    private static String formatResource(McpSchema.Resource resource) {
        return String.format("Resource URI: %s\n  Name: %s\n  Description: %s\n  MIME Type: %s\n",
                resource.uri(),
                resource.name(),
                resource.description(),
                resource.mimeType() != null ? resource.mimeType() : "N/A");
    }

    private static String formatPrompt(McpSchema.Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Prompt: %s\n", prompt.name()));
        sb.append(String.format("  Description: %s\n", prompt.description()));
        if (prompt.arguments() != null && !prompt.arguments().isEmpty()) {
            sb.append("  Arguments:\n");
            prompt.arguments().forEach(arg ->
                    sb.append(String.format("    - %s (%s): %s\n",
                            arg.name(),
                            arg.required() ? "required" : "optional",
                            arg.description()))
            );
        }
        return sb.toString();
    }

    private static String formatJsonSchema(OllamaApi.JsonSchema schema, String indent) {
        if (schema == null) return "";
        StringBuilder sb = new StringBuilder();
        if ("object".equals(schema.type()) && schema.properties() != null) {
            schema.properties().forEach((key, propSchema) -> {
                boolean isRequired = schema.required() != null && schema.required().contains(key);
                sb.append(String.format("%s- %s (%s, %s): %s\n",
                        indent,
                        key,
                        propSchema.type(),
                        isRequired ? "required" : "optional",
                        propSchema.description() != null ? propSchema.description() : "No description"));
                // Recursively format nested objects
                if ("object".equals(propSchema.type())) {
                    sb.append(formatJsonSchema(propSchema, indent + "  "));
                }
            });
        }
        return sb.toString();
    }
}
