package org.example.components;

import javafx.scene.control.TreeItem;
import lombok.Data;
import org.example.model.DependencyNode;

@Data
public class CheckBoxComponent {

  private final TableViewComponent tableViewComponent;

  public void configureCheckBoxAction() {
    tableViewComponent.getSelectAllCheckBox().setOnAction(event -> {
      boolean selectAll = tableViewComponent.getSelectAllCheckBox().isSelected();
      for (TreeItem<DependencyNode> item : tableViewComponent.getTreeTableView().getRoot().getChildren()) {
        item.getValue().setSelected(selectAll);
      }
    });
  }
}
