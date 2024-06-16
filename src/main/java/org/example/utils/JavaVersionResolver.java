package org.example.utils;

import static org.example.service.MavenInvokerService.getMavenInvokerResult;
import static org.example.utils.Utils.getPropertyFromSetting;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaVersionResolver {

  public static final List<String> JDKS = List.of("JAVA17_HOME", "JAVA11_HOME", "JAVA8_HOME");

  public static String resolveJavaPathToBeUsed(String javaVersion) {
    return switch (javaVersion) {
      case "11.0" -> getPropertyFromSetting("JAVA11_HOME");
      case "17.0" -> getPropertyFromSetting("JAVA17_HOME");
      case "1.8", "8.0" -> getPropertyFromSetting("JAVA8_HOME");
      case null, default -> System.getenv("JAVA_HOME");
    };
  }

  public static String resolveJavaVersionToEnvProperty(String javaVersion) {
    return switch (javaVersion) {
      case "11.0" -> "JAVA11_HOME";
      case "17.0" -> "JAVA17_HOME";
      case "1.8", "8.0" -> "JAVA8_HOME";
      case null, default -> "JAVA_HOME";
    };
  }


  public static String getJavaVersionMaven(File repo) {
    var outputHandler = getMavenInvokerResult(repo.getAbsolutePath(),
                                              "", "help:effective-pom",
                                              "", System.getenv("JAVA_HOME"));
    List<String> outputLines = outputHandler.getOutput();
    String response = String.join("\n", outputLines);
    return resolveJavaVersionFromEffectivePom(response);
  }

  public static String resolveJavaVersionFromEffectivePom(String effectivePom) {
    // Trim leading/trailing whitespace
    effectivePom = effectivePom.trim();
    Set<Double> javaVersionDetected = new HashSet<>();

    // Define the pattern for the Maven Enforcer Plugin enforcing Java version
    Pattern enforcerPattern =
        Pattern.compile("<requireJavaVersion>\\s*<version>\\[([\\d.]+),.*?</version>\\s*</requireJavaVersion>", Pattern.DOTALL);
    Matcher enforcerMatcher = enforcerPattern.matcher(effectivePom);

    while (enforcerMatcher.find()) {
      // Extract the numeric part of the version
      log.debug("Maven-Enforcer-Plugin Java Version detected: {}", enforcerMatcher.group(1));
      var version = enforcerMatcher.group(1).trim();
      if (isValidVersion(version)) {
        javaVersionDetected.add(Double.parseDouble(version));
      }
    }


    // Define the regular expression pattern to find the relevant properties
    Pattern mavenPropertyPattern = Pattern.compile(
        "<maven\\.compiler\\.(source|target|release|testRelease)>([\\d.]+)</maven\\.compiler\\."
        + "(source|target|release|testRelease)>");
    Matcher mavenPropertyMatcher = mavenPropertyPattern.matcher(effectivePom);

    // Search for the pattern in the output
    if (mavenPropertyMatcher.find()) {
      log.debug("Maven-Property Java Version detected: {}", mavenPropertyMatcher.group(2));
      var version = mavenPropertyMatcher.group(2).trim();
      if (isValidVersion(version)) {
        javaVersionDetected.add(Double.parseDouble(version));
      }
    }

    // Define the pattern for the maven-compiler-plugin configuration
    Pattern pluginPattern = Pattern.compile(
        "<plugin>\\s*<artifactId>maven-compiler-plugin</artifactId>.*?<configuration>\\s*"
        + "<source>([\\d.]+)</source>\\s*<target>([\\d.]+)</target>\\s*.*?</configuration>\\s*</plugin>",
        Pattern.DOTALL);
    Matcher pluginMatcher = pluginPattern.matcher(effectivePom);

    // Search for the maven-compiler-plugin configuration in the output
    if (pluginMatcher.find()) {
      log.debug("Maven-Compiler-Plugin Java Version detected: {}", pluginMatcher.group(1));
      var version = pluginMatcher.group(1).trim();
      if (isValidVersion(version)) {
        javaVersionDetected.add(Double.parseDouble(version));
      }
    }

    return javaVersionDetected.stream()
               .max(Double::compare)
               .map(String::valueOf)
               .orElse(null);
  }

  public static boolean isValidVersion(String str) {
    try {
      Double.parseDouble(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
