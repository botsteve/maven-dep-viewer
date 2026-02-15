package com.botsteve.mavendepsearcher.components;

import static com.botsteve.mavendepsearcher.utils.FxUtils.createBox;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
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
  private static final String ALL_SCOPES = "All Scopes";
  private final Label filterLabel = new Label("Exclude:");
  private final Label statsLabel = new Label("Dependencies: 0");
  private final TextField filterInput = new TextField();
  private final ComboBox<String> scopeFilter = new ComboBox<>();
  private final CheckBox selectAllCheckBox = new CheckBox("Select All");
  private final CheckBox cleanUpCheckBox = new CheckBox("Clean up existing repos");


  public TableViewComponent() {
    treeTableView.setShowRoot(false);
    treeTableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
    treeTableView.setEditable(true);
    filterInput.setPromptText("Exclude dependencies...");
    filterInput.textProperty().addListener((observable, oldValue, newValue) -> {
      applyFilters();
    });
    scopeFilter.getItems().add(ALL_SCOPES);
    scopeFilter.setValue(ALL_SCOPES);
    scopeFilter.setOnAction(event -> applyFilters());
    cleanUpCheckBox.setSelected(false);
  }

  /**
   * Applies both the text exclusion filter and scope filter, then updates the tree view.
   */
  public void applyFilters() {
    if (this.allDependencies != null) {
      String filterText = filterInput.getText();
      String selectedScope = scopeFilter.getValue();
      Set<DependencyNode> filteredDependencies = filterDependencies(filterText, selectedScope);
      updateTreeView(filteredDependencies);
    }
  }

  public void updateTreeViewWithFilteredDependencies(String newValue) {
    applyFilters();
  }

  public Set<DependencyNode> filterDependencies(String filterText, String selectedScope) {
    Set<DependencyNode> filteredDependencies = new HashSet<>();
    for (DependencyNode node : allDependencies) {
      if (isTextMatchingFilter(node, filterText) && isScopeMatchingFilter(node, selectedScope)) {
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

  private boolean isScopeMatchingFilter(DependencyNode node, String selectedScope) {
    if (selectedScope == null || ALL_SCOPES.equals(selectedScope)) {
      return true;
    }
    String nodeScope = node.getScope();
    return selectedScope.equals(nodeScope);
  }

  public void setAllDependencies(ObservableSet<DependencyNode> allDependencies) {
    this.allDependencies = allDependencies;
    populateScopeFilter(allDependencies);
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

  /**
   * Populates the scope filter dropdown with all unique scopes from the loaded dependencies.
   */
  private void populateScopeFilter(Set<DependencyNode> dependencies) {
    Set<String> scopes = new TreeSet<>(); // TreeSet for alphabetical ordering
    collectScopes(dependencies, scopes);
    scopeFilter.getItems().clear();
    scopeFilter.getItems().add(ALL_SCOPES);
    scopeFilter.getItems().addAll(scopes);
    scopeFilter.setValue(ALL_SCOPES);
  }

  /**
   * Recursively collects all unique scope values from dependencies and their children.
   */
  private void collectScopes(Set<DependencyNode> dependencies, Set<String> scopes) {
    for (DependencyNode node : dependencies) {
      if (node.getScope() != null && !node.getScope().isEmpty()) {
        scopes.add(node.getScope());
      }
      if (node.getChildren() != null) {
        collectScopes(new HashSet<>(node.getChildren()), scopes);
      }
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
    return createBox(new HBox(10, filterLabel, filterInput, new Label("Scope:"), scopeFilter, statsLabel, selectAllCheckBox, cleanUpCheckBox), Pos.CENTER_LEFT);
  }
}
