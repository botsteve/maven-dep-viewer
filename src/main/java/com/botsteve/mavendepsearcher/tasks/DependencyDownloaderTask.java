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
import org.eclipse.jgit.transport.TagOpt;
import com.botsteve.mavendepsearcher.utils.ForceDeleteUtil;

@Slf4j
@RequiredArgsConstructor
public class DependencyDownloaderTask extends Task<Map<String, String>> {

  private final Map<String, String> scmToVersionRepos;
  private final ProgressBar progressBar;
  private final Label progressLabel;
  private final boolean cleanUp;
  private Map.Entry<String, String> currentRepo;
  private final java.util.concurrent.ConcurrentHashMap<String, String> repoToCheckoutTag = new java.util.concurrent.ConcurrentHashMap<>();


  @Override
  protected void failed() {
    super.failed();
    Throwable exception = getException();
    if (exception != null) {
      log.error(exception.getMessage(), exception);
      getErrorAlertAndCloseProgressBar("Failed to download dependencies. Check logs for details: " + exception.getMessage(), progressBar, progressLabel);
    }
  }

  @Override
  protected void succeeded() {
    showAlert("Dependencies downloaded task finished!");
    progressBar.setVisible(false);
    progressLabel.setVisible(false);
  }

  @Override
  protected Map<String, String> call() {
    updateProgressBarAndLabel("Cleaning up previous downloaded repositories", progressBar, progressLabel);
    if (cleanUp) {
        try {
            cleanUpDownloadedDependencies();
        } catch (Exception e) {
            log.error("Cleanup failed", e);
            throw new RuntimeException("Failed to clean up repositories", e);
        }
    }
    
    scmToVersionRepos.entrySet().parallelStream().forEach(versionScm -> {
      try {
          Platform.runLater(() -> progressLabel.setText("Downloading: " + getRepoNameFromUrl(versionScm.getKey())));
          
          File localRepoDir = new File(getRepositoriesPath(), getRepoNameFromUrl(versionScm.getKey()));
          configureProxyIfEnvAvailable();
          
          boolean repoReady = false;
          if (!cleanUp && localRepoDir.exists() && new File(localRepoDir, ".git").exists()) {
               try (Git git = Git.open(localRepoDir)) {
                    log.info("Updating existing repository: {}", versionScm.getKey());
                    git.fetch().setTagOpt(TagOpt.FETCH_TAGS).call();
                    repoReady = true;
               } catch (Exception e) {
                    log.warn("Failed to reuse repository {}: {}", localRepoDir, e.getMessage());
                    try {
                        ForceDeleteUtil.forceDeleteDirectory(localRepoDir.toPath());
                    } catch (IOException ex) {
                        throw new RuntimeException("Failed to clear directory for clone", ex);
                    }
               }
          } else if (localRepoDir.exists()) {
               try {
                   ForceDeleteUtil.forceDeleteDirectory(localRepoDir.toPath());
               } catch (IOException e) {
                   throw new RuntimeException("Failed to clear directory for clone", e);
               }
          }

          if (!repoReady) {
               try (Git git = Git.cloneRepository()
                         .setURI(versionScm.getKey())
                         .setDirectory(localRepoDir)
                         .setCloneAllBranches(true)
                         .setDepth(1)
                         .call()) {
                   log.info("Repository cloned successfully: {}", versionScm.getKey());
               }
          }
          
          var checkoutTagStr = checkoutTag(localRepoDir, versionScm.getValue());
          repoToCheckoutTag.put(getRepoNameFromUrl(versionScm.getKey()), checkoutTagStr.replace("refs/tags/", ""));

      } catch (Exception e) {
        log.error("Failed to clone/checkout repository: {}", versionScm.getKey(), e);
        throw new RuntimeException("Failed to clone " + versionScm.getKey(), e);
      }
    });

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
