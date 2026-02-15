package com.botsteve.mavendepsearcher.tasks;

import static com.botsteve.mavendepsearcher.utils.FxUtils.getErrorAlertAndCloseProgressBar;

import java.util.Set;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import lombok.extern.slf4j.Slf4j;
import com.botsteve.mavendepsearcher.model.DependencyNode;
import com.botsteve.mavendepsearcher.model.ProjectType;
import com.botsteve.mavendepsearcher.service.DependencyAnalyzerService;
import com.botsteve.mavendepsearcher.service.GradleScmUrlFetcherService;
import com.botsteve.mavendepsearcher.service.ScmUrlFetcherService;

@Slf4j
public class DependencyLoadingTask extends Task<Set<DependencyNode>> {

  private final String projectDir;
  private final ProgressBar progressBar;
  private final Label progressLabel;
  private final TreeTableView<DependencyNode> treeTableView;

  public DependencyLoadingTask(String projectDir, ProgressBar progressBar, Label progressLabel,
                               TreeTableView<DependencyNode> treeTableView) {
    this.projectDir = projectDir;
    this.progressBar = progressBar;
    this.progressLabel = progressLabel;
    this.treeTableView = treeTableView;
  }

  @Override
  protected Set<DependencyNode> call() throws Exception {
    Platform.runLater(() -> progressLabel.setText("Loading dependencies..."));
    var dependencies = DependencyAnalyzerService.getDependencies(projectDir);

    Platform.runLater(() -> progressLabel.setText("Fetching SCM URLs..."));
    ProjectType projectType = DependencyAnalyzerService.getProjectType(projectDir);
    if (projectType == ProjectType.MAVEN) {
      ScmUrlFetcherService.fetchScmUrls(projectDir, dependencies);
    } else {
      // For Gradle projects, fetch SCM URLs from Maven Central POMs
      GradleScmUrlFetcherService.fetchScmUrls(dependencies);
    }
    return dependencies;
  }

  @Override
  protected void succeeded() {
    Set<DependencyNode> dependencies = getValue();
    TreeItem<DependencyNode> rootItem = new TreeItem<>(new DependencyNode("Root", "", ""));
    for (DependencyNode node : dependencies) {
      rootItem.getChildren().add(createTreeItem(node));
    }
    treeTableView.setRoot(rootItem);
    progressBar.setVisible(false);
    progressLabel.setVisible(false);
  }

  @Override
  protected void failed() {
    super.failed();
    Throwable exception = getException();
    if (exception != null) {
      log.error(exception.getMessage(), exception);
      ProjectType projectType = DependencyAnalyzerService.getProjectType(projectDir);
      String errorMsg = projectType == ProjectType.GRADLE
          ? "Failed to analyze Gradle project. Make sure the project compiles " +
            "and Gradle Wrapper (gradlew) or system Gradle is available.\n" +
            "Error: " + exception.getMessage()
          : "Make sure the maven project compiles and has a error-free root pom.xml.\n" +
            "If you are behind a proxy, check maven proxy in ~/.m2/settings.xml.";
      getErrorAlertAndCloseProgressBar(errorMsg, progressBar, progressLabel);
    }
  }

  private TreeItem<DependencyNode> createTreeItem(DependencyNode node) {
    TreeItem<DependencyNode> treeItem = new TreeItem<>(node);
    if (node.getChildren() != null) {
      for (DependencyNode child : node.getChildren()) {
        treeItem.getChildren().add(createTreeItem(child));
      }
    }
    return treeItem;
  }
}
