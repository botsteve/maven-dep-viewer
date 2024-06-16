package org.example.tasks;

import static org.example.tasks.CheckoutTagsTask.checkoutTag;
import static org.example.utils.FxUtils.getErrorAlertAndCloseProgressBar;
import static org.example.utils.FxUtils.showAlert;
import static org.example.utils.FxUtils.updateProgressBarAndLabel;
import static org.example.utils.ProxyUtil.configureProxyIfEnvAvailable;
import static org.example.utils.ProxyUtil.getProxyException;
import static org.example.utils.ProxyUtil.getRepoNameFromUrl;
import static org.example.utils.Utils.getRepositoriesPath;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

@Slf4j
@RequiredArgsConstructor
public class DependencyDownloaderTask extends Task<Map<String, String>> {

  private final Map<String, String> scmToVersionRepos;
  private final ProgressBar progressBar;
  private final Label progressLabel;
  private Map.Entry<String, String> currentRepo;
  private final Map<String, String> repoToCheckoutTag = new HashMap<>();


  @Override
  protected void failed() {
    super.failed();
    Throwable exception = getException();
    if (exception != null) {
      log.error(exception.getMessage(), exception);
      getErrorAlertAndCloseProgressBar(getProxyException(currentRepo, exception), progressBar, progressLabel);
    }
  }

  @Override
  protected void succeeded() {
    showAlert("Dependencies downloaded task finished!");
    progressBar.setVisible(false);
    progressLabel.setVisible(false);
  }

  @Override
  protected Map<String, String> call() throws URISyntaxException, IOException, GitAPIException {
    updateProgressBarAndLabel("Cleaning up previous downloaded repositories", progressBar, progressLabel);
    cleanUpDownloadedDependencies();
    for (Map.Entry<String, String> versionScm : scmToVersionRepos.entrySet()) {
      Platform.runLater(() -> progressLabel.setText("Downloading: " + getRepoNameFromUrl(versionScm.getKey())));
      currentRepo = versionScm;
      File localRepoDir = new File(getRepositoriesPath() + getRepoNameFromUrl(versionScm.getKey()));
      configureProxyIfEnvAvailable();
      Git.cloneRepository()
          .setURI(versionScm.getKey())
          .setDirectory(localRepoDir)
          .setCloneAllBranches(true)
          .setDepth(1)  // Limit to the latest commit
          .call();
      var checkoutTag = checkoutTag(localRepoDir, versionScm.getValue());
      repoToCheckoutTag.put(getRepoNameFromUrl(versionScm.getKey()), checkoutTag.replace("refs/tags/", ""));
    }
    return repoToCheckoutTag;
  }

  private void cleanUpDownloadedDependencies() throws IOException, URISyntaxException {
    var dir = Paths.get(getRepositoriesPath());
    if (!dir.toFile().exists()) return;
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
      for (Path path : directoryStream) {
        if (Files.isDirectory(path)) {
          deleteDirectory(path.toFile());
        }
      }
    }
  }

  public static void deleteDirectory(File directory) throws IOException {
    if (!directory.exists()) {
      return;
    }

    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          deleteDirectory(file);
        } else {
          Files.delete(file.toPath());
        }
      }
    }

    Files.delete(directory.toPath());
    log.debug("Deleted directory: {}", directory.getAbsolutePath());
  }
}
