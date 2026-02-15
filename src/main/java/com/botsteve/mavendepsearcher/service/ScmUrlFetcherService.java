package com.botsteve.mavendepsearcher.service;

import static com.botsteve.mavendepsearcher.utils.ScmRepositories.fixNonResolvableScmRepositorise;
import static com.botsteve.mavendepsearcher.service.MavenInvokerService.getMavenInvokerResult;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.shared.invoker.MavenInvocationException;
import com.botsteve.mavendepsearcher.model.DependencyNode;
import org.xml.sax.SAXException;

public class ScmUrlFetcherService {


  private static final String CYCLONEDX_MAVEN = "org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom";
  private static final String MAVEN_OPTS = "-DincludeTestScope=true -DoutputFormat=json";

  public static void fetchScmUrls(String projectDir, Set<DependencyNode> dependencies)
      throws ParserConfigurationException, IOException, SAXException, MavenInvocationException {
    getMavenInvokerResult(projectDir, "", CYCLONEDX_MAVEN, MAVEN_OPTS, System.getenv("JAVA_HOME"));
    populateVcsUrls(dependencies, parseBomFile(projectDir + "/target/bom.json"));
  }


  private static Map<String, String> parseBomFile(String bomFilePath) throws IOException {
    Map<String, String> vcsUrlMap = new HashMap<>();
    File bomFile = new File(bomFilePath);
    
    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(bomFile);
    com.fasterxml.jackson.databind.JsonNode components = rootNode.get("components");

    if (components != null && components.isArray()) {
      for (com.fasterxml.jackson.databind.JsonNode component : components) {
        String groupId = component.path("group").asText();
        String artifactId = component.path("name").asText();
        String version = component.path("version").asText();
        String vcsUrl = "SCM URL not found";

        com.fasterxml.jackson.databind.JsonNode externalReferences = component.path("externalReferences");
        if (externalReferences.isArray()) {
          for (com.fasterxml.jackson.databind.JsonNode ref : externalReferences) {
            if ("vcs".equals(ref.path("type").asText())) {
              vcsUrl = ref.path("url").asText();
              break;
            }
          }
        }
        
        String key = groupId + ":" + artifactId + ":" + version;
        vcsUrlMap.put(key, fixNonResolvableScmRepositorise(convertSCM(vcsUrl), artifactId));
      }
    }

    return vcsUrlMap;
  }

  private static void populateVcsUrls(Set<DependencyNode> dependencies, Map<String, String> vcsUrls) {
    for (DependencyNode node : dependencies) {
      String key = node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion();
      if (vcsUrls.containsKey(key)) {
        node.setScmUrl(vcsUrls.get(key));
      }
      if (node.getChildren() != null) {
        populateVcsUrls(new HashSet<>(node.getChildren()), vcsUrls);
      }
    }
  }

  public static String convertSCM(String scmUrl) {
    String httpsUrl = scmUrl;
    if (httpsUrl.startsWith("git://")) {
      httpsUrl = scmUrl.replace("git://", "https://");
      int index = httpsUrl.indexOf(".git/");
      if (index != -1) {
        httpsUrl = httpsUrl.substring(0, index + 4);
      }
    }
    if (httpsUrl.startsWith("https://") && httpsUrl.split("\\.git").length > 1) {
      httpsUrl = httpsUrl.split("\\.git")[0];
    }
    if (scmUrl.startsWith("scm:git:")) {
      httpsUrl = scmUrl.substring("scm:git:".length());
    }
    if (httpsUrl.startsWith("git@github.com:")) {
      httpsUrl = httpsUrl.replace("git@github.com:", "https://github.com/");
    }
    return httpsUrl;
  }
}
