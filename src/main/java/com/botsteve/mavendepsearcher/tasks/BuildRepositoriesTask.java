package com.botsteve.mavendepsearcher.tasks;

import static com.botsteve.mavendepsearcher.service.MavenInvokerService.getMavenInvokerResult;
import static com.botsteve.mavendepsearcher.utils.FxUtils.getErrorAlertAndCloseProgressBar;
import static com.botsteve.mavendepsearcher.utils.FxUtils.showAlert;
import static com.botsteve.mavendepsearcher.utils.FxUtils.showError;
import static com.botsteve.mavendepsearcher.utils.FxUtils.updateProgressBarAndLabel;
import static com.botsteve.mavendepsearcher.utils.JavaVersionResolver.JDKS;
import static com.botsteve.mavendepsearcher.utils.JavaVersionResolver.getJavaVersionMaven;
import static com.botsteve.mavendepsearcher.utils.JavaVersionResolver.resolveJavaPathToBeUsed;
import static com.botsteve.mavendepsearcher.utils.JavaVersionResolver.resolveJavaVersionToEnvProperty;
import static com.botsteve.mavendepsearcher.utils.Utils.concatenateRepoNames;
import static com.botsteve.mavendepsearcher.utils.Utils.getPropertyFromSetting;
import static com.botsteve.mavendepsearcher.utils.Utils.getRepositoriesPath;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.botsteve.mavendepsearcher.exception.DepViewerException;

@Data
@Slf4j
public class BuildRepositoriesTask extends Task<Map<String, String>> {

  private static final String[] GRADLE_COMMAND = {"./gradlew", "clean", "build"};
  private static final String[] GRADLE_STOP_COMMAND = {"./gradlew", "--stop"};
  private static final String MAVEN_OPTS = "-Dmaven.compiler.fork=true -DargLine=\"-Xmx2g\"";
  private static final String JAVA_HOME = "JAVA_HOME";
  public static final String RETRY_WITH_LOWER_VERSION = "Failed to build using java version {}, retry with lower version";
  public static final String ALL_BUILDS_FAILED = "All attempts to build failed.";
  private final ProgressBar progressBar;
  private final Label progressLabel;
  private String currentRepo;
  private Set<String> reposBuildSuccessfully = new HashSet<>();
  private Map<String, String> reposBuildSuccessfullyToJavaVersion = new HashMap<>();
  private Set<String> reposBuildFailed = new HashSet<>();
  private static String currentJavaVersionUsed;

  @Override
  protected void failed() {
    super.failed();
    Throwable exception = getException();
    if (exception != null) {
      log.error(exception.getMessage(), exception);
      getErrorAlertAndCloseProgressBar(String.format("Build failed for %s repository !", currentRepo),
                                       progressBar,
                                       progressLabel);
    }
  }

  @Override
  protected void succeeded() {
    showAlert("Dependencies build task finished! \n" +
              concatenateRepoNames("Repos build successfully:", reposBuildSuccessfully) + "\n\n" +
              concatenateRepoNames("Repos build failed:", reposBuildFailed));
    progressBar.setVisible(false);
    progressLabel.setVisible(false);
  }

  @Override
  protected Map<String, String> call() throws Exception {
    var repositoriesPath = getRepositoriesPath();
    if (isDownloadRepositoriesEmpty(repositoriesPath)) throw new DepViewerException("No repositories found!");
    buildProject(repositoriesPath);
    return reposBuildSuccessfullyToJavaVersion;
  }

  private boolean isDownloadRepositoriesEmpty(String repositoriesPath) {
    File repositoriesDir = new File(repositoriesPath);

    if (repositoriesDir.isDirectory()) {
      File[] repositories = repositoriesDir.listFiles(File::isDirectory);
      if (repositories == null || repositories.length == 0) {
        Platform.runLater(() -> showError("No dependencies downloaded!"));
        return true;
      }
    }
    return false;
  }

  public void buildProject(String repositoriesPath) {
    File repositoriesDir = new File(repositoriesPath);

    if (repositoriesDir.isDirectory()) {
      File[] repositories = repositoriesDir.listFiles(File::isDirectory);
      updateProgressBarAndLabel("Clean up exiting download repos!", progressBar, progressLabel);
      if (repositories != null) {
        for (File repo : repositories) {
          log.info("Building repository: {}", repo.getName());
          currentRepo = repo.getName();
          updateProgressBarAndLabel("Building repository: " + repo.getName(), progressBar, progressLabel);
          try {
            buildRepository(repo);
            reposBuildSuccessfully.add(currentRepo);
            reposBuildSuccessfullyToJavaVersion.put(currentRepo, currentJavaVersionUsed);
          } catch (Exception e) {
            reposBuildFailed.add(currentRepo);
          }
        }
      } else {
        log.error("No repositories found in the directory.");
        throw new DepViewerException("No repositories found in the directory");
      }
    } else {
      log.error("{} is not a directory.", repositoriesPath);
      throw new DepViewerException("Not a project directory: " + repositoriesPath);
    }
  }

