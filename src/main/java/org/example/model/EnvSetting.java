package org.example.model;

import javafx.beans.property.StringProperty;
import lombok.Data;

@Data
public class EnvSetting {

  private final StringProperty name;
  private final StringProperty value;

  public String getName() {
    return name.get();
  }

  public void setName(String name) {
    this.name.set(name);
  }

  public StringProperty nameProperty() {
    return name;
  }

  public String getValue() {
    return value.get();
  }

  public void setValue(String value) {
    this.value.set(value);
  }

  public StringProperty valueProperty() {
    return value;
  }
}