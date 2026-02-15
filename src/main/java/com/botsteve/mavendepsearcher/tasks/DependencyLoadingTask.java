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
import com.botsteve.mavendepsearcher.service.DependencyAnalyzerService;
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
    ScmUrlFetcherService.fetchScmUrls(projectDir, dependencies);
    return dependencies;
  }

  @Override
  protected void succeeded() {
    Platform.runLater(() -> progressLabel.setText("Fetching SCM URLs..."));
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
      getErrorAlertAndCloseProgressBar("""
                                           Make sure the maven project compiles and has a error-free root pom.xml.
                                           If you are behind a proxy, check maven proxy in ~/.m2/settings.xml.
                                           """,
                                       progressBar,
                                       progressLabel);
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
