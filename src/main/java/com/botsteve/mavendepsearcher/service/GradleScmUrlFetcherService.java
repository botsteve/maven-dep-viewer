package com.botsteve.mavendepsearcher.service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import com.botsteve.mavendepsearcher.model.DependencyNode;

import static com.botsteve.mavendepsearcher.utils.ScmRepositories.fixNonResolvableScmRepositorise;

/**
 * Fetches SCM URLs for Gradle project dependencies by querying Maven Central
 * for POM files and extracting SCM information.
 */
@Slf4j
public class GradleScmUrlFetcherService {

  private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";

  /**
   * Fetches SCM URLs for all dependencies by looking up their POMs on Maven Central.
   */
  public static void fetchScmUrls(Set<DependencyNode> dependencies) {
    for (DependencyNode node : dependencies) {
      try {
        String scmUrl = fetchScmUrlFromMavenCentral(node.getGroupId(), node.getArtifactId(), node.getVersion());
        if (scmUrl != null && !scmUrl.isEmpty()) {
          node.setScmUrl(ScmUrlFetcherService.convertSCM(
              fixNonResolvableScmRepositorise(scmUrl, node.getArtifactId())
          ));
        } else {
          node.setScmUrl("SCM URL not found");
        }
      } catch (Exception e) {
        log.info("Failed to fetch SCM URL for {}:{}:{}: {}",
                  node.getGroupId(), node.getArtifactId(), node.getVersion(), e.getMessage());
        node.setScmUrl("SCM URL not found");
      }

      // Also fetch for children recursively
      if (node.getChildren() != null) {
        fetchScmUrls(new HashSet<>(node.getChildren()));
      }
    }
  }

  /**
   * Fetches SCM URL from the POM file on Maven Central.
   */
  private static String fetchScmUrlFromMavenCentral(String groupId, String artifactId, String version) throws Exception {
    if (groupId == null || artifactId == null || version == null) return null;

    String groupPath = groupId.replace('.', '/');
    String pomUrl = String.format("%s/%s/%s/%s/%s-%s.pom",
                                  MAVEN_CENTRAL_BASE, groupPath, artifactId, version, artifactId, version);

    log.info("Fetching POM from: {}", pomUrl);

    HttpURLConnection connection = (HttpURLConnection) new URL(pomUrl).openConnection();
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(5000);

    try {
      int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
        log.info("POM not found at {} (HTTP {})", pomUrl, responseCode);
        return null;
      }

      try (InputStream is = connection.getInputStream()) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entities for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);
        doc.getDocumentElement().normalize();

        // Try <scm><url> first
        NodeList scmNodes = doc.getElementsByTagName("scm");
        if (scmNodes.getLength() > 0) {
          org.w3c.dom.Element scmElement = (org.w3c.dom.Element) scmNodes.item(0);

          // Try <url> within <scm>
          NodeList urlNodes = scmElement.getElementsByTagName("url");
          if (urlNodes.getLength() > 0) {
            String url = urlNodes.item(0).getTextContent().trim();
            if (!url.isEmpty() && !url.contains("${")) {
              return url;
            }
          }

          // Try <connection> within <scm>
          NodeList connNodes = scmElement.getElementsByTagName("connection");
          if (connNodes.getLength() > 0) {
            String conn = connNodes.item(0).getTextContent().trim();
            if (!conn.isEmpty() && !conn.contains("${")) {
              return conn;
            }
          }

          // Try <developerConnection> within <scm>
          NodeList devConnNodes = scmElement.getElementsByTagName("developerConnection");
          if (devConnNodes.getLength() > 0) {
            String devConn = devConnNodes.item(0).getTextContent().trim();
            if (!devConn.isEmpty() && !devConn.contains("${")) {
              return devConn;
            }
          }
        }
      }
    } finally {
      connection.disconnect();
    }

    return null;
  }
}
