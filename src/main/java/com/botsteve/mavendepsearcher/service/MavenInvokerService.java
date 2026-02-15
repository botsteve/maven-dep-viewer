package com.botsteve.mavendepsearcher.service;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import com.botsteve.mavendepsearcher.exception.DepViewerException;
import com.botsteve.mavendepsearcher.model.CollectingOutputHandler;

@Slf4j
public class MavenInvokerService {


  public static CollectingOutputHandler getMavenInvokerResult(String projectDir,
                                                              String moduleDir,
                                                              String goals,
                                                              String mavenOpts,
                                                              String jdkPath) {
    File toolchainsFile = null;
    toolchainsFile = new File(com.botsteve.mavendepsearcher.utils.Utils.getRepositoriesPath(), "toolchains.xml");
    if (toolchainsFile.exists()) {
        goals += " --toolchains " + toolchainsFile.getAbsolutePath();
    }

    // Set Maven executable and home
    String mavenHome = System.getenv("MAVEN_HOME");
    if (mavenHome == null || mavenHome.isBlank()) {
        throw new DepViewerException("MAVEN_HOME environment variable is not set. Please configure it to point to your Maven installation.");
    }

    File mavenHomeFile = new File(mavenHome);
    if (!mavenHomeFile.exists() || !mavenHomeFile.isDirectory()) {
         throw new DepViewerException("MAVEN_HOME path is invalid: " + mavenHome);
    }

    String os = System.getProperty("os.name").toLowerCase();
    String mvnExecutableName = os.contains("win") ? "bin/mvn.cmd" : "bin/mvn";
    File mavenExecutable = new File(mavenHomeFile, mvnExecutableName);

    if (!mavenExecutable.exists()) {
        throw new DepViewerException("Maven executable not found at: " + mavenExecutable.getAbsolutePath());
    }

    Invoker invoker = new DefaultInvoker();
    invoker.setMavenHome(mavenHomeFile);
    invoker.setMavenExecutable(mavenExecutable);

    InvocationRequest request = new DefaultInvocationRequest();
    request.setPomFile(new File(projectDir, moduleDir + "/pom.xml"));
    request.addArgs(Arrays.asList(goals.trim().split(" ")));
    request.setBatchMode(true);
    request.setMavenOpts(mavenOpts.trim());
    request.addShellEnvironment("JAVA_HOME", jdkPath);
    log.info("Invoker executing Maven command goals: {} and OPTS: {} with JAVA_HOME: {} on repository: {}", goals, mavenOpts,
             jdkPath, projectDir);

    var outputHandler = new CollectingOutputHandler();
    request.setOutputHandler(outputHandler);

    InvocationResult result = null;
    try {
      result = invoker.execute(request);

      if (result.getExitCode() != 0) {
        var depViewerException = new DepViewerException("Build failed!");
        log.error("Build failed!", depViewerException);
        throw depViewerException;
      }
    } catch (Exception e) {
      throw new DepViewerException(e);
    }


    return outputHandler;
  }
}
