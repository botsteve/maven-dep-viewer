package com.botsteve.mavendepsearcher.components;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import lombok.Data;
import com.botsteve.mavendepsearcher.model.DependencyNode;

@Data
public class ContextMenuComponent {

  private final TableViewComponent tableViewComponent;

  public ContextMenu createContextMenu(TreeTableColumn<DependencyNode, String> dependencyColumn,
                                       TreeTableColumn<DependencyNode, String> scmColumn) {
    ContextMenu contextMenu = new ContextMenu();
    var copyMenuItem = createCopyContextMenuItem(dependencyColumn, scmColumn);
    contextMenu.getItems().add(copyMenuItem);
    return contextMenu;
  }

  private MenuItem createCopyContextMenuItem(TreeTableColumn<DependencyNode, String> dependencyColumn,
                                             TreeTableColumn<DependencyNode, String> scmColumn) {
    MenuItem copyMenuItem = new MenuItem("Copy");

    copyMenuItem.setOnAction(event -> {
      TreeTablePosition<DependencyNode, ?> pos = tableViewComponent.getTreeTableView().getFocusModel().getFocusedCell();
      if (pos != null) {
        TreeTableColumn<DependencyNode, ?> column = pos.getTableColumn();
        TreeItem<DependencyNode> treeItem = pos.getTreeItem();
        String contentToCopy = "";

        if (column == dependencyColumn) { // Directly compare with the column reference
          contentToCopy = treeItem.getValue().getGroupId() + ":" +
                          treeItem.getValue().getArtifactId() + ":" +
                          treeItem.getValue().getVersion();
        } else if (column == scmColumn) { // Directly compare with the column reference
          contentToCopy = treeItem.getValue().getScmUrl();
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(contentToCopy);
        Clipboard.getSystemClipboard().setContent(content);
      }
    });
    return copyMenuItem;
  }
}
