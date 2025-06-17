# JavaFX Fat JAR Build Guide

## Building the Application

### Option 1: Shadow JAR (Fat JAR) - RECOMMENDED ✅
This creates a single JAR file containing all dependencies including JavaFX.

```bash
# Clean and build the shadow JAR
./gradlew clean shadowJar

# Run the application
java -jar build/libs/mcp-client-gui-1.0-SNAPSHOT-all.jar
```

### Option 2: Native Installer using Shadow JAR
Create a native installer (DMG on macOS, MSI on Windows, DEB on Linux) from the shadow JAR.

```bash
# Build native installer
./gradlew jpackageFromShadow

# The installer will be in build/jpackage/
# On macOS: MCP Client GUI-1.0-SNAPSHOT.dmg
# On Windows: MCP Client GUI-1.0-SNAPSHOT.msi
# On Linux: mcp-client-gui_1.0-SNAPSHOT-1_amd64.deb
```

### Option 3: Custom Runtime Image with jlink ✅
Creates a custom Java runtime with only the modules your application needs. The build is configured to handle automatic modules by merging them.

```bash
# Build custom runtime image
./gradlew jlink

# Run the application (macOS/Linux)
./build/image/bin/mcp-client-gui

# Run the application (Windows)
build\image\bin\mcp-client-gui.bat
```

**Advantages**:
- No Java installation required on target machine
- Smaller than full JDK (~150-200MB)
- Optimized for your application

## How jlink Works with Automatic Modules

The `jlink` tool normally cannot process automatic modules (JARs without module-info). Your project uses several libraries that are automatic modules:
- commonmark
- logback
- controlsfx
- sqlite-jdbc
- And others...

However, the build is configured with the Beryx jlink plugin which handles this by:
1. Creating a merged module that includes all automatic modules
2. Generating delegating modules for proper module resolution
3. Building a custom runtime that includes everything needed

## Recommendations

1. **For Development**: Use the shadow JAR approach - it's simple and works reliably.
   ```bash
   ./gradlew shadowJar
   java -jar build/libs/mcp-client-gui-1.0-SNAPSHOT-all.jar
   ```

2. **For Distribution to End Users**: 
   - **Option A**: Use jpackage with the shadow JAR to create native installers.
     ```bash
     ./gradlew jpackageFromShadow
     ```
   - **Option B**: Use jlink to create a custom runtime (no Java required on target machine).
     ```bash
     ./gradlew jlink
     # Then distribute the build/image directory
     ```

3. **For Production Deployment**: The jlink approach is recommended as it:
   - Creates a self-contained application
   - Doesn't require Java to be installed
   - Is optimized for your specific application
   - Can be further packaged with jpackage for native installers

## Troubleshooting

### "JavaFX runtime components are missing" error
- Make sure you're running the `-all.jar` file, not the regular JAR
- The shadow JAR includes all JavaFX dependencies for your current platform

### Platform-specific builds
The build automatically detects your OS and includes the appropriate JavaFX native libraries:
- Windows: `win`
- Linux: `linux`
- macOS Intel: `mac`
- macOS Apple Silicon: `mac-aarch64`

### Cross-platform builds
To build for other platforms, modify the `platform` variable in build.gradle or create separate build configurations.

## Shadow JAR Contents

The shadow JAR (`mcp-client-gui-1.0-SNAPSHOT-all.jar`) contains:
- Your application classes
- All dependencies (JavaFX, Jackson, Logback, etc.)
- Platform-specific JavaFX native libraries
- A special launcher class that bypasses module system issues

Size: ~50-70MB (includes all dependencies)

## Quick Commands Reference

```bash
# Build shadow JAR
./gradlew shadowJar

# Run shadow JAR
java -jar build/libs/mcp-client-gui-1.0-SNAPSHOT-all.jar

# Build custom runtime with jlink
./gradlew jlink

# Run jlink image (macOS/Linux)
./build/image/bin/mcp-client-gui

# Run jlink image (Windows)
build\image\bin\mcp-client-gui.bat

# Build native installer
./gradlew jpackageFromShadow

# Clean build artifacts
./gradlew clean

# Build and run shadow JAR in one command
./gradlew shadowJar && java -jar build/libs/mcp-client-gui-1.0-SNAPSHOT-all.jar

# Build and run jlink in one command (macOS/Linux)
./gradlew jlink && ./build/image/bin/mcp-client-gui
```
