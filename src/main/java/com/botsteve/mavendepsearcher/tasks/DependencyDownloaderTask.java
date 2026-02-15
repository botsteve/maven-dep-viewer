package com.botsteve.mavendepsearcher.tasks;

import static com.botsteve.mavendepsearcher.tasks.CheckoutTagsTask.checkoutTag;
import static com.botsteve.mavendepsearcher.utils.FxUtils.getErrorAlertAndCloseProgressBar;
import static com.botsteve.mavendepsearcher.utils.FxUtils.showAlert;
import static com.botsteve.mavendepsearcher.utils.FxUtils.updateProgressBarAndLabel;
import static com.botsteve.mavendepsearcher.utils.ProxyUtil.configureProxyIfEnvAvailable;
import static com.botsteve.mavendepsearcher.utils.ProxyUtil.getProxyExceptionMessage;
import static com.botsteve.mavendepsearcher.utils.ProxyUtil.getRepoNameFromUrl;
import static com.botsteve.mavendepsearcher.utils.Utils.getRepositoriesPath;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import com.botsteve.mavendepsearcher.utils.ForceDeleteUtil;

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
      var errorMessage = getProxyExceptionMessage(currentRepo, exception);
      if (exception instanceof AccessDeniedException e) {
        errorMessage = e.getMessage();
      }
      getErrorAlertAndCloseProgressBar(errorMessage, progressBar, progressLabel);
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
      try (
          Git git = Git.cloneRepository()
                        .setURI(versionScm.getKey())
                        .setDirectory(localRepoDir)
                        .setCloneAllBranches(true)
                        .setDepth(1)  // Limit to the latest commit
                        .call()
      ) {
        // The repository has been cloned successfully
        log.info("Repository cloned successfully: {}", versionScm.getKey());
      } catch (GitAPIException e) {
        log.error("Failed to clone repository: {}", versionScm.getKey(), e);
        throw e;
      }

      var checkoutTag = checkoutTag(localRepoDir, versionScm.getValue());
      repoToCheckoutTag.put(getRepoNameFromUrl(versionScm.getKey()), checkoutTag.replace("refs/tags/", ""));
    }
    return repoToCheckoutTag;
  }

  private void cleanUpDownloadedDependencies() throws IOException, URISyntaxException {
    Path dir = Paths.get(getRepositoriesPath());
    if (!Files.exists(dir)) return;

    try (Stream<Path> paths = Files.list(dir)) {
      paths.filter(Files::isDirectory)
          .forEach(path -> {
            try {
              ForceDeleteUtil.forceDeleteDirectory(path);
              log.debug("Deleted directory: {}", path);
            } catch (IOException e) {
              log.error("Failed to delete directory: {}", path, e);
              throw new RuntimeException(
                  "Cleanup process stopped due to error. If this persists try to delete the downloaded_repos directory manually",
                  e);
            }
          });
    }
  }
}
