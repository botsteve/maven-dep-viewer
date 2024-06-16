package org.example.utils;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FxUtils {

  public static HBox createBox(HBox box, Pos position) {
    box.setAlignment(position);
    return box;
  }

  public static void showAlert(String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    Label label = new Label(message);
    label.setWrapText(true);
    alert.getDialogPane().setContent(label);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.setTitle("INFO");
    alert.setHeaderText("");
    alert.showAndWait();
  }

  public static void showError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    Label label = new Label(message);
    label.setWrapText(true);
    alert.getDialogPane().setContent(label);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.setTitle("ERROR");
    alert.setHeaderText("");
    alert.showAndWait();
  }

  public static void getErrorAlertAndCloseProgressBar(String message,
                                                      ProgressBar progressBar,
                                                      Label progressLabel) {
    Platform.runLater(() -> {
      log.debug("Closing progress bar and clear label");
      progressBar.setVisible(false);
      progressLabel.setVisible(false);
      showError(message);
    });
  }

  public static void updateProgressBarAndLabel(String message, ProgressBar progressBar, Label progressLabel) {
    Platform.runLater(() -> {
      log.debug("Enable progress bar and configure label message: {}", message);
      progressBar.setVisible(true);
      progressLabel.setVisible(true);
      progressLabel.setText(message);
    });
  }
}
