# MCP Client GUI

A JavaFX-based graphical user interface for interacting with Large Language Models through the Model Context Protocol (MCP). This application merges the functionality of a JavaFX chat interface with an MCP client, allowing users to chat with AI models that have access to MCP-enabled tools and resources.

## Features

- **Modern JavaFX Interface**: Clean, intuitive chat interface with multiple chat sessions
- **MCP Integration**: Connect to Model Context Protocol servers for enhanced AI capabilities
- **Ollama Support**: Built-in support for Ollama language models
- **Settings Management**: Easy configuration of LLM models and MCP configurations
- **Real-time Chat**: Asynchronous message processing with thinking indicators
- **Tool Execution**: Visual feedback when AI uses tools through MCP

## Prerequisites

- Java 21 or later
- [Ollama](https://ollama.ai/) installed and running locally
- MCP server(s) configured and available
- An `mcp.json` configuration file

## Configuration

### MCP Configuration File (mcp.json)

Create an `mcp.json` file to define your MCP servers. Example:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/your/mcp-server-filesystem.jar",
        "/path/to/working/directory"
      ]
    },
    "web-search": {
      "command": "python",
      "args": [
        "/path/to/mcp-server-web-search/main.py"
      ],
      "env": {
        "API_KEY": "your-api-key"
      }
    }
  }
}
```

### Application Settings

When you first run the application, click the "Settings" button to configure:

1. **LLM Model**: The Ollama model name (e.g., `llama3.2`, `qwen:7b`)
2. **MCP Config File**: Path to your `mcp.json` file
3. **Ollama Base URL**: URL of your Ollama instance (default: `http://localhost:11434`)

## Building and Running

### Using Gradle

```bash
# Clone or create the project
cd mcp-client-gui

# Build the project
./gradlew build

# Run the application
./gradlew run
```

### Creating a Distribution

```bash
# Create distribution packages
./gradlew distZip distTar

# The distributions will be created in build/distributions/
```

## Usage

1. **Start the Application**: Run using `./gradlew run` or execute the built JAR
2. **Configure Settings**: Click the "Settings" button and configure your LLM model and MCP settings
3. **Create a New Chat**: Click "New Chat" to start a conversation
4. **Chat with AI**: Type messages and interact with the AI, which can use MCP tools when needed
5. **Monitor Status**: The status bar shows the current state (thinking, executing tools, etc.)

## Project Structure

```
src/main/java/com/brunorozendo/mcpclientgui/
├── McpClientGuiApp.java              # Main application class
├── controller/
│   ├── MainController.java           # Main UI controller
│   └── SettingsController.java       # Settings dialog controller
├── model/
│   ├── AppSettings.java             # Application settings model
│   ├── Chat.java                    # Chat session model
│   ├── Message.java                 # Chat message model
│   ├── McpConfig.java               # MCP configuration model
│   └── OllamaApi.java               # Ollama API models
├── service/
│   ├── McpConfigLoader.java         # MCP configuration loader
│   └── OllamaApiClient.java         # Ollama API client
├── control/
│   ├── GuiChatController.java       # Chat logic controller
│   ├── McpConnectionManager.java    # MCP connection management
│   └── SystemPromptBuilder.java     # System prompt builder
└── util/
    └── SchemaConverter.java         # MCP to Ollama schema converter
```

## Dependencies

- **JavaFX**: UI framework
- **MCP SDK**: Model Context Protocol integration
- **Jackson**: JSON processing
- **SLF4J + Logback**: Logging
- **ControlsFX**: Enhanced UI controls

## Logging

Logs are written to:
- Console output (INFO level and above)
- `logs/mcp-client-gui.log` (with rotation)

Log levels can be adjusted in `src/main/resources/logback.xml`.

## Troubleshooting

### Common Issues

1. **"Not configured" message**: Ensure you've set up the LLM model and MCP config file in Settings
2. **Connection errors**: Verify Ollama is running and accessible at the configured URL
3. **MCP tool errors**: Check that your MCP servers are properly configured and running
4. **JavaFX issues**: Ensure you're using Java 21+ with JavaFX modules

### Debug Mode

To enable debug logging, modify `logback.xml`:

```xml
<logger name="com.brunorozendo.mcpclientgui" level="DEBUG" />
<logger name="io.modelcontextprotocol" level="DEBUG" />
```

## License

This project builds upon the Model Context Protocol SDK and follows its licensing terms.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Support

For issues or questions:
1. Check the logs for error details
2. Verify your MCP configuration
3. Ensure Ollama is running and accessible
4. Create an issue with relevant log output
