package uz.postbaby.desktop.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class PickerDialog<T> {

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String title = "Select";
        private String subtitle;
        private List<T> items = List.of();
        private T selected;
        private Function<T, String> toString = Objects::toString;

        public Builder<T> title(String t) {
            this.title = t;
            return this;
        }

        public Builder<T> subtitle(String s) {
            this.subtitle = s;
            return this;
        }

        public Builder<T> items(List<T> items) {
            this.items = items == null ? List.of() : items;
            return this;
        }

        public Builder<T> selected(T s) {
            this.selected = s;
            return this;
        }

        public Builder<T> toString(Function<T, String> f) {
            if (f != null) this.toString = f;
            return this;
        }

        public Optional<T> show() {
            return PickerDialog.show(this);
        }
    }

    private static <T> Optional<T> show(Builder<T> b) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle(b.title);
        stage.setResizable(false);

        Label titleLabel = new Label(b.title);
        titleLabel.getStyleClass().add("brand-title");

        Label subtitleLabel = null;
        if (b.subtitle != null && !b.subtitle.isBlank()) {
            subtitleLabel = new Label(b.subtitle);
            subtitleLabel.getStyleClass().add("brand-subtitle");
            subtitleLabel.setWrapText(true);
        }

        Region accentBar = new Region();
        accentBar.getStyleClass().add("brand-accent-bar");
        accentBar.setPrefHeight(3);
        accentBar.setMaxWidth(48);

        ListView<T> list = new ListView<>(FXCollections.observableArrayList(b.items));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : b.toString.apply(item));
            }
        });
        list.setPrefHeight(280);
        if (b.selected != null) {
            list.getSelectionModel().select(b.selected);
            list.scrollTo(b.selected);
        } else if (!b.items.isEmpty()) {
            list.getSelectionModel().select(0);
        }

        Button ok = new Button("Select");
        ok.getStyleClass().addAll("primary", "brand-primary");
        ok.setDefaultButton(true);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost");
        cancel.setCancelButton(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, cancel, ok);
        actions.setAlignment(Pos.CENTER_RIGHT);

        @SuppressWarnings("unchecked")
        T[] result = (T[]) new Object[1];
        Runnable confirm = () -> {
            T v = list.getSelectionModel().getSelectedItem();
            if (v == null) return;
            result[0] = v;
            stage.close();
        };
        ok.setOnAction(e -> confirm.run());
        cancel.setOnAction(e -> stage.close());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) confirm.run();
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) confirm.run();
            else if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });

        VBox card = new VBox(10);
        card.getStyleClass().add("brand-dialog-card");
        card.setPadding(new Insets(22, 24, 20, 24));
        card.getChildren().add(titleLabel);
        if (subtitleLabel != null) card.getChildren().add(subtitleLabel);
        card.getChildren().addAll(accentBar, list, actions);
        VBox.setMargin(accentBar, new Insets(2, 0, 6, 0));
        VBox.setMargin(actions, new Insets(8, 0, 0, 0));

        StackPane root = new StackPane(card);
        root.getStyleClass().add("brand-dialog-root");
        root.setPadding(new Insets(0));

        Scene scene = new Scene(root, 440, -1);
        Theme.apply(scene);

        stage.setScene(scene);
        stage.setOnShown(e -> list.requestFocus());
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}
