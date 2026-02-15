package com.botsteve.mavendepsearcher.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class DependencyNode {

  @JsonProperty("groupId")
  private String groupId;

  @JsonProperty("artifactId")
  private String artifactId;

  @JsonProperty("version")
  private String version;

  @JsonProperty("scmUrl")
  private String scmUrl;

  @JsonProperty("children")
  private List<DependencyNode> children;

  @JsonIgnore
  private StringProperty checkoutTag = new SimpleStringProperty("");

  @JsonIgnore
  private StringProperty buildWith = new SimpleStringProperty("");

  @JsonIgnore
  private BooleanProperty selected = new SimpleBooleanProperty(false);

  public DependencyNode(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public String getBuildWith() {
    return buildWith.get();
  }

  public StringProperty buildWithProperty() {
    return buildWith;
  }

  public void setBuildWith(String buildWith) {
    this.buildWith.set(buildWith);
  }

  public BooleanProperty selectedProperty() {
    return selected;
  }

  public boolean isSelected() {
    return selected.get();
  }

  public void setSelected(boolean selected) {
    this.selected.set(selected);
  }

  public void setCheckoutTag(String checkoutTag) {
    this.checkoutTag.set(checkoutTag);
  }

  public String getCheckoutTag() {
    return this.checkoutTag.get();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DependencyNode that = (DependencyNode) o;
    return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId)
           && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, version);
  }
}
