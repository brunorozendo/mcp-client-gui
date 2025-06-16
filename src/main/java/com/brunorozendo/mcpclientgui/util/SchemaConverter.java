package com.brunorozendo.mcpclientgui.util;

import com.brunorozendo.mcpclientgui.model.OllamaApi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for converting between MCP and Ollama schema formats.
 * 
 * This converter handles the transformation of Model Context Protocol (MCP) schemas
 * into formats compatible with the Ollama API. This is essential for exposing MCP
 * server capabilities (tools, functions) to Ollama language models.
 * 
 * The conversion process handles:
 * - Tool definitions with their input schemas
 * - Nested object and array structures
 * - Type mappings and property constraints
 * - Special fields like enums and formats
 */
public class SchemaConverter {

    private static final Logger logger = LoggerFactory.getLogger(SchemaConverter.class);
    
    // Schema field names
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_ENUM = "enum";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_ITEMS = "items";
    
    // Default values for missing schemas
    private static final String DEFAULT_TYPE = "string";
    private static final String DEFAULT_DESCRIPTION_MISSING = "No description provided";
    private static final String DEFAULT_DESCRIPTION_UNDEFINED = "Undefined schema";
    private static final String DEFAULT_DESCRIPTION_EMPTY_OBJECT = "No parameters";
    private static final String DEFAULT_ARRAY_ITEM_DESCRIPTION = "Array item";
    
    // Jackson ObjectMapper for generic conversions
    private static final ObjectMapper objectMapper = createObjectMapper();
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SchemaConverter() {
        throw new AssertionError("SchemaConverter should not be instantiated");
    }
    
    /**
     * Creates and configures the ObjectMapper for schema conversions.
     * 
     * @return Configured ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Converts a list of MCP tools into Ollama-compatible tool definitions.
     * 
     * Each MCP tool is transformed into an Ollama tool with:
     * - Type set to "function"
     * - Function name and description preserved
     * - Input schema converted to Ollama parameter format
     *
     * @param mcpTools List of MCP tool definitions (may be null)
     * @return List of Ollama-compatible tools, empty list if input is null
     */
    public static List<OllamaApi.Tool> convertMcpToolsToOllamaTools(List<McpSchema.Tool> mcpTools) {
        if (mcpTools == null || mcpTools.isEmpty()) {
            logger.debug("No MCP tools to convert");
            return Collections.emptyList();
        }
        
        logger.debug("Converting {} MCP tools to Ollama format", mcpTools.size());
        
        return mcpTools.stream()
                .map(SchemaConverter::convertSingleTool)
                .filter(Objects::nonNull) // Skip any failed conversions
                .collect(Collectors.toList());
    }
    
    /**
     * Converts a single MCP tool to an Ollama tool.
     * 
     * @param mcpTool The MCP tool to convert
     * @return Converted Ollama tool, or null if conversion fails
     */
    private static OllamaApi.Tool convertSingleTool(McpSchema.Tool mcpTool) {
        if (mcpTool == null) {
            logger.warn("Attempted to convert null MCP tool");
            return null;
        }
        
        try {
            String toolName = mcpTool.name();
            String toolDescription = mcpTool.description();
            
            if (toolName == null || toolName.trim().isEmpty()) {
                logger.warn("MCP tool has no name, skipping conversion");
                return null;
            }
            
            // Convert the input schema
            OllamaApi.JsonSchema parametersSchema = convertMcpInputSchemaToOllamaParamsSchema(mcpTool.inputSchema());
            
            // Create the Ollama function
            OllamaApi.OllamaFunction ollamaFunction = new OllamaApi.OllamaFunction(
                    toolName,
                    toolDescription != null ? toolDescription : DEFAULT_DESCRIPTION_MISSING,
                    parametersSchema
            );
            
            // Create and return the tool
            OllamaApi.Tool tool = OllamaApi.Tool.function(ollamaFunction);
            
            logger.trace("Successfully converted tool: {}", toolName);
            return tool;
            
        } catch (Exception e) {
            logger.error("Error converting MCP tool: {}", mcpTool.name(), e);
            return null;
        }
    }

