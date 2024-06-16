package org.example;


import static org.example.utils.Utils.createSettingsFile;
import static org.example.views.LoginViewer.LOGIN_VIEWER;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.stage.Stage;
import org.example.logging.MemoryLogger;

public class MavenDependencyViewer extends Application {

  @Override
  public void start(Stage primaryStage) {
    LOGIN_VIEWER.showPasswordDialog(primaryStage);
    createSettingsFile();
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(MemoryLogger::logMemoryUsage, 0, 10, TimeUnit.SECONDS);
  }

  public static void main(String[] args) {
    launch(args);
  }
}
