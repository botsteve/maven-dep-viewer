package com.botsteve.mavendepsearcher.service;


import static com.botsteve.mavendepsearcher.service.DependencyTreeAnalyzerService.getModules;
import static com.botsteve.mavendepsearcher.service.DependencyTreeAnalyzerService.runMavenDependencyTree;
import static com.botsteve.mavendepsearcher.utils.Utils.getProjectName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.botsteve.mavendepsearcher.model.DependencyNode;

@Slf4j
public class DependencyAnalyzerService {

  private static final Map<String, DependencyNode> moduleToDependencyNode = new HashMap<>();

  public static Set<DependencyNode> getDependencies(String projectDir) throws Exception {
    List<String> modules = getModules(projectDir);
    ObjectMapper objectMapper = new ObjectMapper();
    var rootDependencies = objectMapper.readValue(runMavenDependencyTree(projectDir, ""), DependencyNode.class);
    moduleToDependencyNode.put(getProjectName(new File(projectDir, "pom.xml")), rootDependencies);
    Set<DependencyNode> totalDependencies = new HashSet<>(new HashSet<>(rootDependencies.getChildren()));

    for (String module : modules) {
      String dependencyTreeJson = runMavenDependencyTree(projectDir, module);
      DependencyNode dependencyNode = objectMapper.readValue(dependencyTreeJson, DependencyNode.class);
      moduleToDependencyNode.put(module, dependencyNode);
      totalDependencies.addAll(new HashSet<>(dependencyNode.getChildren()));
    }
    return totalDependencies.stream()
               .filter(dependencyNode -> !modules.contains(dependencyNode.getArtifactId()))
               .collect(Collectors.toSet());
  }
}
