package com.brunorozendo.mcpclientgui;

import com.brunorozendo.mcpclientgui.service.DatabaseService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main application class for the MCP Client GUI.
 * 
 * This JavaFX application provides a graphical interface for interacting with
 * MCP (Model Context Protocol) servers and Language Learning Models (LLMs).
 */
public class McpClientGuiApp extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(McpClientGuiApp.class);
    
    // Application window configuration
    private static final String APP_TITLE = "MCP Client GUI";
    private static final int INITIAL_WIDTH = 1200;
    private static final int INITIAL_HEIGHT = 800;
    private static final int MIN_WIDTH = 800;
    private static final int MIN_HEIGHT = 600;
    
    // Resource paths
    private static final String MAIN_VIEW_FXML = "/fxml/main-view.fxml";
    private static final String STYLES_CSS = "/css/styles.css";
    private static final String ICON_PATH = "/images/icon.png";

    /**
     * Starts the JavaFX application by loading the main view and configuring the primary stage.
     * 
     * @param primaryStage The primary stage for this application
     * @throws IOException If the FXML file cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent rootNode = loadMainView();
        Scene mainScene = createMainScene(rootNode);
        configurePrimaryStage(primaryStage, mainScene);
        primaryStage.show();
    }
    
    /**
     * Loads the main view from the FXML file.
     * 
     * @return The root node of the loaded FXML
     * @throws IOException If the FXML file cannot be loaded
     */
    private Parent loadMainView() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(MAIN_VIEW_FXML));
        return fxmlLoader.load();
    }
    
    /**
     * Creates the main scene with the specified root node and applies styling.
     * 
     * @param rootNode The root node for the scene
     * @return The configured scene
     */
    private Scene createMainScene(Parent rootNode) {
        Scene scene = new Scene(rootNode, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(getClass().getResource(STYLES_CSS).toExternalForm());
        return scene;
    }
    
    /**
     * Configures the primary stage with title, size constraints, and icon.
     * 
     * @param stage The stage to configure
     * @param scene The scene to set on the stage
     */
    private void configurePrimaryStage(Stage stage, Scene scene) {
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        
        loadApplicationIcon(stage);
    }
    
    /**
     * Attempts to load and set the application icon.
     * If the icon is not found, the application continues without it.
     * 
     * @param stage The stage to set the icon on
     */
    private void loadApplicationIcon(Stage stage) {
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream(ICON_PATH)));
            logger.debug("Application icon loaded successfully");
        } catch (Exception e) {
            logger.debug("Application icon not found at {}. Continuing without icon.", ICON_PATH);
        }
    }

    /**
     * Called when the application is stopping.
     * Ensures proper cleanup of resources, particularly the database connection.
     * 
     * @throws Exception If an error occurs during shutdown
     */
    @Override
    public void stop() throws Exception {
        logger.info("Application shutting down...");
        DatabaseService.getInstance().close();
        super.stop();
        logger.info("Application shutdown complete");
    }

    /**
     * Main entry point for the application.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
