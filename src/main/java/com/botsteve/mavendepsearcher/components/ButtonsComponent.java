package com.botsteve.mavendepsearcher.components;

import static com.botsteve.mavendepsearcher.utils.FxUtils.getErrorAlertAndCloseProgressBar;
import static com.botsteve.mavendepsearcher.utils.FxUtils.showAlert;
import static com.botsteve.mavendepsearcher.utils.FxUtils.showError;
import static com.botsteve.mavendepsearcher.utils.ProxyUtil.getRepoNameFromUrl;
import static com.botsteve.mavendepsearcher.utils.Utils.arePropertiesConfiguredAndValid;
import static com.botsteve.mavendepsearcher.utils.Utils.collectLatestVersions;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.Data;
import com.botsteve.mavendepsearcher.tasks.BuildRepositoriesTask;
import com.botsteve.mavendepsearcher.tasks.DependencyDownloaderTask;
import com.botsteve.mavendepsearcher.tasks.DependencyLoadingTask;

@Data
public class ButtonsComponent {

  private static AtomicBoolean isDownloaded = new AtomicBoolean(false);
  private final TableViewComponent tableViewComponent;

  public ToolBar getToolBar(Stage primaryStage, ProgressBar progressBar, Label progressLabel) {
    var openButton = createOpenDirectoryButton(primaryStage, progressBar, progressLabel);
    var downloadButton = createDownloadSelectedButton(progressBar, progressLabel);
    var buildSelectedButton = createBuildSelectedButton(progressBar, progressLabel);
    var buildAggregatedLicenseButton = createBuildAggregatedLicenseButton(progressBar, progressLabel);
    return new ToolBar(openButton, downloadButton, buildSelectedButton, buildAggregatedLicenseButton);
  }

  public Button createBuildAggregatedLicenseButton(ProgressBar progressBar, Label progressLabel) {
    Button buildButton = new Button("Create Aggregated License");
    buildButton.setOnAction(event -> showAlert("Feature not yet implemented!"));
    return buildButton;
  }

  public Button createBuildSelectedButton(ProgressBar progressBar, Label progressLabel) {
    Button buildButton = new Button("Build Selected");
    buildButton.setOnAction(event -> {
      if (!arePropertiesConfiguredAndValid() || !areDependenciesSelected()) {
        return;
      }
      BuildRepositoriesTask task = new BuildRepositoriesTask(progressBar, progressLabel);
      task.setOnSucceeded(workerStateEvent -> {
        var successfulBuiltReposToJavaVersion = task.getValue();
        tableViewComponent.getSelectedDependencies()
            .forEach(dependencyNode ->
                         successfulBuiltReposToJavaVersion.entrySet().stream()
                             .filter(entry -> entry.getKey().equals(getRepoNameFromUrl(dependencyNode.getScmUrl())))
                             .findFirst()
                             .ifPresent(entry -> dependencyNode.setBuildWith(entry.getValue()))
            );
        // simulating refresh
        tableViewComponent.updateTreeViewWithFilteredDependencies(tableViewComponent.getFilterInput().getText());
      });
      Thread buildThread = new Thread(task);
      progressBar.progressProperty().bind(task.progressProperty());
      buildThread.start();
    });
    return buildButton;
  }

  public Button createDownloadSelectedButton(ProgressBar progressBar, Label progressLabel) {
    Button downloadButton = new Button("Download Selected");
    downloadButton.setOnAction(event -> {
      var urlToVersion = collectLatestVersions(tableViewComponent.getSelectedDependencies());
      if (urlToVersion.isEmpty()) {
        Platform.runLater(() -> showError("No dependencies selected!"));
        return;
      }
      DependencyDownloaderTask task = new DependencyDownloaderTask(urlToVersion, progressBar, progressLabel);
      task.setOnSucceeded(workerStateEvent -> {
        isDownloaded.set(true);
        var versionToCheckoutTag = task.getValue();
        tableViewComponent.getSelectedDependencies()
            .forEach(dependencyNode ->
                         versionToCheckoutTag.entrySet().stream()
                             .filter(entry -> entry.getKey().equals(getRepoNameFromUrl(dependencyNode.getScmUrl())))
                             .findFirst()
                             .ifPresent(entry -> dependencyNode.setCheckoutTag(entry.getValue()))
            );
        tableViewComponent.updateTreeViewWithFilteredDependencies(tableViewComponent.getFilterInput().getText());
      });
      Thread thread = new Thread(task);
      progressBar.progressProperty().bind(task.progressProperty());
      thread.start();
    });
    return downloadButton;
  }

  public Button createOpenDirectoryButton(Stage primaryStage, ProgressBar progressBar, Label progressLabel) {
    Button openButton = new Button("Open Directory");
    openButton.setOnAction(event -> {
      var mavenHome = System.getenv("MAVEN_HOME");
      var javaHome = System.getenv("JAVA_HOME");
      if (mavenHome == null || mavenHome.isEmpty() || javaHome == null || javaHome.isEmpty()) {
        showError("MAVEN_HOME or JAVA_HOME environment variables are not configured, please configure and try again!");
        return;
      }
      DirectoryChooser directoryChooser = new DirectoryChooser();
      File selectedDirectory = directoryChooser.showDialog(primaryStage);
      if (selectedDirectory != null) {
        if (!new File(selectedDirectory, "pom.xml").exists()) {
          getErrorAlertAndCloseProgressBar("Not a valid maven project! Please open the root directory of a maven project",
                                           progressBar, progressLabel);
          return;
        }
        reset();
        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressLabel.setText("Loading dependencies...");

        var task = new DependencyLoadingTask(selectedDirectory.getPath(), progressBar, progressLabel,
                                             tableViewComponent.getTreeTableView());

        task.setOnSucceeded(workerStateEvent -> {
          tableViewComponent.setAllDependencies(FXCollections.observableSet(task.getValue()));
          tableViewComponent.updateTreeView(tableViewComponent.getAllDependencies());
          tableViewComponent.updateTreeViewWithFilteredDependencies(tableViewComponent.getFilterInput().getText());
        });

        progressBar.progressProperty().bind(task.progressProperty());
        new Thread(task).start();
      }
    });
    return openButton;
  }

  private void reset() {
    tableViewComponent.getSelectAllCheckBox().setSelected(false);
    tableViewComponent.getFilterInput().setText("");
    tableViewComponent.setAllDependencies(FXCollections.observableSet(new HashSet<>()));
    tableViewComponent.setSelectedDependencies(FXCollections.observableSet(new HashSet<>()));
    tableViewComponent.updateTreeView(new HashSet<>());
  }

  private boolean areDependenciesSelected() {
    if (tableViewComponent.getSelectedDependencies().isEmpty() || !isDownloaded.get()) {
      showError("No dependencies downloaded!");
      return false;
    }
    return true;
  }
}
