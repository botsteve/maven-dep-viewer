package com.botsteve.mavendepsearcher.service;

import static com.botsteve.mavendepsearcher.utils.JavaVersionResolver.JDKS;
import static com.botsteve.mavendepsearcher.utils.JavaVersionResolver.resolveJavaPathToBeUsed;
import static com.botsteve.mavendepsearcher.utils.Utils.getPropertyFromSetting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import com.botsteve.mavendepsearcher.exception.DepViewerException;
import com.botsteve.mavendepsearcher.model.DependencyNode;

/**
 * Analyzes dependencies for Gradle projects by running `gradle dependencies`
 * and parsing the text-based dependency tree output.
 */
@Slf4j
public class GradleDependencyAnalyzerService {

  private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

  // Matches lines like: +--- group:artifact:version
  // Also handles: +--- group:artifact:version -> resolvedVersion
  // And: +--- group:artifact:{strictly version}
  // And: \--- group:artifact:version (*)
  private static final Pattern DEPENDENCY_PATTERN =
      Pattern.compile("^([| ]*)[+\\\\]---\\s+(\\S+?):(\\S+?):(\\S+?)(?:\\s+->\\s+(\\S+))?(?:\\s+\\(\\*\\))?(?:\\s+\\(c\\))?\\s*$");

  // Matches configuration headers like: runtimeClasspath - Runtime classpath of source set 'main'.
  private static final Pattern CONFIGURATION_HEADER_PATTERN =
      Pattern.compile("^(\\w+)(?:\\s+-\\s+.*)?$");

  // Configurations we care about for scope resolution
  private static final Set<String> RELEVANT_CONFIGURATIONS = Set.of(
      "implementation", "api", "compileOnly", "compileOnlyApi",
      "runtimeOnly", "runtimeClasspath", "compileClasspath",
      "testImplementation", "testCompileOnly", "testRuntimeOnly",
      "testRuntimeClasspath", "testCompileClasspath",
      "annotationProcessor", "testAnnotationProcessor"
  );

  /**
   * Maps Gradle configuration names to simplified scope labels.
   */
  private static String mapConfigurationToScope(String configuration) {
    if (configuration == null) return "";
    return switch (configuration) {
      case "implementation", "compileClasspath" -> "implementation";
      case "api" -> "api";
      case "compileOnly" -> "compileOnly";
      case "compileOnlyApi" -> "compileOnlyApi";
      case "runtimeOnly" -> "runtimeOnly";
      case "runtimeClasspath" -> "runtime";
      case "testImplementation", "testCompileClasspath" -> "testImplementation";
      case "testCompileOnly" -> "testCompileOnly";
      case "testRuntimeOnly" -> "testRuntimeOnly";
      case "testRuntimeClasspath" -> "testRuntime";
      case "annotationProcessor" -> "annotationProcessor";
      case "testAnnotationProcessor" -> "testAnnotationProcessor";
      default -> configuration;
    };
  }

  /**
   * Extracts all dependencies from a Gradle project directory.
   * Runs `gradle dependencies` once and parses all configuration sections.
   */
  public static Set<DependencyNode> getDependencies(String projectDir) throws Exception {
    String output = runGradleDependencies(projectDir);
    Map<String, Set<DependencyNode>> configToDeps = parseAllConfigurations(output);

    if (configToDeps.isEmpty()) {
      throw new DepViewerException("No dependencies found in Gradle project. Make sure the project has dependencies declared.");
    }

    // Merge dependencies from all configurations, keeping the most specific scope
    // Priority: implementation > api > compileOnly > runtimeOnly > test*
    Map<String, DependencyNode> merged = new LinkedHashMap<>();
    for (var entry : configToDeps.entrySet()) {
      for (DependencyNode node : entry.getValue()) {
        String key = node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion();
        if (!merged.containsKey(key)) {
          merged.put(key, node);
        }
        // If already present, keep the first one (which is from a more specific config)
      }
    }

    Set<DependencyNode> result = new HashSet<>(merged.values());
    log.info("Found {} unique dependencies across all Gradle configurations", result.size());
    return result;
  }

