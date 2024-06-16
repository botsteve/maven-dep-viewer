package org.example.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.example.exception.DepViewerException;
import org.example.model.CollectingOutputHandler;

@Slf4j
public class MavenInvokerService {


  public static CollectingOutputHandler getMavenInvokerResult(String projectDir,
                                                              String moduleDir,
                                                              String goals,
                                                              String mavenOpts,
                                                              String jdkPath) {
    // Set Maven executable and home
    Invoker invoker = new DefaultInvoker();
    invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
    invoker.setMavenExecutable(new File(System.getenv("MAVEN_HOME"), "bin/mvn"));

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
