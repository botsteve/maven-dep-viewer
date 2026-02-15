package com.botsteve.mavendepsearcher.components;

import static com.botsteve.mavendepsearcher.utils.FxUtils.createBox;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;

public class ProgressBoxComponent {

  public Label createProgressLabel() {
    Label progressLabel = new Label();
    progressLabel.setVisible(false);
    return progressLabel;
  }

  public ProgressBar createProgressBar() {
    ProgressBar progressBar = new ProgressBar();
    progressBar.setVisible(false);
    return progressBar;
  }

  public HBox createProgressBox(ProgressBar progressBar, Label progressLabel, Scene scene) {
    var progressBox = createBox(new HBox(10, progressBar, progressLabel), Pos.CENTER);
    progressBox.setMaxWidth(Double.MAX_VALUE);
    progressBox.prefWidthProperty().bind(scene.widthProperty());

    progressBar.setMaxWidth(Double.MAX_VALUE);
    progressBar.prefWidthProperty().bind(progressBox.widthProperty().multiply(0.8));
    progressLabel.prefWidthProperty().bind(progressBox.widthProperty().multiply(0.2));
    return progressBox;
  }
}