    /**
     * Converts an MCP input schema to Ollama parameters schema format.
     * 
     * If the input schema is null, returns an empty object schema to indicate
     * that the function takes no parameters.
     *
     * @param mcpInputSchema The MCP input schema (may be null)
     * @return Ollama-compatible parameters schema
     */
    private static OllamaApi.JsonSchema convertMcpInputSchemaToOllamaParamsSchema(McpSchema.JsonSchema mcpInputSchema) {
        if (mcpInputSchema == null) {
            // Return empty object schema for tools with no parameters
            return OllamaApi.JsonSchema.object(
                DEFAULT_DESCRIPTION_EMPTY_OBJECT, 
                new HashMap<>(), 
                new ArrayList<>()
            );
        }
        
        return convertMcpSchemaRecursive(mcpInputSchema);
    }

    /**
     * Recursively converts an MCP JSON schema to Ollama format.
     * 
     * This method handles:
     * - Basic types (string, number, boolean, etc.)
     * - Object types with nested properties
     * - Array types with item schemas
     * - Special constraints (enums, formats, required fields)
     *
     * @param mcpSchema The MCP schema to convert
     * @return Converted Ollama schema
     */
    public static OllamaApi.JsonSchema convertMcpSchemaRecursive(McpSchema.JsonSchema mcpSchema) {
        if (mcpSchema == null) {
            logger.debug("Null schema encountered, using default string type");
            return new OllamaApi.JsonSchema(DEFAULT_TYPE, DEFAULT_DESCRIPTION_UNDEFINED);
        }

        String type = mcpSchema.type();
        if (type == null) {
            logger.warn("Schema has no type specified, defaulting to string");
            type = DEFAULT_TYPE;
        }
        
        // Extract basic fields
        List<String> required = mcpSchema.required() != null ? new ArrayList<>(mcpSchema.required()) : null;
        Map<String, Object> mcpProperties = mcpSchema.properties();
        
        // Initialize schema components
        SchemaComponents components = extractSchemaComponents(mcpProperties, type);
        
        // Build and return the Ollama schema
        return new OllamaApi.JsonSchema(
            type,
            components.description,
            components.properties,
            components.items,
            required,
            components.enumValues,
            components.format
        );
    }
    
    /**
     * Container for extracted schema components.
     */
    private static class SchemaComponents {
        String description;
        Map<String, OllamaApi.JsonSchema> properties;
        OllamaApi.JsonSchema items;
        List<Object> enumValues;
        String format;
    }
    
    /**
     * Extracts schema components from MCP properties map.
     * 
     * @param mcpProperties The MCP properties map
     * @param type The schema type
     * @return Extracted components
     */
    private static SchemaComponents extractSchemaComponents(Map<String, Object> mcpProperties, String type) {
        SchemaComponents components = new SchemaComponents();
        
        if (mcpProperties == null) {
            return components;
        }
        
        // Extract basic fields
        components.description = extractString(mcpProperties, FIELD_DESCRIPTION);
        components.enumValues = extractList(mcpProperties, FIELD_ENUM);
        components.format = extractString(mcpProperties, FIELD_FORMAT);
        
        // Handle type-specific fields
        if (OllamaApi.TYPE_OBJECT.equals(type)) {
            components.properties = extractObjectProperties(mcpProperties);
        } else if (OllamaApi.TYPE_ARRAY.equals(type)) {
            components.items = extractArrayItems(mcpProperties);
        }
        
        return components;
    }
    
