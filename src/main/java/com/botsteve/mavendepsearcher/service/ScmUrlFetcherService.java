package com.botsteve.mavendepsearcher.service;

import static com.botsteve.mavendepsearcher.utils.ScmRepositories.fixNonResolvableScmRepositorise;
import static com.botsteve.mavendepsearcher.service.MavenInvokerService.getMavenInvokerResult;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.shared.invoker.MavenInvocationException;
import com.botsteve.mavendepsearcher.model.DependencyNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ScmUrlFetcherService {


  private static final String CYCLONEDX_MAVEN = "org.cyclonedx:cyclonedx-maven-plugin:2.8.0:makeAggregateBom";
  private static final String MAVEN_OPTS = "-DincludeTestScope=true -DoutputFormat=xml";

  public static void fetchScmUrls(String projectDir, Set<DependencyNode> dependencies)
      throws ParserConfigurationException, IOException, SAXException, MavenInvocationException {
    getMavenInvokerResult(projectDir, "", CYCLONEDX_MAVEN, MAVEN_OPTS, System.getenv("JAVA_HOME"));
    populateVcsUrls(dependencies, parseBomFile(projectDir + "/target/bom.xml"));
  }


  private static Map<String, String> parseBomFile(String bomFilePath)
      throws ParserConfigurationException, IOException, SAXException {
    Map<String, String> vcsUrlMap = new HashMap<>();
    File bomFile = new File(bomFilePath);
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(bomFile);
    doc.getDocumentElement().normalize();

    NodeList componentList = doc.getElementsByTagName("component");
    for (int i = 0; i < componentList.getLength(); i++) {
      Element component = (Element) componentList.item(i);
      String groupId = component.getElementsByTagName("group").item(0).getTextContent();
      String artifactId = component.getElementsByTagName("name").item(0).getTextContent();
      String version = component.getElementsByTagName("version").item(0).getTextContent();
      NodeList referenceList = component.getElementsByTagName("reference");
      String vcsUrl = "SCM URL not found";

      for (int j = 0; j < referenceList.getLength(); j++) {
        Element reference = (Element) referenceList.item(j);
        if ("vcs".equals(reference.getAttribute("type"))) {
          vcsUrl = reference.getElementsByTagName("url").item(0).getTextContent();
          break;
        }
      }
      String key = groupId + ":" + artifactId + ":" + version;
      vcsUrlMap.put(key, fixNonResolvableScmRepositorise(convertSCM(vcsUrl), artifactId));
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
