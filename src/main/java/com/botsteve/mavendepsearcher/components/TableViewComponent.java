package com.botsteve.mavendepsearcher.components;

import static com.botsteve.mavendepsearcher.utils.FxUtils.createBox;

import java.util.HashSet;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import lombok.Data;
import com.botsteve.mavendepsearcher.model.DependencyNode;

@Data
public class TableViewComponent {

  private final TreeTableView<DependencyNode> treeTableView = new TreeTableView<>();
  private ObservableSet<DependencyNode> allDependencies = FXCollections.observableSet();
  private ObservableSet<DependencyNode> selectedDependencies = FXCollections.observableSet();
  private final Label filterLabel = new Label("Exclude:");
  private final Label statsLabel = new Label("Dependencies: 0");
  private final TextField filterInput = new TextField();
  private final CheckBox selectAllCheckBox = new CheckBox("Select All");


  public TableViewComponent() {
    treeTableView.setShowRoot(false);
    filterInput.setPromptText("Exclude dependencies...");
    filterInput.textProperty().addListener((observable, oldValue, newValue) -> {
      updateTreeViewWithFilteredDependencies(newValue);
    });
  }

  public void updateTreeViewWithFilteredDependencies(String newValue) {
    if (this.allDependencies != null) {
      Set<DependencyNode> filteredDependencies = filterDependencies(newValue);
      updateTreeView(filteredDependencies);
    }
  }

  public Set<DependencyNode> filterDependencies(String filterText) {
    Set<DependencyNode> filteredDependencies = new HashSet<>();
    for (DependencyNode node : allDependencies) {
      if (isTextMatchingFilter(node, filterText)) {
        filteredDependencies.add(node);
      }
    }
    return filteredDependencies;
  }

  private boolean isTextMatchingFilter(DependencyNode node, String filterText) {
    if (filterText == null || filterText.isEmpty()) {
      return true;
    }
    String dependencyText = node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion();
    return !dependencyText.toLowerCase().contains(filterText.toLowerCase());
  }

  public void setAllDependencies(ObservableSet<DependencyNode> allDependencies) {
    this.allDependencies = allDependencies;
    for (DependencyNode node : allDependencies) {
      if (node.isSelected()) {
        selectedDependencies.add(node);
      }
      node.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
        if (isNowSelected) {
          selectedDependencies.add(node);
        } else {
          selectedDependencies.remove(node);
        }
      });
    }
  }

  public void updateTreeView(Set<DependencyNode> dependencies) {
    TreeItem<DependencyNode> rootItem = createRootTreeItem();
    populateTreeItems(rootItem, dependencies);
    treeTableView.setRoot(rootItem);
    statsLabel.setText("Dependencies: " + dependencies.size());
  }

  private TreeItem<DependencyNode> createRootTreeItem() {
    return new TreeItem<>(new DependencyNode("Root", "", ""));
  }

  private void populateTreeItems(TreeItem<DependencyNode> rootItem, Set<DependencyNode> dependencies) {
    for (DependencyNode node : dependencies) {
      rootItem.getChildren().add(createTreeItem(node));
    }
  }

  private TreeItem<DependencyNode> createTreeItem(DependencyNode node) {
    TreeItem<DependencyNode> treeItem = new TreeItem<>(node);
    addChildrenToTreeItem(node, treeItem);
    return treeItem;
  }

  private void addChildrenToTreeItem(DependencyNode node, TreeItem<DependencyNode> treeItem) {
    if (node.getChildren() != null) {
      for (DependencyNode child : node.getChildren()) {
        treeItem.getChildren().add(createTreeItem(child));
      }
    }
  }

  public HBox creatToolsBox() {
    return createBox(new HBox(10, filterLabel, filterInput, statsLabel, selectAllCheckBox), Pos.CENTER_LEFT);
  }
}
