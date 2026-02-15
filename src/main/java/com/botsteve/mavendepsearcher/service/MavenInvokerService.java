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
    Invoker invoker = new DefaultInvoker();
    invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
    
    String mavenHome = System.getenv("MAVEN_HOME");
    String os = System.getProperty("os.name").toLowerCase();
    String mvnExecutable = os.contains("win") ? "bin/mvn.cmd" : "bin/mvn";
    invoker.setMavenExecutable(new File(mavenHome, mvnExecutable));

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
