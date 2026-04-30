package uz.postbaby.desktop.ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;

public class NameDialog {

    public static class Builder {
        private String title = "Name";
        private String subtitle;
        private String fieldLabel = "Name";
        private String placeholder;
        private String initial = "";
        private String okText = "Create";

        public Builder title(String t) {
            this.title = t;
            return this;
        }

        public Builder subtitle(String s) {
            this.subtitle = s;
            return this;
        }

        public Builder fieldLabel(String l) {
            this.fieldLabel = l;
            return this;
        }

        public Builder placeholder(String p) {
            this.placeholder = p;
            return this;
        }

        public Builder initial(String i) {
            this.initial = i == null ? "" : i;
            return this;
        }

        public Builder okText(String t) {
            this.okText = t;
            return this;
        }

        public Optional<String> show() {
            return NameDialog.show(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Optional<String> show(Builder b) {
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

        Label fieldLabel = new Label(b.fieldLabel);
        fieldLabel.getStyleClass().add("brand-field-label");

        TextField field = new TextField(b.initial);
        if (b.placeholder != null) field.setPromptText(b.placeholder);
        field.getStyleClass().add("brand-field");

        Label error = new Label();
        error.getStyleClass().add("brand-error");
        error.setManaged(false);
        error.setVisible(false);

        Button ok = new Button(b.okText);
        ok.getStyleClass().addAll("primary", "brand-primary");
        ok.setDefaultButton(true);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost");
        cancel.setCancelButton(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, cancel, ok);
        actions.setAlignment(Pos.CENTER_RIGHT);

        String[] result = new String[1];
        Runnable confirm = () -> {
            String v = field.getText() == null ? "" : field.getText().trim();
            if (v.isEmpty()) {
                error.setText("Name can't be empty.");
                error.setManaged(true);
                error.setVisible(true);
                field.requestFocus();
                return;
            }
            result[0] = v;
            stage.close();
        };
        ok.setOnAction((ActionEvent e) -> confirm.run());
        cancel.setOnAction(e -> stage.close());
        field.setOnAction(e -> confirm.run());
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        field.textProperty().addListener((obs, oldV, newV) -> {
            if (error.isVisible()) {
                error.setManaged(false);
                error.setVisible(false);
            }
        });

        VBox card = new VBox(10);
        card.getStyleClass().add("brand-dialog-card");
        card.setPadding(new Insets(22, 24, 20, 24));
        card.getChildren().add(titleLabel);
        if (subtitleLabel != null) card.getChildren().add(subtitleLabel);
        card.getChildren().addAll(accentBar, fieldLabel, field, error, actions);
        VBox.setMargin(accentBar, new Insets(2, 0, 6, 0));
        VBox.setMargin(actions, new Insets(8, 0, 0, 0));

        StackPane root = new StackPane(card);
        root.getStyleClass().add("brand-dialog-root");
        root.setPadding(new Insets(0));

        Scene scene = new Scene(root, 440, -1);
        var css = NameDialog.class.getResource("/css/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.setOnShown(e -> {
            field.requestFocus();
            field.selectAll();
        });
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}
