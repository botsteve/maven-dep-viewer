package com.botsteve.mavendepsearcher.components;

import static com.botsteve.mavendepsearcher.utils.FxUtils.createBox;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
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
  private final MenuButton scopeFilterMenu = new MenuButton(ALL_SCOPES);
  private final Set<String> selectedScopes = new LinkedHashSet<>();
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
    scopeFilterMenu.setMinWidth(120);
    cleanUpCheckBox.setSelected(false);
  }

  /**
   * Applies both the text exclusion filter and scope filter, then updates the tree view.
   */
  public void applyFilters() {
    if (this.allDependencies != null) {
      String filterText = filterInput.getText();
      Set<DependencyNode> filteredDependencies = filterDependencies(filterText, selectedScopes);
      updateTreeView(filteredDependencies);
    }
  }

  public void updateTreeViewWithFilteredDependencies(String newValue) {
    applyFilters();
  }

  public Set<DependencyNode> filterDependencies(String filterText, Set<String> scopes) {
    Set<DependencyNode> filteredDependencies = new HashSet<>();
    for (DependencyNode node : allDependencies) {
      if (isTextMatchingFilter(node, filterText) && isScopeMatchingFilter(node, scopes)) {
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

  private boolean isScopeMatchingFilter(DependencyNode node, Set<String> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return true; // No scopes selected = show all
    }
    String nodeScope = node.getScope();
    return nodeScope != null && scopes.contains(nodeScope);
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
   * Populates the scope filter MenuButton with CheckMenuItems for each unique scope.
   */
  private void populateScopeFilter(Set<DependencyNode> dependencies) {
    Set<String> scopes = new TreeSet<>(); // TreeSet for alphabetical ordering
    collectScopes(dependencies, scopes);

    scopeFilterMenu.getItems().clear();
    selectedScopes.clear();

    // Add "All Scopes" toggle item
    CheckMenuItem allItem = new CheckMenuItem(ALL_SCOPES);
    allItem.setSelected(true);
    allItem.setOnAction(event -> {
      if (allItem.isSelected()) {
        // Deselect all individual scope items
        selectedScopes.clear();
        scopeFilterMenu.getItems().stream()
            .filter(item -> item instanceof CheckMenuItem && !ALL_SCOPES.equals(item.getText()))
            .forEach(item -> ((CheckMenuItem) item).setSelected(false));
      }
      updateScopeButtonText();
      applyFilters();
    });
    scopeFilterMenu.getItems().add(allItem);

    // Add individual scope items
    for (String scope : scopes) {
      CheckMenuItem item = new CheckMenuItem(scope);
      item.setOnAction(event -> {
        if (item.isSelected()) {
          selectedScopes.add(scope);
          // Uncheck "All Scopes" when a specific scope is selected
          allItem.setSelected(false);
        } else {
          selectedScopes.remove(scope);
          // If nothing is selected, re-check "All Scopes"
          if (selectedScopes.isEmpty()) {
            allItem.setSelected(true);
          }
        }
        updateScopeButtonText();
        applyFilters();
      });
      scopeFilterMenu.getItems().add(item);
    }

    updateScopeButtonText();
  }

  /**
   * Updates the MenuButton label to reflect the current selection.
   */
  private void updateScopeButtonText() {
    if (selectedScopes.isEmpty()) {
      scopeFilterMenu.setText(ALL_SCOPES);
    } else if (selectedScopes.size() == 1) {
      scopeFilterMenu.setText(selectedScopes.iterator().next());
    } else {
      scopeFilterMenu.setText(selectedScopes.size() + " scopes");
    }
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
    rootItem.setExpanded(true);
    populateTreeItems(rootItem, dependencies);
    treeTableView.setRoot(rootItem);
    treeTableView.refresh();
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
    return createBox(new HBox(10, filterLabel, filterInput, new Label("Scope:"), scopeFilterMenu, statsLabel, selectAllCheckBox, cleanUpCheckBox), Pos.CENTER_LEFT);
  }
}
