package com.botsteve.mavendepsearcher.service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Enriches dependency nodes with SCM (VCS) URLs by querying local caches
 * and remote repositories (Maven Central). Works for both Maven and Gradle projects.
 */
@Slf4j
public class ScmEnrichmentService {

  private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";

  /**
   * Fetches SCM URLs for all dependencies that don't already have one.
   */
  public static void fetchScmUrls(Set<DependencyNode> dependencies) {
    for (DependencyNode node : dependencies) {
      if (shouldFetch(node)) {
        try {
          String scmUrl = resolveScmUrl(node.getGroupId(), node.getArtifactId(), node.getVersion(), 0);
          if (scmUrl != null && !scmUrl.isEmpty()) {
            node.setScmUrl(convertSCM(fixNonResolvableScmRepositorise(scmUrl, node.getArtifactId())
            ));
          } else {
            node.setScmUrl("SCM URL not found");
          }
        } catch (Exception e) {
          log.info("Failed to resolve SCM URL for {}:{}:{}: {}",
                    node.getGroupId(), node.getArtifactId(), node.getVersion(), e.getMessage());
          node.setScmUrl("SCM URL not found");
        }
      }

      // Always process children to ensure deep enrichment
      if (node.getChildren() != null && !node.getChildren().isEmpty()) {
        fetchScmUrls(new HashSet<>(node.getChildren()));
      }
    }
  }

  private static boolean shouldFetch(DependencyNode node) {
    String current = node.getScmUrl();
    return current == null || current.isEmpty() || "SCM URL not found".equals(current);
  }

  /**
   * Resolves SCM URL by checking the local .m2 cache or Maven Central, 
   * following parents recursively if needed.
   */
  private static String resolveScmUrl(String groupId, String artifactId, String version, int depth) throws Exception {
    if (depth > 3 || groupId == null || artifactId == null || version == null) return null;

    Document doc = fetchPom(groupId, artifactId, version);
    if (doc == null) return null;

    // 1. Try <scm> tags in current POM
    String scmInfo = extractScmFromDoc(doc);
    if (scmInfo != null) return scmInfo;

    // 2. Try top-level <url> (Homepage) as fallback
    NodeList urlNodes = doc.getElementsByTagName("url");
    if (urlNodes.getLength() > 0) {
      String homepage = urlNodes.item(0).getTextContent().trim();
      if (!homepage.isEmpty() && !homepage.contains("${")) {
        // Many homepages are just GitHub repos
        if (homepage.contains("github.com") || homepage.contains("gitlab.com")) {
            return homepage;
        }
      }
    }

    // 3. Follow Parent POM
    NodeList parentNodes = doc.getElementsByTagName("parent");
    if (parentNodes.getLength() > 0) {
      org.w3c.dom.Element parent = (org.w3c.dom.Element) parentNodes.item(0);
      String pGroup = getTagValue(parent, "groupId");
      String pArtifact = getTagValue(parent, "artifactId");
      String pVersion = getTagValue(parent, "version");
      
      if (pGroup != null && pArtifact != null && pVersion != null) {
        log.info("Following parent POM for enrichment: {}:{}:{} (level {})", pGroup, pArtifact, pVersion, depth + 1);
        return resolveScmUrl(pGroup, pArtifact, pVersion, depth + 1);
      }
    }

    return null;
  }

  private static String extractScmFromDoc(Document doc) {
    NodeList scmNodes = doc.getElementsByTagName("scm");
    if (scmNodes.getLength() > 0) {
      org.w3c.dom.Element scmElement = (org.w3c.dom.Element) scmNodes.item(0);
      
      // Order of preference: <url> -> <connection> -> <developerConnection>
      String[] tags = {"url", "connection", "developerConnection"};
      for (String tag : tags) {
        String val = getTagValue(scmElement, tag);
        if (val != null && !val.isEmpty() && !val.contains("${")) {
          return val;
        }
      }
    }
    return null;
  }

  private static String getTagValue(org.w3c.dom.Element element, String tagName) {
    NodeList list = element.getElementsByTagName(tagName);
    if (list != null && list.getLength() > 0) {
        // Ensure we take the direct child tag value, not an inherited one deep inside
        return list.item(0).getTextContent().trim();
    }
    return null;
  }

  /**
   * Attempts to load POM from local .m2 repo or Maven Central.
   */
  private static Document fetchPom(String groupId, String artifactId, String version) {
    try {
      // Try local .m2 repo first to save bandwidth/time
      String m2Repo = System.getProperty("user.home") + "/.m2/repository";
      Path localPath = Paths.get(m2Repo, groupId.replace('.', '/'), artifactId, version, 
                                 String.format("%s-%s.pom", artifactId, version));
      
      if (Files.exists(localPath)) {
        try (InputStream is = Files.newInputStream(localPath)) {
          return parseXml(is);
        }
      }

      // Fallback to Maven Central
      String groupPath = groupId.replace('.', '/');
      String pomUrl = String.format("%s/%s/%s/%s/%s-%s.pom",
                                    MAVEN_CENTRAL_BASE, groupPath, artifactId, version, artifactId, version);

      HttpURLConnection connection = (HttpURLConnection) java.net.URI.create(pomUrl).toURL().openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(3000);
      
      try {
        if (connection.getResponseCode() == 200) {
          try (InputStream is = connection.getInputStream()) {
            return parseXml(is);
          }
        }
      } finally {
        connection.disconnect();
      }
    } catch (Exception e) {
      log.debug("Failed to fetch POM for {}:{}:{}: {}", groupId, artifactId, version, e.getMessage());
    }
    return null;
  }

  private static Document parseXml(InputStream is) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(is);
    doc.getDocumentElement().normalize();
    return doc;
  }

  public static String convertSCM(String scmUrl) {
    if (scmUrl == null) return "";
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
