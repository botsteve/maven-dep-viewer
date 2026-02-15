package com.botsteve.mavendepsearcher.components;

import static com.botsteve.mavendepsearcher.utils.Utils.loadSettings;
import static com.botsteve.mavendepsearcher.utils.Utils.saveSettings;

import java.util.Properties;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.stage.FileChooser;
import java.io.IOException;
import javafx.scene.control.CheckMenuItem;
import com.botsteve.mavendepsearcher.utils.FxUtils;
import com.botsteve.mavendepsearcher.model.EnvSetting;
import java.io.File;

@Data
public class MenuComponent { 

  private final TableViewComponent tableViewComponent;

  public static final String ABOUT_TEXT = """
          This application analyzes the dependency tree of a Maven project and attempts to resolve the source code URLs for each dependency.
          Users can then filter and select the necessary dependencies to download their source code and subsequently build the dependencies from the source.
          Given that some repositories require specific Java versions for compilation, and to prevent newer warnings that might be treated as errors, it is essential to configure the JAVA11_HOME and JAVA17_HOME environment variables. Additionally, as build artifacts do not have a predefined target location and projects may use either Gradle or Maven, the process of uploading these artifacts to an internal repository must be performed manually.
          The Configuration for the JAVA8, JAVA11 AND JAVA17 paths can be done in the settings/environment settings.
          Requirements:
              - JAVA_HOME should be configured and point to JDK version 21 or higher.
              - MAVEN_HOME should be configured and point to a local Maven installation.
      """;

  public void openSettingsDialog(Stage primaryStage) {
    // Load existing settings or create new ones
    Properties settings = loadSettings();

    // Create dialog components (TableView, TableColumn, TextField, Button)
    TableView<EnvSetting> tableView = new TableView<>();
    TableColumn<EnvSetting, String> nameCol = new TableColumn<>("Name");
    nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());

    TableColumn<EnvSetting, String> valueCol = new TableColumn<>("Value");
    valueCol.setCellValueFactory(cellData -> cellData.getValue().valueProperty());
    valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
    valueCol.setOnEditCommit(event -> {
      EnvSetting setting = event.getRowValue();
      setting.setValue(event.getNewValue());
    });

    // Ensure columns are equally distributed
    tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    nameCol.setMaxWidth(1f * Integer.MAX_VALUE * 50); // 50% width
    valueCol.setMaxWidth(1f * Integer.MAX_VALUE * 50); // 50% width

    tableView.getColumns().addAll(nameCol, valueCol);
    tableView.setEditable(true);

    ObservableList<EnvSetting> settingsList = FXCollections.observableArrayList();

    settingsList.addAll(new EnvSetting(new SimpleStringProperty("JAVA8_HOME"),
                                       new SimpleStringProperty(settings.getProperty("JAVA8_HOME", ""))),
                        new EnvSetting(new SimpleStringProperty("JAVA11_HOME"),
                                       new SimpleStringProperty(settings.getProperty("JAVA11_HOME", ""))),
                        new EnvSetting(new SimpleStringProperty("JAVA17_HOME"),
                                       new SimpleStringProperty(settings.getProperty("JAVA17_HOME", "")))
    );
    tableView.setItems(settingsList);

    Button saveButton = new Button("Save");
    saveButton.setOnAction(event -> {
      saveSettings(settingsList);
      ((Stage) saveButton.getScene().getWindow()).close(); // Close dialog
    });

    Button closeButton = new Button("Close");
    closeButton.setOnAction(event -> {
      ((Stage) closeButton.getScene().getWindow()).close(); // Close dialog
    });

    // Layout
    BorderPane dialogRoot = new BorderPane();
    dialogRoot.setCenter(tableView);
    dialogRoot.setBottom(new ToolBar(saveButton, closeButton));

    Scene dialogScene = new Scene(dialogRoot, 800, 300);
    Stage dialogStage = new Stage();
    dialogStage.setScene(dialogScene);
    dialogStage.setTitle("Environment Settings");
    dialogStage.initOwner(primaryStage); // Set owner to maintain focus hierarchy
    dialogStage.showAndWait(); // Show dialog and wait for it to be closed
  }

  private void openAboutDialog() {
    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle("About Project");
    dialog.initModality(Modality.APPLICATION_MODAL);

    TextArea textArea = new TextArea(ABOUT_TEXT);
    textArea.setEditable(false);
    textArea.setWrapText(true);

    VBox dialogVBox = new VBox(textArea);
    dialogVBox.setPrefSize(600, 400);

    // Set the TextArea to fill the VBox
    VBox.setVgrow(textArea, Priority.ALWAYS);
    textArea.setMaxHeight(Double.MAX_VALUE);
    textArea.setMaxWidth(Double.MAX_VALUE);

    dialog.getDialogPane().setContent(dialogVBox);

    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().add(okButtonType);

    dialog.showAndWait();
  }


  public Menu getAboutMenu() {
    Menu about = new Menu("About");
    MenuItem readMe = new MenuItem("Read Me");
    readMe.setOnAction(e -> openAboutDialog());
    about.getItems().add(readMe);
    return about;
  }

  public Menu getSettingsMenu(Stage primaryStage) {
    Menu settingsMenu = new Menu("Settings");
    MenuItem envSettingsItem = new MenuItem("Environment Settings");
    envSettingsItem.setOnAction(event -> openSettingsDialog(primaryStage));
    settingsMenu.getItems().add(envSettingsItem);
    return settingsMenu;
  }

  public Menu getFileMenu(Stage primaryStage) {
    Menu fileMenu = new Menu("File");
    MenuItem exportItem = new MenuItem("Export to JSON");
    exportItem.setOnAction(event -> exportDependencies(primaryStage));
    fileMenu.getItems().add(exportItem);
    return fileMenu;
  }

  public Menu getViewMenu(Stage primaryStage) {
    Menu viewMenu = new Menu("View");
    CheckMenuItem darkModeItem = new CheckMenuItem("Dark Mode");
    darkModeItem.setOnAction(event -> {
      Scene scene = primaryStage.getScene();
      if (scene != null) {
        if (darkModeItem.isSelected()) {
          scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
          scene.getRoot().getStyleClass().add("dark-mode");
        } else {
          scene.getRoot().getStyleClass().remove("dark-mode");
        }
      }
    });
    viewMenu.getItems().add(darkModeItem);
    return viewMenu;
  }

  private void exportDependencies(Stage primaryStage) {
     FileChooser fileChooser = new FileChooser();
     fileChooser.setTitle("Save Dependencies");
     fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
     File file = fileChooser.showSaveDialog(primaryStage);
     if (file != null) {
         try {
             ObjectMapper mapper = new ObjectMapper();
             mapper.writerWithDefaultPrettyPrinter().writeValue(file, tableViewComponent.getAllDependencies());
             FxUtils.showAlert("Dependencies exported successfully!");
         } catch (IOException e) {
             FxUtils.showError("Failed to export dependencies: " + e.getMessage());
         }
     }
  }

  public MenuBar getMenuBar(Stage primaryStage) {
    MenuBar menuBar = new MenuBar();
    var fileMenu = getFileMenu(primaryStage);
    var settingsMenu = getSettingsMenu(primaryStage);
    var viewMenu = getViewMenu(primaryStage);
    var aboutMenu = getAboutMenu();

    menuBar.getMenus().addAll(fileMenu, settingsMenu, viewMenu, aboutMenu);
    return menuBar;
  }
}
