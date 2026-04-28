package uz.postbaby.desktop.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import uz.postbaby.desktop.api.BackendException;
import uz.postbaby.desktop.api.EnvironmentApi;
import uz.postbaby.desktop.model.Environment;
import uz.postbaby.desktop.model.Team;
import uz.postbaby.desktop.store.LocalStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EnvironmentDialog {

    public static void show(Team team, Environment initial, EnvironmentApi api,
                            LocalStore store, Runnable onSaved) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Environments — " + team.name);

        ListView<Environment> list = new ListView<>();
        list.setPrefWidth(220);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Environment env, boolean empty) {
                super.updateItem(env, empty);
                setText(empty || env == null ? null : (env.name == null ? "Untitled" : env.name));
            }
        });

        TextField nameField = new TextField();
        nameField.setPromptText("Environment name");

        TableView<EnvVarRow> table = new TableView<>();
        table.setEditable(true);
        TableColumn<EnvVarRow, String> keyCol = new TableColumn<>("Key");
        TableColumn<EnvVarRow, String> valCol = new TableColumn<>("Value");
        keyCol.setCellValueFactory(new PropertyValueFactory<>("key"));
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        keyCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valCol.setCellFactory(TextFieldTableCell.forTableColumn());
        keyCol.setOnEditCommit(e -> {
            e.getRowValue().setKey(e.getNewValue());
            ensureBlank(table.getItems());
        });
        valCol.setOnEditCommit(e -> {
            e.getRowValue().setValue(e.getNewValue());
            ensureBlank(table.getItems());
        });
        keyCol.setPrefWidth(220);
        valCol.setPrefWidth(360);
        table.getColumns().add(keyCol);
        table.getColumns().add(valCol);

        Button addBtn = new Button("New env");
        Button delBtn = new Button("Delete");
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary");
        Label status = new Label();

        VBox left = new VBox(6, list, new HBox(6, addBtn, delBtn));
        VBox right = new VBox(8,
                new Label("Name:"), nameField,
                new Label("Variables:"), table,
                new HBox(8, status, spacer(), saveBtn));
        VBox.setVgrow(table, Priority.ALWAYS);
        right.setPadding(new Insets(10));

        HBox root = new HBox(8, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);
        root.setPadding(new Insets(10));

        // Load list
        List<Environment> cached = store.loadEnvironments(team.id);
        list.setItems(FXCollections.observableArrayList(cached));
        if (initial != null && initial.id != null) {
            cached.stream().filter(e -> e.id != null && e.id.equals(initial.id))
                    .findFirst().ifPresent(e -> list.getSelectionModel().select(e));
        }

        list.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            nameField.setText(val.name == null ? "" : val.name);
            ObservableList<EnvVarRow> rows = FXCollections.observableArrayList();
            if (val.variables != null) {
                val.variables.forEach((k, v) -> rows.add(new EnvVarRow(k, v)));
            }
            ensureBlank(rows);
            table.setItems(rows);
        });

        addBtn.setOnAction(e -> {
            Environment fresh = new Environment();
            fresh.name = "New environment";
            fresh.variables = new LinkedHashMap<>();
            list.getItems().add(fresh);
            list.getSelectionModel().select(fresh);
        });

        delBtn.setOnAction(e -> {
            Environment selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            if (selected.id == null) {
                list.getItems().remove(selected);
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    api.delete(team.id, selected.id);
                    Platform.runLater(() -> list.getItems().remove(selected));
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Delete failed: " + ex.getMessage()));
                }
            });
        });

        saveBtn.setOnAction(e -> {
            Environment selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Pick or create an environment first.");
                return;
            }
            selected.name = nameField.getText().trim();
            LinkedHashMap<String, String> vars = new LinkedHashMap<>();
            for (EnvVarRow row : table.getItems()) {
                if (row.getKey() == null || row.getKey().isBlank()) continue;
                vars.put(row.getKey().trim(), row.getValue() == null ? "" : row.getValue());
            }
            selected.variables = vars;
            selected.team_id = team.id;

            CompletableFuture.runAsync(() -> {
                try {
                    Environment saved = selected.id == null
                            ? api.create(team.id, selected)
                            : api.update(team.id, selected.id, selected);
                    Platform.runLater(() -> {
                        int idx = list.getItems().indexOf(selected);
                        if (idx >= 0) list.getItems().set(idx, saved);
                        list.getSelectionModel().select(saved);
                        status.setText("Saved.");
                        if (onSaved != null) onSaved.run();
                    });
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText(
                            ex.isNetwork() ? "Offline — environments require online." : "Save failed: " + ex.getMessage()));
                }
            });
        });

        Scene scene = new Scene(root, 760, 480);
        var css = EnvironmentDialog.class.getResource("/css/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static void ensureBlank(ObservableList<EnvVarRow> rows) {
        if (rows.isEmpty() || !rows.get(rows.size() - 1).isBlank()) {
            rows.add(new EnvVarRow("", ""));
        }
    }

    public static class EnvVarRow {
        private final javafx.beans.property.SimpleStringProperty key = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.SimpleStringProperty value = new javafx.beans.property.SimpleStringProperty();

        public EnvVarRow(String k, String v) {
            this.key.set(k == null ? "" : k);
            this.value.set(v == null ? "" : v);
        }

        public String getKey() { return key.get(); }
        public void setKey(String k) { key.set(k); }
        public javafx.beans.property.SimpleStringProperty keyProperty() { return key; }

        public String getValue() { return value.get(); }
        public void setValue(String v) { value.set(v); }
        public javafx.beans.property.SimpleStringProperty valueProperty() { return value; }

        public boolean isBlank() {
            return (key.get() == null || key.get().isBlank()) && (value.get() == null || value.get().isBlank());
        }
    }
}
