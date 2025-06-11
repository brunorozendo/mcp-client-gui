package com.brunorozendo.mcpclientgui.util;

import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the conversion of MCP (Model Context Protocol) schemas to Ollama API compatible schemas.
 * This is crucial for correctly exposing MCP capabilities (like tools) to the Ollama LLM.
 */
public class SchemaConverter {

    private static final Logger logger = LoggerFactory.getLogger(SchemaConverter.class);
    private static final ObjectMapper generalObjectMapper = new ObjectMapper();

    /**
     * Converts a list of MCP Tools into a list of Ollama Tools.
     *
     * @param mcpTools The list of tools defined by the MCP servers.
     * @return A list of tools formatted for the Ollama API.
     */
    public static List<OllamaApi.Tool> convertMcpToolsToOllamaTools(List<McpSchema.Tool> mcpTools) {
        if (mcpTools == null) {
            return java.util.Collections.emptyList();
        }
        return mcpTools.stream()
                .map(mcpTool -> {
                    OllamaApi.OllamaFunction ollamaFunction = new OllamaApi.OllamaFunction(
                            mcpTool.name(),
                            mcpTool.description(),
                            convertMcpInputSchemaToOllamaParamsSchema(mcpTool.inputSchema())
                    );
                    return new OllamaApi.Tool("function", ollamaFunction);
                })
                .collect(Collectors.toList());
    }

    /**
     * Converts an MCP input JSON schema into the format required by Ollama's 'parameters' field.
     *
     * @param mcpInputSchema The JSON schema from an MCP Tool.
     * @return A JSON schema formatted for the Ollama API.
     */
    private static OllamaApi.JsonSchema convertMcpInputSchemaToOllamaParamsSchema(McpSchema.JsonSchema mcpInputSchema) {
        if (mcpInputSchema == null) {
            // If an MCP tool has no input schema, represent it as an empty object for Ollama.
            return new OllamaApi.JsonSchema("object", "No parameters", new HashMap<>(), null, new ArrayList<>(), null, null);
        }
        return convertMcpSchemaRecursive(mcpInputSchema);
    }

    /**
     * Recursively converts an MCP JSON schema to an Ollama-compatible JSON schema.
     * This method handles nested objects and arrays, and attempts to preserve as much detail as possible.
     *
     * @param mcpSchema The MCP schema to convert.
     * @return The converted Ollama-compatible schema.
     */
    public static OllamaApi.JsonSchema convertMcpSchemaRecursive(McpSchema.JsonSchema mcpSchema) {
        if (mcpSchema == null) {
            // Default to a string type if a sub-schema is unexpectedly null.
            return new OllamaApi.JsonSchema("string", "Undefined schema");
        }

        // Directly supported fields
        String type = mcpSchema.type();
        List<String> required = mcpSchema.required() != null ? new ArrayList<>(mcpSchema.required()) : null;

        // Fields that need to be extracted from the generic 'properties' map in McpSchema
        String description = null;
        List<Object> enumValues = null;
        String format = null;
        OllamaApi.JsonSchema itemsSchema = null;
        Map<String, OllamaApi.JsonSchema> propertiesSchema = null;

        Map<String, Object> mcpProperties = mcpSchema.properties();

        if (mcpProperties != null) {
            description = (String) mcpProperties.get("description");
            enumValues = (List<Object>) mcpProperties.get("enum");
            format = (String) mcpProperties.get("format");

            if ("object".equals(type)) {
                Object propsField = mcpProperties.get("properties");
                if (propsField instanceof Map) {
                    propertiesSchema = new HashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) propsField).entrySet()) {
                        String propKey = (String) entry.getKey();
                        McpSchema.JsonSchema propertyMcpSchema = convertObjectToMcpSchema(entry.getValue(), propKey);
                        propertiesSchema.put(propKey, convertMcpSchemaRecursive(propertyMcpSchema));
                    }
                }
            } else if ("array".equals(type)) {
                Object itemsField = mcpProperties.get("items");
                if (itemsField != null) {
                    McpSchema.JsonSchema itemsMcpSchema = convertObjectToMcpSchema(itemsField, "array items");
                    itemsSchema = convertMcpSchemaRecursive(itemsMcpSchema);
                } else {
                    logger.warn("Array type schema for '{}' does not have a parsable 'items' definition. Defaulting to array of strings.", type);
                    itemsSchema = new OllamaApi.JsonSchema("string", "Array item");
                }
            }
        }

        return new OllamaApi.JsonSchema(type, description, propertiesSchema, itemsSchema, required, enumValues, format);
    }

    /**
     * A helper to safely convert a generic Object from the schema properties map into an McpSchema.JsonSchema instance.
     */
    private static McpSchema.JsonSchema convertObjectToMcpSchema(Object schemaObject, String propertyName) {
        if (schemaObject instanceof McpSchema.JsonSchema) {
            return (McpSchema.JsonSchema) schemaObject;
        }
        if (schemaObject instanceof Map) {
            try {
                // Use Jackson's converter to map the Map to the record
                return generalObjectMapper.convertValue(schemaObject, McpSchema.JsonSchema.class);
            } catch (IllegalArgumentException e) {
                logger.warn("Could not convert property '{}' schema (Map) to McpSchema.JsonSchema. Defaulting to string. Error: {}", propertyName, e.getMessage());
                return new McpSchema.JsonSchema("string", null, null, null, null, null);
            }
        }
        logger.warn("Property '{}' has unexpected schema type: {}. Defaulting to string.", propertyName, (schemaObject != null ? schemaObject.getClass().getName() : "null"));
        return new McpSchema.JsonSchema("string", null, null, null, null, null);
    }
}
