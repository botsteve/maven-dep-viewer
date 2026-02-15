package com.botsteve.mavendepsearcher.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VersionScm {

  private String version;
  private String url;
}