    /**
     * Safely extracts a string value from a map.
     * 
     * @param map The map to extract from
     * @param key The key to look up
     * @return The string value, or null if not found or not a string
     */
    private static String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }
    
    /**
     * Safely extracts a list value from a map.
     * 
     * @param map The map to extract from
     * @param key The key to look up
     * @return The list value, or null if not found or not a list
     */
    @SuppressWarnings("unchecked")
    private static List<Object> extractList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<Object>) value : null;
    }

    /**
     * Extracts and converts object properties from MCP schema.
     * 
     * @param mcpProperties The MCP properties map
     * @return Map of property names to their schemas
     */
    private static Map<String, OllamaApi.JsonSchema> extractObjectProperties(Map<String, Object> mcpProperties) {
        Object propsField = mcpProperties.get(FIELD_PROPERTIES);
        
        if (!(propsField instanceof Map)) {
            return null;
        }
        
        Map<String, OllamaApi.JsonSchema> properties = new HashMap<>();
        Map<?, ?> propsMap = (Map<?, ?>) propsField;
        
        for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
            String propKey = String.valueOf(entry.getKey());
            
            try {
                McpSchema.JsonSchema propertyMcpSchema = convertToMcpSchema(entry.getValue(), propKey);
                OllamaApi.JsonSchema convertedSchema = convertMcpSchemaRecursive(propertyMcpSchema);
                properties.put(propKey, convertedSchema);
            } catch (Exception e) {
                logger.warn("Failed to convert property '{}', using default string schema", propKey, e);
                properties.put(propKey, new OllamaApi.JsonSchema(DEFAULT_TYPE, DEFAULT_DESCRIPTION_MISSING));
            }
        }
        
        return properties.isEmpty() ? null : properties;
    }

    /**
     * Extracts and converts array items schema from MCP schema.
     * 
     * @param mcpProperties The MCP properties map
     * @return The items schema for the array
     */
    private static OllamaApi.JsonSchema extractArrayItems(Map<String, Object> mcpProperties) {
        Object itemsField = mcpProperties.get(FIELD_ITEMS);
        
        if (itemsField == null) {
            logger.debug("Array schema has no items definition, defaulting to string items");
            return new OllamaApi.JsonSchema(DEFAULT_TYPE, DEFAULT_ARRAY_ITEM_DESCRIPTION);
        }
        
        try {
            McpSchema.JsonSchema itemsMcpSchema = convertToMcpSchema(itemsField, "array items");
            return convertMcpSchemaRecursive(itemsMcpSchema);
        } catch (Exception e) {
            logger.warn("Failed to convert array items schema, using default", e);
            return new OllamaApi.JsonSchema(DEFAULT_TYPE, DEFAULT_ARRAY_ITEM_DESCRIPTION);
        }
    }

    /**
     * Converts a generic object to an MCP JsonSchema.
     * 
     * Handles various input types:
     * - Already a JsonSchema: returns as-is
     * - Map: converts using Jackson
     * - Other: creates default string schema
     * 
     * @param schemaObject The object to convert
     * @param propertyName The property name for logging
     * @return Converted MCP schema
     */
    private static McpSchema.JsonSchema convertToMcpSchema(Object schemaObject, String propertyName) {
        if (schemaObject instanceof McpSchema.JsonSchema) {
            return (McpSchema.JsonSchema) schemaObject;
        }
        
        if (schemaObject instanceof Map) {
            try {
                // Use Jackson to convert Map to JsonSchema record
                return objectMapper.convertValue(schemaObject, McpSchema.JsonSchema.class);
            } catch (IllegalArgumentException e) {
                logger.warn("Could not convert property '{}' from Map to JsonSchema: {}", 
                          propertyName, e.getMessage());
                return createDefaultSchema();
            }
        }
        
        logger.warn("Property '{}' has unexpected schema type: {}. Using default string schema.", 
                   propertyName, 
                   schemaObject != null ? schemaObject.getClass().getSimpleName() : "null");
        
        return createDefaultSchema();
    }
    
    /**
     * Creates a default string schema.
     * 
     * @return Default MCP JsonSchema
     */
    private static McpSchema.JsonSchema createDefaultSchema() {
        return new McpSchema.JsonSchema(DEFAULT_TYPE, null, null, null, null, null);
    }

    /**
     * Validates if a converted Ollama schema is valid.
     * 
     * @param schema The schema to validate
     * @return True if the schema appears valid
     */
    public static boolean isValidOllamaSchema(OllamaApi.JsonSchema schema) {
        if (schema == null) {
            return false;
        }
        
        // Must have a type
        if (schema.type() == null || schema.type().trim().isEmpty()) {
            return false;
        }
        
        // If it's an object, it should have properties or be explicitly empty
        if (OllamaApi.TYPE_OBJECT.equals(schema.type()) && schema.properties() == null) {
            logger.debug("Object schema has null properties map");
        }
        
        // If it's an array, it should have items schema
        if (OllamaApi.TYPE_ARRAY.equals(schema.type()) && schema.items() == null) {
            logger.warn("Array schema has no items definition");
        }
        
        return true;
    }
}
