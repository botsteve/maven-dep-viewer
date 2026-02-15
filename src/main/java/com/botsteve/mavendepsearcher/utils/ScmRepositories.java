package com.botsteve.mavendepsearcher.utils;

import java.util.HashMap;
import java.util.Map;

public class ScmRepositories {

  public static Map<String, String> artifactUrlMap = new HashMap<>();

  static {
    artifactUrlMap.put("hibernate-validator-cdi", "http://github.com/hibernate/hibernate-validator/");
    artifactUrlMap.put("rest-assured", "http://github.com/rest-assured/rest-assured/");
    artifactUrlMap.put("microprofile-openapi-api", "https://github.com/eclipse/microprofile-open-api/");
    artifactUrlMap.put("jersey-media-json-binding", "https://github.com/eclipse-ee4j/jersey/");
    artifactUrlMap.put("microprofile-metrics-api", "https://github.com/eclipse/microprofile-metrics");
    artifactUrlMap.put("helidon", "https://github.com/helidon-io/helidon");
  }

  public static String fixNonResolvableScmRepositorise(String scmUrl, String artifactId) {
    if (artifactUrlMap.containsKey(artifactId)) {
      return artifactUrlMap.get(artifactId);
    }

    // Check if any key in artifactUrlMap starts with artifactId
    for (Map.Entry<String, String> artifactUrl : artifactUrlMap.entrySet()) {
      if (artifactId.startsWith(artifactUrl.getKey())) {
        return artifactUrl.getValue();
      }
    }

    // If no matching key is found, return scmUrl
    return scmUrl;
  }
}
