package uz.postbaby.desktop.ui;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;

public final class AboutDialog {

    private static final String VERSION = "1.1.0";

    private AboutDialog() {
    }

    public static void show(String backendUrl) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle("About PostBaby");
        stage.setResizable(false);

        Node logo = buildLogo(72);

        Label product = new Label("PostBaby");
        product.getStyleClass().add("about-product");

        Label tagline = new Label("A friendly REST client built for everyday API work.");
        tagline.getStyleClass().add("about-tagline");
        tagline.setWrapText(true);

        VBox heading = new VBox(2, product, tagline);
        heading.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(16, logo, heading);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heading, Priority.ALWAYS);

        Region accentBar = new Region();
        accentBar.getStyleClass().add("brand-accent-bar");
        accentBar.setPrefHeight(3);
        accentBar.setMaxWidth(48);

        GridPane meta = new GridPane();
        meta.setHgap(18);
        meta.setVgap(8);
        addMetaRow(meta, 0, "VERSION", VERSION);
        addMetaRow(meta, 1, "BUILT BY", "The PostBaby team");
        addMetaRow(meta, 2, "BACKEND", backendUrl == null || backendUrl.isBlank() ? "—" : backendUrl);

        Region divider = new Region();
        divider.getStyleClass().add("about-divider");
        divider.setPrefHeight(1);
        divider.setMaxWidth(Double.MAX_VALUE);

        Label credit = new Label("© " + java.time.Year.now() + " PostBaby team. Made with care.");
        credit.getStyleClass().add("about-credit");

        Button close = new Button("Close");
        close.getStyleClass().addAll("primary", "brand-primary");
        close.setDefaultButton(true);
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, credit, spacer, close);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, header, accentBar, meta, divider, actions);
        card.getStyleClass().add("brand-dialog-card");
        card.setPadding(new Insets(24, 26, 22, 26));
        VBox.setMargin(accentBar, new Insets(2, 0, 4, 0));
        VBox.setMargin(divider, new Insets(4, 0, 0, 0));

        StackPane root = new StackPane(card);
        root.getStyleClass().add("brand-dialog-root");
        root.setPadding(new Insets(0));

        Scene scene = new Scene(root, 480, -1);
        Theme.apply(scene);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.setScene(scene);
        stage.showAndWait();
    }

    private static Node buildLogo(double size) {
        InputStream in = AboutDialog.class.getResourceAsStream("/icons/postbaby.png");
        if (in != null) {
            ImageView iv = new ImageView(new Image(in, size * 2, size * 2, true, true));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            StackPane wrap = new StackPane(iv);
            wrap.setPrefSize(size, size);
            wrap.setMinSize(size, size);
            wrap.setMaxSize(size, size);
            return wrap;
        }
        // Fallback if the icon resource is missing for some reason.
        StackPane fallback = new StackPane(new Label("P") {{
            getStyleClass().add("about-logo-letter");
        }});
        fallback.getStyleClass().add("about-logo");
        fallback.setPrefSize(size, size);
        fallback.setMinSize(size, size);
        fallback.setMaxSize(size, size);
        return fallback;
    }

    private static void addMetaRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("about-row-label");
        Label v = new Label(value);
        v.getStyleClass().add("about-row-value");
        v.setWrapText(true);
        GridPane.setHalignment(l, HPos.LEFT);
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }
}
