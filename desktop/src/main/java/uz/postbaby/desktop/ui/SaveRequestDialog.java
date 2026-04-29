package uz.postbaby.desktop.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import uz.postbaby.desktop.model.Collection;
import uz.postbaby.desktop.model.PostmanCollection;
import uz.postbaby.desktop.model.PostmanItem;
import uz.postbaby.desktop.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modal that asks where to save / create a request:
 *  - which Collection
 *  - which folder inside it (root or any nested folder)
 *  - request name
 */
public class SaveRequestDialog {

    public static final String ROOT_LABEL = "(root)";

    public static class Result {
        public final Collection collection;
        public final List<String> folderPath; // empty = root
        public final String name;

        public Result(Collection c, List<String> path, String n) {
            this.collection = c;
            this.folderPath = path;
            this.name = n;
        }
    }

    public static Optional<Result> show(List<Collection> collections, String defaultName, String title) {
        if (collections == null || collections.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.WARNING,
                    "Create a collection first, then add requests to it.");
            a.setHeaderText("No collections");
            a.showAndWait();
            return Optional.empty();
        }

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title == null ? "Save request" : title);

        Label titleLabel = new Label(title == null ? "Save request" : title);
        titleLabel.getStyleClass().add("title");

        Label collectionLabel = new Label("Collection");
        ComboBox<Collection> collCombo = new ComboBox<>(FXCollections.observableArrayList(collections));
        collCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Collection c) { return c == null ? "" : c.name; }
            @Override public Collection fromString(String s) { return null; }
        });
        collCombo.setMaxWidth(Double.MAX_VALUE);

        Label folderLabel = new Label("Folder");
        ComboBox<String> folderCombo = new ComboBox<>();
        folderCombo.setMaxWidth(Double.MAX_VALUE);
        // Map combo entry → list of folder names (empty list = root)
        Map<String, List<String>> folderPaths = new LinkedHashMap<>();

        Runnable refreshFolders = () -> {
            folderCombo.getItems().clear();
            folderPaths.clear();
            folderCombo.getItems().add(ROOT_LABEL);
            folderPaths.put(ROOT_LABEL, new ArrayList<>());
            Collection sel = collCombo.getValue();
            if (sel != null && sel.raw_json != null && !sel.raw_json.isBlank()) {
                try {
                    PostmanCollection pc = Json.parse(sel.raw_json, PostmanCollection.class);
                    if (pc != null && pc.item != null) collectFolders(pc.item, new ArrayList<>(), folderPaths);
                } catch (Exception ignored) {}
            }
            folderCombo.getItems().addAll(folderPaths.keySet().stream().filter(k -> !ROOT_LABEL.equals(k)).toList());
            folderCombo.getSelectionModel().select(ROOT_LABEL);
        };

        collCombo.valueProperty().addListener((obs, old, val) -> refreshFolders.run());
        collCombo.getSelectionModel().select(0);
        refreshFolders.run();

        Label nameLabel = new Label("Request name");
        TextField nameField = new TextField(defaultName == null ? "New Request" : defaultName);
        nameField.selectAll();

        Label status = new Label();
        status.getStyleClass().add("status");

        Button save = new Button("Save");
        save.getStyleClass().add("primary");
        Button cancel = new Button("Cancel");

        Result[] result = new Result[1];

        save.setOnAction(e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isEmpty()) { status.setText("Name can't be empty."); return; }
            Collection sel = collCombo.getValue();
            if (sel == null) { status.setText("Pick a collection."); return; }
            List<String> path = folderPaths.getOrDefault(folderCombo.getValue(), new ArrayList<>());
            result[0] = new Result(sel, path, name);
            stage.close();
        });
        cancel.setOnAction(e -> stage.close());

        VBox root = new VBox(10,
                titleLabel,
                collectionLabel, collCombo,
                folderLabel, folderCombo,
                nameLabel, nameField,
                new HBox(8, status, spacer(), cancel, save));
        root.setPadding(new Insets(16));

        Scene scene = new Scene(root, 460, 340);
        var css = SaveRequestDialog.class.getResource("/css/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private static void collectFolders(List<PostmanItem> items, List<String> ancestors,
                                        Map<String, List<String>> out) {
        for (PostmanItem it : items) {
            if (it == null) continue;
            if (it.isFolder()) {
                List<String> path = new ArrayList<>(ancestors);
                path.add(it.name == null ? "" : it.name);
                out.put(String.join(" / ", path), path);
                if (it.item != null && !it.item.isEmpty()) collectFolders(it.item, path, out);
            }
        }
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
