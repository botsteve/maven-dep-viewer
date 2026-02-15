package com.botsteve.mavendepsearcher.service;

import static com.botsteve.mavendepsearcher.service.MavenInvokerService.getMavenInvokerResult;

import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.botsteve.mavendepsearcher.utils.Utils;

@Slf4j
public class DependencyTreeAnalyzerService {

  public static List<String> getModules(String projectDir) throws Exception {
    File parentPomFile = new File(projectDir, "pom.xml");
    return Utils.parseModulesFromPom(parentPomFile);
  }

  public static String runMavenDependencyTree(String projectDir, String moduleDir) {
    var outputHandler = getMavenInvokerResult(projectDir, moduleDir,
                                              "org.apache.maven.plugins:maven-dependency-plugin:3.7.0:tree",
                                              "-DoutputType=json", System.getenv("JAVA_HOME"));
    List<String> outputLines = outputHandler.getOutput();
    return extractJsonFromMavenOutput(outputLines);
  }

  private static String extractJsonFromMavenOutput(List<String> outputLines) {
    StringBuilder jsonBuilder = new StringBuilder();
    boolean inJson = false;

    for (String line : outputLines) {
      // Remove the "[INFO]" prefix
      String strippedLine = line.replaceFirst("^\\[INFO] ?", "");
      if (strippedLine.trim().startsWith("{")) {
        inJson = true;
      }
      if (inJson) {
        jsonBuilder.append(strippedLine).append(System.lineSeparator());
      }
    }

    return jsonBuilder.toString().trim();
  }
}

