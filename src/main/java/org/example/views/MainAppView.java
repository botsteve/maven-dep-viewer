package org.example.views;


import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Data;
import org.example.components.ButtonsComponent;
import org.example.components.CheckBoxComponent;
import org.example.components.ColumnsComponent;
import org.example.components.ContextMenuComponent;
import org.example.components.MenuComponent;
import org.example.components.ProgressBoxComponent;
import org.example.components.TableViewComponent;

@Data
public class MainAppView extends Application {

  private final TableViewComponent tableViewComponent = new TableViewComponent();
  private final ColumnsComponent columnsComponent = new ColumnsComponent(tableViewComponent);
  private final CheckBoxComponent checkBoxComponent = new CheckBoxComponent(tableViewComponent);
  private final ButtonsComponent buttonsComponent = new ButtonsComponent(tableViewComponent);
  private final ContextMenuComponent contextMenuComponent = new ContextMenuComponent(tableViewComponent);
  private final MenuComponent menuComponent = new MenuComponent();
  private final ProgressBoxComponent progressBoxComponent = new ProgressBoxComponent();

  @Override
  public void start(Stage primaryStage) {

    BorderPane root = new BorderPane();
    Scene scene = new Scene(root, 1000, 800);
    var treeTableView = tableViewComponent.getTreeTableView();
    var dependencyColumn = columnsComponent.getDependencyTreeTableColumn();
    var scmColumn = columnsComponent.getSCMTreeTableColumn();
    var selectColumn = columnsComponent.getSelectTreeTableColumn();
    var checkoutTagColumn = columnsComponent.getCheckoutTagColumn();
    var buildWithColumn = columnsComponent.getBuildWithColumn();
    treeTableView.getColumns().addAll(selectColumn, dependencyColumn, scmColumn, checkoutTagColumn, buildWithColumn);
    columnsComponent.configureColumnsWidthStyle(selectColumn, dependencyColumn, scmColumn, checkoutTagColumn, buildWithColumn);
    checkBoxComponent.configureCheckBoxAction();

    var progressBar = progressBoxComponent.createProgressBar();
    var progressLabel = progressBoxComponent.createProgressLabel();
    var progressBox = progressBoxComponent.createProgressBox(progressBar, progressLabel, scene);
    var toolBar = buttonsComponent.getToolBar(primaryStage, progressBar, progressLabel);
    var toolsBox = tableViewComponent.creatToolsBox();
    var contextMenu = contextMenuComponent.createContextMenu(dependencyColumn, scmColumn);
    treeTableView.setContextMenu(contextMenu);

    VBox vbox = new VBox(10);
    vbox.getChildren().addAll(toolBar, toolsBox, progressBox, treeTableView);
    VBox.setVgrow(treeTableView, Priority.ALWAYS);

    root.setCenter(vbox);

    // Configure MenuBar
    var menuBar = menuComponent.getMenuBar(primaryStage);
    root.setTop(menuBar);

    Label developerLabel = new Label("Developed by Rusen Stefan @ Oracle");
    BorderPane.setAlignment(developerLabel, Pos.BOTTOM_CENTER);
    root.setBottom(developerLabel);
    primaryStage.setTitle("Maven Dependencies Viewer");
    primaryStage.setScene(scene);
    primaryStage.show();
  }


  public static void main(String[] args) {
    launch(args);
  }
}
