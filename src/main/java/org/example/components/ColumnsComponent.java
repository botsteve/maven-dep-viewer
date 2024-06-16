package org.example.components;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import javafx.scene.input.MouseEvent;
import lombok.Data;
import org.example.model.DependencyNode;

@Data
public class ColumnsComponent {

  private final TableViewComponent tableViewComponent;

  public TreeTableColumn<DependencyNode, String> getBuildWithColumn() {
    TreeTableColumn<DependencyNode, String> buildWithColumn = new TreeTableColumn<>("Build with");
    buildWithColumn.setCellValueFactory(param -> getSimpleStringProperty(param.getValue().getValue().getBuildWith()));
    buildWithColumn.setMinWidth(100);
    buildWithColumn.setMaxWidth(100);
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

    // Prevent row selection when clicking checkbox
    selectColumn.setCellFactory(tc -> {
      CheckBoxTreeTableCell<DependencyNode, Boolean> cell = new CheckBoxTreeTableCell<>();
      cell.addEventFilter(MouseEvent.MOUSE_PRESSED, (MouseEvent event) -> {
        event.consume(); // Consume the event to prevent row selection
        if (event.getClickCount() == 1) {
          DependencyNode item = cell.getTreeTableRow().getItem();
          if (item != null) {
            item.setSelected(!item.isSelected()); // Toggle the checkbox
          }
        }
      });
      return cell;
    });
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
    // Calculate remaining space (as an ObservableValue)
    DoubleBinding remainingWidth = Bindings.createDoubleBinding(() -> {
      double totalWidth = tableViewComponent.getTreeTableView().getWidth();
      double checkboxWidth = selectColumn.getWidth();
      double buildWithWidth = buildWithColumn.getWidth();

      return Math.max(0, totalWidth - checkboxWidth - buildWithWidth);
    }, tableViewComponent.getTreeTableView().widthProperty(), selectColumn.widthProperty());

    // Bind each column to half of the remaining space
    dependencyColumn.prefWidthProperty().bind(remainingWidth.divide(3));
    scmColumn.prefWidthProperty().bind(remainingWidth.divide(3));
    checkoutTagColumn.prefWidthProperty().bind(remainingWidth.divide(3));
  }
}
