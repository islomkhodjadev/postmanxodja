package uz.postbaby.desktop.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import uz.postbaby.desktop.api.ApiKeyApi;
import uz.postbaby.desktop.api.BackendException;
import uz.postbaby.desktop.model.ApiKey;
import uz.postbaby.desktop.model.Team;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ApiKeysDialog {

    public static void show(Team team, ApiKeyApi api) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("API Keys — " + team.name);

        Label title = new Label("API Keys for " + team.name);
        title.getStyleClass().add("title");
        Label status = new Label();
        status.getStyleClass().add("status");

        TableView<ApiKey> table = new TableView<>();
        TableColumn<ApiKey, String> nameCol = new TableColumn<>("Name");
        TableColumn<ApiKey, String> prefixCol = new TableColumn<>("Prefix");
        TableColumn<ApiKey, String> permsCol = new TableColumn<>("Permissions");
        TableColumn<ApiKey, String> expCol = new TableColumn<>("Expires");
        TableColumn<ApiKey, String> usedCol = new TableColumn<>("Last used");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        prefixCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().key_prefix + "…"));
        permsCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().permissions));
        expCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().expires_at == null ? "never" : c.getValue().expires_at));
        usedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().last_used_at == null ? "—" : c.getValue().last_used_at));
        nameCol.setPrefWidth(180);
        prefixCol.setPrefWidth(120);
        permsCol.setPrefWidth(120);
        expCol.setPrefWidth(180);
        usedCol.setPrefWidth(180);
        table.getColumns().add(nameCol);
        table.getColumns().add(prefixCol);
        table.getColumns().add(permsCol);
        table.getColumns().add(expCol);
        table.getColumns().add(usedCol);

        // Create form
        TextField nameField = new TextField();
        nameField.setPromptText("Key name (e.g. CI deploy)");
        ComboBox<String> permsCombo = new ComboBox<>(FXCollections.observableArrayList("read", "write", "read_write"));
        permsCombo.getSelectionModel().select("read");
        TextField expiresField = new TextField();
        expiresField.setPromptText("Expires in days (blank = never)");
        Button createBtn = new Button("Create");
        createBtn.getStyleClass().add("primary");

        Button deleteBtn = new Button("Delete selected");
        deleteBtn.getStyleClass().add("danger");
        Button closeBtn = new Button("Close");

        VBox root = new VBox(10,
                title,
                table,
                new HBox(8, deleteBtn, spacer(), status, closeBtn),
                new Label("Create new key"),
                new HBox(8, nameField, permsCombo, expiresField, createBtn)
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(14));

        Runnable load = () -> CompletableFuture.runAsync(() -> {
            try {
                List<ApiKey> keys = api.list(team.id);
                Platform.runLater(() -> table.setItems(FXCollections.observableArrayList(keys)));
            } catch (BackendException e) {
                Platform.runLater(() -> status.setText("Failed: " + e.getMessage()));
            }
        });

        createBtn.setOnAction(e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isEmpty()) {
                status.setText("Enter a name first.");
                return;
            }
            Integer days = null;
            String raw = expiresField.getText();
            if (raw != null && !raw.isBlank()) {
                try {
                    days = Integer.parseInt(raw.trim());
                } catch (NumberFormatException ex) {
                    status.setText("Expiry must be a number of days.");
                    return;
                }
            }
            createBtn.setDisable(true);
            Integer expiresInDays = days;
            CompletableFuture.runAsync(() -> {
                try {
                    ApiKey created = api.create(team.id, name, permsCombo.getValue(), expiresInDays);
                    Platform.runLater(() -> {
                        createBtn.setDisable(false);
                        nameField.clear();
                        expiresField.clear();
                        if (created != null && created.key != null) {
                            showCreatedKey(created);
                        }
                        status.setText("Created.");
                    });
                    load.run();
                } catch (BackendException ex) {
                    Platform.runLater(() -> {
                        status.setText("Create failed: " + ex.getMessage());
                        createBtn.setDisable(false);
                    });
                }
            });
        });

        deleteBtn.setOnAction(e -> {
            ApiKey selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a key.");
                return;
            }
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + selected.name + "\"? This cannot be undone.",
                    ButtonType.OK, ButtonType.CANCEL);
            a.setHeaderText("Delete API key");
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            CompletableFuture.runAsync(() -> {
                try {
                    api.delete(team.id, selected.id);
                    Platform.runLater(() -> status.setText("Deleted."));
                    load.run();
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Delete failed: " + ex.getMessage()));
                }
            });
        });

        closeBtn.setOnAction(e -> stage.close());

        load.run();

        Scene scene = new Scene(root, 820, 520);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    private static void showCreatedKey(ApiKey key) {
        Stage s = new Stage();
        s.initModality(Modality.APPLICATION_MODAL);
        s.setTitle("New API key — copy now");

        Label warn = new Label("Copy this key now. It won't be shown again.");
        warn.getStyleClass().add("subtitle");

        TextField keyField = new TextField(key.key);
        keyField.setEditable(false);
        keyField.getStyleClass().add("monospace");
        Button copy = new Button("Copy");
        copy.getStyleClass().add("primary");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(key.key);
            Clipboard.getSystemClipboard().setContent(c);
            copy.setText("Copied ✓");
        });
        Button done = new Button("Done");
        done.setOnAction(e -> s.close());

        VBox root = new VBox(12, new Label(key.name), warn, keyField, new HBox(8, copy, done));
        root.setPadding(new Insets(16));
        Scene scene = new Scene(root, 560, 200);
        Theme.apply(scene);
        s.setScene(scene);
        s.show();
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
