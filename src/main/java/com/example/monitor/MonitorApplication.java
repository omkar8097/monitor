package com.example.monitor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.GraphicsEnvironment;

@SpringBootApplication
public class MonitorApplication extends Application {

    private static ConfigurableApplicationContext springContext;

    @Override
    public void init() throws Exception {
        // Start Spring Boot backend context in the background
        String[] args = getParameters().getRaw().toArray(new String[0]);
        springContext = new SpringApplicationBuilder()
                .sources(MonitorApplication.class)
                .run(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Create a JavaFX WebView to embed the Spring Boot web dashboard
        WebView webView = new WebView();
        webView.getEngine().load("http://localhost:8080/");

        // Build the desktop application window scene
        Scene scene = new Scene(webView, 1200, 800);
        primaryStage.setTitle("API Console & System Monitor");
        primaryStage.setScene(scene);
        
        // Handle window close cleanly
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // Terminate Spring Boot backend when window closes
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        // Headless check: allows running tests or CLI environments without starting GUI
        boolean headless = GraphicsEnvironment.isHeadless() || 
                           "true".equals(System.getProperty("java.awt.headless"));

        if (headless) {
            System.out.println("Running in HEADLESS mode. Launching Spring Boot server only...");
            springContext = org.springframework.boot.SpringApplication.run(MonitorApplication.class, args);
        } else {
            System.out.println("Running in GRAPHICAL mode. Launching JavaFX Desktop Application...");
            Application.launch(MonitorApplication.class, args);
        }
    }
}
