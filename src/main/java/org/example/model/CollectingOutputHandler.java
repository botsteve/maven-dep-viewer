package org.example.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.shared.invoker.InvocationOutputHandler;

@Data
@Slf4j
public class CollectingOutputHandler implements InvocationOutputHandler {

  private final List<String> output = new ArrayList<>();

  @Override
  public void consumeLine(String line) {
    output.add(line);
    log.info(line);
  }
}