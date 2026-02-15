package com.botsteve.mavendepsearcher;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import com.botsteve.mavendepsearcher.logging.MemoryLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.botsteve.mavendepsearcher.utils.Utils.createSettingsFile;
import static com.botsteve.mavendepsearcher.views.LoginViewer.LOGIN_VIEWER;

public class MavenDependencyViewer extends Application {


    @Override
    public void start(Stage primaryStage) {
        LOGIN_VIEWER.showPasswordDialog(primaryStage);
        createSettingsFile();
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(MemoryLogger::logMemoryUsage, 0, 10, TimeUnit.SECONDS);
        }

    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        // Add any other cleanup code here
        Platform.exit();
        System.exit(0);
    }

}
