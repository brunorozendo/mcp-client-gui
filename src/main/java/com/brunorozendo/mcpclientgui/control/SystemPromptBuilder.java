package com.brunorozendo.mcpclientgui.control;

import com.brunorozendo.mcpclientgui.util.SchemaConverter;
import com.brunorozendo.mcpclientgui.model.OllamaApi;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds system prompts for the Language Learning Model (LLM).
 * 
 * The system prompt provides the LLM with context about:
 * - Its role as an AI assistant
 * - Available MCP capabilities (tools, resources, prompts)
 * - Instructions on how to use these capabilities
 * 
 * The generated prompt helps the LLM understand what external functions
 * it can call and how to format its responses when tool usage is required.
 */
public class SystemPromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SystemPromptBuilder.class);

    // Prompt structure constants
    private static final String PROMPT_HEADER = 
        "You are a helpful AI assistant with access to a set of capabilities provided by Model Context Protocol (MCP) servers.\n" +
        "You can use the tools provided. When you decide to call a tool, you must respond with a JSON object containing the tool call.\n" +
        "You also have access to a list of resources and prompts for context.\n";
    
    private static final String NO_CAPABILITIES_MESSAGE = 
        "\nNo external capabilities (tools, resources, or prompts) are currently available.";
    
    private static final String CAPABILITIES_INTRO = 
        "\nHere are the available capabilities:\n";
    
    // Section headers
    private static final String TOOLS_SECTION_HEADER = 
        "\n--- AVAILABLE TOOLS ---\n" +
        "You can call the following tools. For each tool, the name, description, and parameters are provided.\n\n";
    
    private static final String RESOURCES_SECTION_HEADER = 
        "\n--- AVAILABLE RESOURCES ---\n" +
        "The following resources are available for context. You can refer to them in your responses.\n\n";
    
    private static final String PROMPTS_SECTION_HEADER = 
        "\n--- AVAILABLE PROMPTS ---\n" +
        "The following prompt templates are available for use.\n\n";
    
    // Formatting constants
    private static final String INDENT_LEVEL_1 = "  ";
    private static final String INDENT_LEVEL_2 = "    ";
    private static final String PARAMETER_PREFIX = "- ";
    private static final String REQUIRED_LABEL = "required";
    private static final String OPTIONAL_LABEL = "optional";
    private static final String NO_DESCRIPTION = "No description provided";
    private static final String NOT_AVAILABLE = "N/A";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SystemPromptBuilder() {
        throw new AssertionError("SystemPromptBuilder should not be instantiated");
    }

    /**
     * Builds a comprehensive system prompt for the LLM.
     * 
     * The prompt includes information about all available MCP capabilities,
     * formatted in a way that helps the LLM understand how to use them.
     *
     * @param tools List of available MCP tools (may be null or empty)
     * @param resources List of available MCP resources (may be null or empty)
     * @param prompts List of available MCP prompts (may be null or empty)
     * @return A formatted system prompt string
     */
    public static String build(List<McpSchema.Tool> tools, 
                             List<McpSchema.Resource> resources, 
                             List<McpSchema.Prompt> prompts) {
        // Ensure non-null lists
        tools = tools != null ? tools : List.of();
        resources = resources != null ? resources : List.of();
        prompts = prompts != null ? prompts : List.of();
        
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(PROMPT_HEADER);

        boolean hasCapabilities = !tools.isEmpty() || !resources.isEmpty() || !prompts.isEmpty();

        if (!hasCapabilities) {
            promptBuilder.append(NO_CAPABILITIES_MESSAGE);
            logger.warn("No MCP capabilities discovered. The LLM will operate without external tools.");
            return promptBuilder.toString();
        }

        promptBuilder.append(CAPABILITIES_INTRO);
        
        // Add each section if capabilities exist
        appendToolsSection(promptBuilder, tools);
        appendResourcesSection(promptBuilder, resources);
        appendPromptsSection(promptBuilder, prompts);

        String finalPrompt = promptBuilder.toString();
        logger.info("Built system prompt with {} tools, {} resources, {} prompts (total length: {} chars)",
                   tools.size(), resources.size(), prompts.size(), finalPrompt.length());
        
        return finalPrompt;
    }

    /**
     * Appends the tools section to the prompt if tools are available.
     * 
     * @param builder The StringBuilder to append to
     * @param tools The list of tools
     */
    private static void appendToolsSection(StringBuilder builder, List<McpSchema.Tool> tools) {
        if (tools.isEmpty()) {
            return;
        }
        
        builder.append(TOOLS_SECTION_HEADER);
        tools.forEach(tool -> builder.append(formatTool(tool)).append("\n"));
    }

    /**
     * Appends the resources section to the prompt if resources are available.
     * 
     * @param builder The StringBuilder to append to
     * @param resources The list of resources
     */
    private static void appendResourcesSection(StringBuilder builder, List<McpSchema.Resource> resources) {
        if (resources.isEmpty()) {
            return;
        }
        
        builder.append(RESOURCES_SECTION_HEADER);
        resources.forEach(resource -> builder.append(formatResource(resource)).append("\n"));
    }

    /**
     * Appends the prompts section to the prompt if prompts are available.
     * 
     * @param builder The StringBuilder to append to
     * @param prompts The list of prompts
     */
    private static void appendPromptsSection(StringBuilder builder, List<McpSchema.Prompt> prompts) {
        if (prompts.isEmpty()) {
            return;
        }
        
        builder.append(PROMPTS_SECTION_HEADER);
        prompts.forEach(prompt -> builder.append(formatPrompt(prompt)).append("\n"));
    }

    /**
     * Formats a tool definition for inclusion in the system prompt.
     * 
     * @param tool The tool to format
     * @return Formatted tool description
     */
    private static String formatTool(McpSchema.Tool tool) {
        StringBuilder toolBuilder = new StringBuilder();
        
        // Tool name and description
        toolBuilder.append("Tool: ").append(tool.name()).append("\n");
        toolBuilder.append(INDENT_LEVEL_1).append("Description: ")
                  .append(tool.description() != null ? tool.description() : NO_DESCRIPTION)
                  .append("\n");
        
        // Tool parameters
        if (hasParameters(tool)) {
            toolBuilder.append(INDENT_LEVEL_1).append("Parameters:\n");
            OllamaApi.JsonSchema ollamaSchema = SchemaConverter.convertMcpSchemaRecursive(tool.inputSchema());
            toolBuilder.append(formatJsonSchema(ollamaSchema, INDENT_LEVEL_2));
        } else {
            toolBuilder.append(INDENT_LEVEL_1).append("Parameters: None\n");
        }
        
        return toolBuilder.toString();
    }

    /**
     * Checks if a tool has parameters defined.
     * 
     * @param tool The tool to check
     * @return True if the tool has parameters
     */
    private static boolean hasParameters(McpSchema.Tool tool) {
        return tool.inputSchema() != null && 
               tool.inputSchema().properties() != null && 
               !tool.inputSchema().properties().isEmpty();
    }

    /**
     * Formats a resource definition for inclusion in the system prompt.
     * 
     * @param resource The resource to format
     * @return Formatted resource description
     */
    private static String formatResource(McpSchema.Resource resource) {
        StringBuilder resourceBuilder = new StringBuilder();
        
        resourceBuilder.append("Resource URI: ").append(resource.uri()).append("\n");
        resourceBuilder.append(INDENT_LEVEL_1).append("Name: ")
                      .append(resource.name() != null ? resource.name() : NOT_AVAILABLE)
                      .append("\n");
        resourceBuilder.append(INDENT_LEVEL_1).append("Description: ")
                      .append(resource.description() != null ? resource.description() : NO_DESCRIPTION)
                      .append("\n");
        resourceBuilder.append(INDENT_LEVEL_1).append("MIME Type: ")
                      .append(resource.mimeType() != null ? resource.mimeType() : NOT_AVAILABLE)
                      .append("\n");
        
        return resourceBuilder.toString();
    }

    /**
     * Formats a prompt definition for inclusion in the system prompt.
     * 
     * @param prompt The prompt to format
     * @return Formatted prompt description
     */
    private static String formatPrompt(McpSchema.Prompt prompt) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // Prompt name and description
        promptBuilder.append("Prompt: ").append(prompt.name()).append("\n");
        promptBuilder.append(INDENT_LEVEL_1).append("Description: ")
                    .append(prompt.description() != null ? prompt.description() : NO_DESCRIPTION)
                    .append("\n");
        
        // Prompt arguments
        if (prompt.arguments() != null && !prompt.arguments().isEmpty()) {
            promptBuilder.append(INDENT_LEVEL_1).append("Arguments:\n");
            prompt.arguments().forEach(arg -> formatPromptArgument(promptBuilder, arg));
        }
        
        return promptBuilder.toString();
    }

    /**
     * Formats a single prompt argument.
     * 
     * @param builder The StringBuilder to append to
     * @param argument The argument to format
     */
    private static void formatPromptArgument(StringBuilder builder, McpSchema.PromptArgument argument) {
        builder.append(INDENT_LEVEL_2)
               .append(PARAMETER_PREFIX)
               .append(argument.name())
               .append(" (")
               .append(Boolean.TRUE.equals(argument.required()) ? REQUIRED_LABEL : OPTIONAL_LABEL)
               .append("): ")
               .append(argument.description() != null ? argument.description() : NO_DESCRIPTION)
               .append("\n");
    }

    /**
     * Recursively formats a JSON schema for tool parameters.
     * 
     * @param schema The schema to format
     * @param indent The current indentation level
     * @return Formatted schema description
     */
    private static String formatJsonSchema(OllamaApi.JsonSchema schema, String indent) {
        if (schema == null) {
            return "";
        }
        
        StringBuilder schemaBuilder = new StringBuilder();
        
        // Only process object types with properties
        if (isObjectWithProperties(schema)) {
            schema.properties().forEach((propertyName, propertySchema) -> {
                formatSchemaProperty(schemaBuilder, schema, propertyName, propertySchema, indent);
            });
        }
        
        return schemaBuilder.toString();
    }

    /**
     * Checks if a schema is an object type with properties.
     * 
     * @param schema The schema to check
     * @return True if it's an object with properties
     */
    private static boolean isObjectWithProperties(OllamaApi.JsonSchema schema) {
        return "object".equals(schema.type()) && 
               schema.properties() != null && 
               !schema.properties().isEmpty();
    }

    /**
     * Formats a single property of a JSON schema.
     * 
     * @param builder The StringBuilder to append to
     * @param parentSchema The parent schema containing required field info
     * @param propertyName The property name
     * @param propertySchema The property's schema
     * @param indent The current indentation
     */
    private static void formatSchemaProperty(StringBuilder builder, 
                                           OllamaApi.JsonSchema parentSchema,
                                           String propertyName, 
                                           OllamaApi.JsonSchema propertySchema, 
                                           String indent) {
        // Determine if property is required
        boolean isRequired = parentSchema.required() != null && 
                           parentSchema.required().contains(propertyName);
        
        // Format the property
        builder.append(indent)
               .append(PARAMETER_PREFIX)
               .append(propertyName)
               .append(" (")
               .append(propertySchema.type())
               .append(", ")
               .append(isRequired ? REQUIRED_LABEL : OPTIONAL_LABEL)
               .append("): ")
               .append(propertySchema.description() != null ? propertySchema.description() : NO_DESCRIPTION)
               .append("\n");
        
        // Recursively format nested objects
        if ("object".equals(propertySchema.type())) {
            builder.append(formatJsonSchema(propertySchema, indent + INDENT_LEVEL_1));
        }
    }

    /**
     * Creates a minimal system prompt when no capabilities are available.
     * 
     * @return A basic system prompt
     */
    public static String buildMinimalPrompt() {
        return PROMPT_HEADER + NO_CAPABILITIES_MESSAGE;
    }
}
