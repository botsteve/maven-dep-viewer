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
                                         TreeTableColumn<DependencyNode, String> scmColumn,
                                         TreeTableColumn<DependencyNode, String> checkoutTagColumn,
                                         TreeTableColumn<DependencyNode, String> buildWithColumn) {
    // Set initial widths to distribute space roughly, but allow resizing
    // Assuming visible width ~1000. 50 (select) + others.
    dependencyColumn.setPrefWidth(250);
    scmColumn.setPrefWidth(250);
    checkoutTagColumn.setPrefWidth(200);
    buildWithColumn.setPrefWidth(250);
  }
}