  /**
   * Runs `gradle dependencies` and captures the output (all configurations).
   * Detects the compatible Java version from the Gradle wrapper version.
   * Falls back to trying different JDK versions if detection fails.
   */
  private static String runGradleDependencies(String projectDir) throws Exception {
    File dir = new File(projectDir);
    String gradleCmd = findGradleExecutable(dir);

    // Build ordered list of JDKs to try, with the detected best JDK first
    List<String> jdkPaths = buildJdkCandidateList(dir);

    Exception lastException = null;

    for (String javaHome : jdkPaths) {
      try {
        String result = executeGradleDependencies(gradleCmd, dir, javaHome);
        return result;
      } catch (Exception e) {
        lastException = e;
        log.warn("Gradle dependencies failed with JAVA_HOME={}: {}. Trying next JDK...", javaHome, e.getMessage());
      }
    }

    throw new DepViewerException("Gradle dependencies command failed with all available JDKs. Last error: "
        + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
  }

  /**
   * Builds an ordered list of JAVA_HOME paths to try.
   * Puts the detected compatible JDK first, then falls back to all configured JDKs.
   */
  private static List<String> buildJdkCandidateList(File projectDir) {
    // Use LinkedHashSet to avoid duplicates while preserving order
    Set<String> candidates = new LinkedHashSet<>();

    // 1. Try to detect the best JDK from the Gradle wrapper version
    String detectedJavaVersion = detectJavaVersionFromGradleWrapper(projectDir);
    if (detectedJavaVersion != null) {
      String detectedJdkPath = resolveJavaPathToBeUsed(detectedJavaVersion);
      if (detectedJdkPath != null && !detectedJdkPath.isEmpty()) {
        log.info("Detected Gradle-compatible Java version: {} -> JAVA_HOME={}", detectedJavaVersion, detectedJdkPath);
        candidates.add(detectedJdkPath);
      }
    }

    // 2. Add the current system JAVA_HOME
    String currentJavaHome = System.getenv("JAVA_HOME");
    if (currentJavaHome != null && !currentJavaHome.isEmpty()) {
      candidates.add(currentJavaHome);
    }

    // 3. Add all configured JDKs as fallbacks
    for (String jdkProperty : JDKS) {
      String jdkPath = getPropertyFromSetting(jdkProperty);
      if (jdkPath != null && !jdkPath.isEmpty()) {
        candidates.add(jdkPath);
      }
    }

    log.info("JDK candidate list (in order): {}", candidates);
    return new ArrayList<>(candidates);
  }

  /**
   * Detects the compatible Java version by reading the Gradle wrapper version
   * from gradle/wrapper/gradle-wrapper.properties.
   *
   * Gradle version to max Java version mapping:
   *   Gradle 4.x        -> Java 8
   *   Gradle 5.0-5.4     -> Java 11
   *   Gradle 5.5-5.6     -> Java 11
   *   Gradle 6.0-6.6     -> Java 11 (safest; 6.7+ has partial Java 15 support)
   *   Gradle 6.7-6.9     -> Java 15 (but safer with 11)
   *   Gradle 7.0-7.2     -> Java 17
   *   Gradle 7.3-7.5     -> Java 17
   *   Gradle 7.6         -> Java 17
   *   Gradle 8.0-8.4     -> Java 17 (safe) / 21 (partial)
   *   Gradle 8.5+        -> Java 21
   */
  public static String detectJavaVersionFromGradleWrapper(File projectDir) {
    File wrapperProps = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
    if (!wrapperProps.exists()) {
      log.info("No gradle-wrapper.properties found, cannot detect Gradle version");
      return null;
    }

    try {
      Properties props = new Properties();
      try (FileInputStream fis = new FileInputStream(wrapperProps)) {
        props.load(fis);
      }

      String distributionUrl = props.getProperty("distributionUrl");
      if (distributionUrl == null) {
        log.info("No distributionUrl found in gradle-wrapper.properties");
        return null;
      }

      // Extract version from URL like: https://services.gradle.org/distributions/gradle-6.7.1-all.zip
      Matcher matcher = Pattern.compile("gradle-(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
          .matcher(distributionUrl);
      if (!matcher.find()) {
        log.info("Could not parse Gradle version from distributionUrl: {}", distributionUrl);
        return null;
      }

      int major = Integer.parseInt(matcher.group(1));
      int minor = Integer.parseInt(matcher.group(2));
      log.info("Detected Gradle wrapper version: {}.{}", major, minor);

      return mapGradleVersionToJavaVersion(major, minor);

    } catch (Exception e) {
      log.warn("Failed to read gradle-wrapper.properties: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Maps a Gradle major.minor version to the maximum Java version it supports.
   * Returns a version string compatible with JavaVersionResolver.resolveJavaPathToBeUsed().
   */
  private static String mapGradleVersionToJavaVersion(int major, int minor) {
    if (major >= 8 && minor >= 5) {
      return "21.0";
    } else if (major >= 8) {
      // Gradle 8.0-8.4: officially supports Java 20, but 17 is safest
      return "17.0";
    } else if (major == 7 && minor >= 3) {
      return "17.0";
    } else if (major == 7) {
      // Gradle 7.0-7.2: supports Java 16, use 17 as closest available
      return "17.0";
    } else if (major == 6) {
      // Gradle 6.x: max Java 15, but 11 is safest and most common
      return "11.0";
    } else if (major == 5) {
      return "11.0";
    } else {
      // Gradle 4.x and older
      return "1.8";
    }
  }

  /**
   * Executes the `gradle dependencies` command with the specified JAVA_HOME.
   */
  private static String executeGradleDependencies(String gradleCmd, File projectDir, String javaHome) throws Exception {
    var command = new ArrayList<String>();
    command.add(gradleCmd);
    command.add("dependencies");
    command.add("--console=plain");
    command.add("--info"); // Verbose output

    log.info("Executing Gradle command: {} in {} with JAVA_HOME={}", String.join(" ", command), projectDir, javaHome);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(projectDir);
    pb.redirectErrorStream(true);

    if (javaHome != null && !javaHome.isEmpty()) {
      pb.environment().put("JAVA_HOME", javaHome);
    }

    Process process;
    try {
      process = pb.start();
    } catch (Exception e) {
      throw new DepViewerException("Failed to start Gradle. Ensure Gradle or Gradle Wrapper is available in the project.", e);
    }

    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        log.info("[gradle] {}", line);
        output.append(line).append(System.lineSeparator());
      }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      log.warn("Gradle dependencies command exited with code {} using JAVA_HOME={}", exitCode, javaHome);
      throw new DepViewerException("Gradle dependencies command failed (exit code " + exitCode + ")");
    }

    return output.toString();
  }

  /**
   * Finds the best Gradle executable: prefers wrapper over system Gradle.
   */
  private static String findGradleExecutable(File projectDir) {
    String wrapperName = IS_WINDOWS ? "gradlew.bat" : "./gradlew";
    File wrapper = new File(projectDir, IS_WINDOWS ? "gradlew.bat" : "gradlew");
    if (wrapper.exists() && wrapper.canExecute()) {
      return wrapperName;
    }
    // Fall back to system gradle
    return IS_WINDOWS ? "gradle.bat" : "gradle";
  }

  /**
   * Parses the full `gradle dependencies` output, splitting it into configuration sections
   * and parsing each section's dependency tree. Sets the scope on each node.
   *
   * Example output format:
   * <pre>
   * implementation - Implementation dependencies for the 'main' feature.
   * +--- org.springframework.boot:spring-boot-starter-web:3.1.0
   * |    +--- org.springframework.boot:spring-boot-starter:3.1.0
   *
   * runtimeClasspath - Runtime classpath of source set 'main'.
   * +--- org.springframework.boot:spring-boot-starter-web:3.1.0
   * </pre>
   */
  static Map<String, Set<DependencyNode>> parseAllConfigurations(String output) {
    Map<String, Set<DependencyNode>> result = new LinkedHashMap<>();
    String[] lines = output.split("\\r?\\n");
    String currentConfig = null;
    var currentLines = new ArrayList<String>();

    for (String line : lines) {
      // Check if this is a configuration header
      if (!line.startsWith(" ") && !line.startsWith("|") && !line.startsWith("+") && !line.startsWith("\\")) {
        // Flush the previous section
        if (currentConfig != null && !currentLines.isEmpty()) {
          Set<DependencyNode> deps = parseDependencyTree(currentLines, currentConfig);
          if (!deps.isEmpty()) {
            result.put(currentConfig, deps);
          }
        }

        // Check if this is a relevant config header
        Matcher headerMatcher = CONFIGURATION_HEADER_PATTERN.matcher(line.trim());
        if (headerMatcher.matches()) {
          String configName = headerMatcher.group(1);
          if (RELEVANT_CONFIGURATIONS.contains(configName)) {
            log.info("Parsing Gradle configuration section: {}", configName);
            currentConfig = configName;
            currentLines = new ArrayList<>();
            continue;
          }
        }
        currentConfig = null;
        currentLines = new ArrayList<>();
      } else if (currentConfig != null) {
        currentLines.add(line);
      }
    }

    // Flush the last section
    if (currentConfig != null && !currentLines.isEmpty()) {
      Set<DependencyNode> deps = parseDependencyTree(currentLines, currentConfig);
      if (!deps.isEmpty()) {
        result.put(currentConfig, deps);
      }
    }

    return result;
  }

  /**
   * Parses a list of dependency tree lines into DependencyNode objects,
   * assigning the given configuration as the scope.
   */
  static Set<DependencyNode> parseDependencyTree(java.util.List<String> lines, String configuration) {
    Set<DependencyNode> topLevelDependencies = new HashSet<>();
    Deque<NodeAtDepth> stack = new ArrayDeque<>();
    String scope = mapConfigurationToScope(configuration);

    for (String line : lines) {
      Matcher matcher = DEPENDENCY_PATTERN.matcher(line);
      if (!matcher.matches()) {
        continue;
      }

      String indent = matcher.group(1);
      String groupId = matcher.group(2);
      String artifactId = matcher.group(3);
      String version = matcher.group(4);
      String resolvedVersion = matcher.group(5); // may be null

      // Use resolved version if present (e.g., "1.0 -> 1.1")
      if (resolvedVersion != null && !resolvedVersion.isEmpty()) {
        version = resolvedVersion;
      }

      // Clean version string of constraint markers
      version = cleanVersion(version);

      int depth = calculateDepth(indent);

      DependencyNode node = new DependencyNode(groupId, artifactId, version, scope);

      // Pop stack until we find the parent at depth - 1
      while (!stack.isEmpty() && stack.peek().depth >= depth) {
        stack.pop();
      }

      if (stack.isEmpty()) {
        // Top-level dependency
        topLevelDependencies.add(node);
      } else {
        // Child of the node at the top of the stack
        NodeAtDepth parent = stack.peek();
        if (parent.node.getChildren() == null) {
          parent.node.setChildren(new ArrayList<>());
        }
        parent.node.getChildren().add(node);
      }

      stack.push(new NodeAtDepth(node, depth));
    }

    return topLevelDependencies;
  }

  /**
   * Calculates the depth of a dependency line based on its indentation.
   * Each level of nesting adds 5 characters of indentation ("|    " or "     ").
   */
  private static int calculateDepth(String indent) {
    if (indent == null || indent.isEmpty()) {
      return 0;
    }
    // Each depth level = 5 characters ("|    " or "     ")
    return indent.length() / 5;
  }

  /**
   * Cleans version strings by removing Gradle constraint markers.
   */
  private static String cleanVersion(String version) {
    if (version == null) return "";
    // Remove {strictly X} markers
    version = version.replaceAll("\\{strictly\\s+", "").replace("}", "");
    // Remove SNAPSHOT qualifiers markers but keep version
    version = version.trim();
    return version;
  }

  /**
   * Helper class to track a node and its depth in the tree.
   */
  private static class NodeAtDepth {
    final DependencyNode node;
    final int depth;

    NodeAtDepth(DependencyNode node, int depth) {
      this.node = node;
      this.depth = depth;
    }
  }
}