  private void buildRepository(File repo) {
    if (new File(repo, "build.gradle").exists() || new File(repo, "build.gradle.kts").exists()) {
      tryGradleBuildWithDifferentJdks(repo);
    } else if (new File(repo, "pom.xml").exists()) {
      tryMavenBuildWithDifferentJdks(repo);
    } else {
      log.warn("No recognizable build file found in {}", repo.getName());
    }
  }

  private static void tryMavenBuildWithDifferentJdks(File repo) {
    var jdks = new ArrayList<>(JDKS);
    if (runMavenBuildWithDetectedJavaVersion(repo, jdks)) return;

    String currentJdkPath = System.getenv(JAVA_HOME);
    boolean buildSuccessful = false;

    for (String property : jdks) {
      try {
        runMavenBuild(repo, currentJdkPath);
        currentJavaVersionUsed = property;
        buildSuccessful = true;
        break;
      } catch (Exception e) {
        log.error(RETRY_WITH_LOWER_VERSION, currentJdkPath);
        currentJdkPath = getPropertyFromSetting(property);
      }
    }

    if (!buildSuccessful) {
      log.error(ALL_BUILDS_FAILED);
      throw new DepViewerException(ALL_BUILDS_FAILED);
    }
  }

  private static boolean runMavenBuildWithDetectedJavaVersion(File repo, ArrayList<String> jdks) {
    var javaVersionMaven = getJavaVersionMaven(repo);
    var resolvedjdkPath = resolveJavaPathToBeUsed(javaVersionMaven);
    try {
      runMavenBuild(repo, resolvedjdkPath);
      currentJavaVersionUsed = resolveJavaVersionToEnvProperty(javaVersionMaven);
      return true;
    } catch (Exception e1) {
      log.error("Maven build failed with resolved java version: {}", resolvedjdkPath);
      if ("1.8".equals(javaVersionMaven) || "8".equals(javaVersionMaven)) {
        jdks.remove("JAVA8_HOME");
      } else if ("11.0".equals(javaVersionMaven)) {
        jdks.remove("JAVA11_HOME");
      } else if ("17.0".equals(javaVersionMaven)) {
        jdks.remove("JAVA17_HOME");
      }
    }
    return false;
  }

  private static void tryGradleBuildWithDifferentJdks(File repo) {

    String currentJdkPath = System.getenv(JAVA_HOME);
    boolean buildSuccessful = false;

    for (String property : JDKS) {
      try {
        runGradleBuild(repo, currentJdkPath, GRADLE_STOP_COMMAND);
        runGradleBuild(repo, currentJdkPath, GRADLE_COMMAND);
        buildSuccessful = true;
        currentJavaVersionUsed = property;
        break;
      } catch (Exception e) {
        log.error(RETRY_WITH_LOWER_VERSION, currentJdkPath);
        currentJdkPath = getPropertyFromSetting(property);
      }
    }

    if (!buildSuccessful) {
      log.error(ALL_BUILDS_FAILED);
      throw new DepViewerException(ALL_BUILDS_FAILED);
    }
  }

  private static void runGradleBuild(File repo, String jdkPath, String[] command) throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(repo);
    processBuilder.redirectErrorStream(true);
    processBuilder.environment().put(JAVA_HOME, jdkPath);
    log.info("Executing gradle build command: {} with JAVA_HOME: {}", processBuilder.command(), jdkPath);

    Process process = processBuilder.start();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        log.info(line);
      }
    }

    int exitCode = process.waitFor();
    if (exitCode == 0) {
      log.debug("Build successful for {}", repo.getName());
    } else {
      throw new DepViewerException(String.format("Build failed for %s", repo.getName()));
    }
  }


  private static void runMavenBuild(File repo, String jdkPath) {
    try {
      getMavenInvokerResult(repo.getAbsolutePath(), "", "clean package", MAVEN_OPTS, jdkPath);
    } catch (Exception e) {
      log.error("Building repository failed, retry with new file permissions", e);
      changeDirectoryPermissions(repo);
      getMavenInvokerResult(repo.getAbsolutePath(), "", "package", MAVEN_OPTS, jdkPath);
    }
  }

  public static void changeDirectoryPermissions(File directory) {
    try {
      // Set read and write permissions for owner, group, and others
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      perms.add(PosixFilePermission.OWNER_EXECUTE);
      perms.add(PosixFilePermission.GROUP_READ);
      perms.add(PosixFilePermission.GROUP_WRITE);
      perms.add(PosixFilePermission.GROUP_EXECUTE);
      perms.add(PosixFilePermission.OTHERS_READ);
      perms.add(PosixFilePermission.OTHERS_WRITE);
      perms.add(PosixFilePermission.OTHERS_EXECUTE);

      // Convert permissions to file attributes
      Path path = Paths.get(directory.getAbsolutePath());
      Files.setPosixFilePermissions(path, perms);

      log.debug("Permissions changed successfully for directory: {}", directory.getAbsolutePath());

      // Recursively apply permissions to all files and directories
      if (directory.isDirectory()) {
        File[] files = directory.listFiles();
        if (files != null) {
          for (File file : files) {
            changeDirectoryPermissions(file);
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to change permissions", e);
    }
  }
}
