package com.botsteve.mavendepsearcher.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import lombok.Data;
import com.botsteve.mavendepsearcher.model.DependencyNode;

@Data
public class ColumnsComponent {

  private final TableViewComponent tableViewComponent;

  public TreeTableColumn<DependencyNode, String> getBuildWithColumn() {
    TreeTableColumn<DependencyNode, String> buildWithColumn = new TreeTableColumn<>("Output");
    buildWithColumn.setCellValueFactory(param -> getSimpleStringProperty(param.getValue().getValue().getBuildWith()));
    buildWithColumn.setCellFactory(column -> new javafx.scene.control.TreeTableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          getStyleClass().removeAll("status-ok", "status-failed");
        } else {
          setText(item);
          getStyleClass().removeAll("status-ok", "status-failed");
          if (item.contains("Build OK")) {
            getStyleClass().add("status-ok");
          } else if (item.contains("Failed") || item.contains("Internal Error")) {
            getStyleClass().add("status-failed");
          }
        }
      }
    });
    buildWithColumn.setMinWidth(100);
    return buildWithColumn;
  }

  public TreeTableColumn<DependencyNode, String> getCheckoutTagColumn() {
    TreeTableColumn<DependencyNode, String> checkoutTag = new TreeTableColumn<>("Repo checkout tag");
    checkoutTag.setCellValueFactory(param -> getSimpleStringProperty(param.getValue().getValue().getCheckoutTag()));
    return checkoutTag;
  }

  public TreeTableColumn<DependencyNode, String> getSCMTreeTableColumn() {
    TreeTableColumn<DependencyNode, String> scmColumn = new TreeTableColumn<>("SCM URL");
    scmColumn.setCellValueFactory(param -> getSimpleStringProperty(getSCMColumnValue(param)));
    scmColumn.setCellFactory(column -> new javafx.scene.control.TreeTableCell<>() {
      private final javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink();
      {
        link.setOnAction(event -> {
          String url = itemProperty().get();
          if (url != null && !url.isEmpty() && !"SCM URL not found".equals(url)) {
            new Thread(() -> {
              try {
                if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                  Runtime.getRuntime().exec(new String[]{"open", url});
                } else if (System.getProperty("os.name").toLowerCase().contains("win")) {
                  Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                }
              } catch (Exception e) {
                // Ignore
              }
            }).start();
          }
        });
      }
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null || item.isEmpty() || "SCM URL not found".equals(item)) {
          setGraphic(null);
          setText(item);
          if ("SCM URL not found".equals(item)) {
            setStyle("-fx-text-fill: #95a5a6;");
          } else {
            setStyle("");
          }
        } else {
          link.setText(item);
          setGraphic(link);
          setText(null);
          setStyle("");
        }
      }
    });
    return scmColumn;
  }

  public TreeTableColumn<DependencyNode, Boolean> getSelectTreeTableColumn() {
    TreeTableColumn<DependencyNode, Boolean> selectColumn = new TreeTableColumn<>("Select");
    selectColumn.setCellValueFactory(param -> param.getValue().getValue().selectedProperty());
    selectColumn.setCellFactory(CheckBoxTreeTableCell.forTreeTableColumn(selectColumn));
    selectColumn.setMinWidth(50); // Set a fixed width for the checkbox column
    selectColumn.setMaxWidth(50);
    selectColumn.setEditable(true);
    return selectColumn;
  }

  public TreeTableColumn<DependencyNode, String> getDependencyTreeTableColumn() {
    TreeTableColumn<DependencyNode, String> dependencyColumn = new TreeTableColumn<>("Dependency");
    dependencyColumn.setCellValueFactory(param -> getSimpleStringProperty(getDependencyColumnValue(param)));
    return dependencyColumn;
  }

  public TreeTableColumn<DependencyNode, String> getScopeColumn() {
    TreeTableColumn<DependencyNode, String> scopeColumn = new TreeTableColumn<>("Scope");
    scopeColumn.setCellValueFactory(param -> {
      String scope = param.getValue().getValue().getScope();
      return getSimpleStringProperty(scope != null ? scope : "");
    });
    scopeColumn.setMinWidth(80);
    return scopeColumn;
  }


  private String getSCMColumnValue(TreeTableColumn.CellDataFeatures<DependencyNode, String> param) {
    return param.getValue().getValue().getScmUrl();
  }

  private String getDependencyColumnValue(TreeTableColumn.CellDataFeatures<DependencyNode, String> param) {
    return param.getValue().getValue().getGroupId() + ":" +
           param.getValue().getValue().getArtifactId() + ":" +
           param.getValue().getValue().getVersion();
  }

  private SimpleStringProperty getSimpleStringProperty(String value) {
    return new SimpleStringProperty(value);
  }

  public void configureColumnsWidthStyle(TreeTableColumn<DependencyNode, Boolean> selectColumn,
                                         TreeTableColumn<DependencyNode, String> dependencyColumn,
                                         TreeTableColumn<DependencyNode, String> scopeColumn,
                                         TreeTableColumn<DependencyNode, String> scmColumn,
                                         TreeTableColumn<DependencyNode, String> checkoutTagColumn,
                                         TreeTableColumn<DependencyNode, String> buildWithColumn) {
    // Set initial widths to distribute space roughly, but allow resizing
    dependencyColumn.setPrefWidth(230);
    scopeColumn.setPrefWidth(110);
    scmColumn.setPrefWidth(220);
    checkoutTagColumn.setPrefWidth(170);
    buildWithColumn.setPrefWidth(220);
  }
}
