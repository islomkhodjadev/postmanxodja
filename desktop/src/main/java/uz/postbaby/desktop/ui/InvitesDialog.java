package uz.postbaby.desktop.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import uz.postbaby.desktop.api.BackendException;
import uz.postbaby.desktop.api.InviteApi;
import uz.postbaby.desktop.model.TeamInvite;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InvitesDialog {

    public static void show(InviteApi inviteApi, Runnable onChanged) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Pending invites");

        Label title = new Label("Pending invites");
        title.getStyleClass().add("title");
        Label status = new Label();
        status.getStyleClass().add("status");

        TableView<TeamInvite> table = new TableView<>();
        TableColumn<TeamInvite, String> teamCol = new TableColumn<>("Team");
        TableColumn<TeamInvite, String> inviterCol = new TableColumn<>("Inviter");
        TableColumn<TeamInvite, String> expiresCol = new TableColumn<>("Expires");
        teamCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().team == null ? "" : c.getValue().team.name));
        inviterCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().inviter == null ? "" : c.getValue().inviter.name));
        expiresCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().expires_at));
        teamCol.setPrefWidth(180);
        inviterCol.setPrefWidth(200);
        expiresCol.setPrefWidth(220);
        table.getColumns().add(teamCol);
        table.getColumns().add(inviterCol);
        table.getColumns().add(expiresCol);

        Button accept = new Button("Accept");
        accept.getStyleClass().add("primary");
        Button decline = new Button("Decline");
        Button close = new Button("Close");

        VBox root = new VBox(10,
                title,
                table,
                new HBox(8, accept, decline, spacer(), status, close)
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(14));

        Runnable load = () -> CompletableFuture.runAsync(() -> {
            try {
                List<TeamInvite> list = inviteApi.listForUser();
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList(list));
                    if (list.isEmpty()) status.setText("Inbox is empty.");
                });
            } catch (BackendException e) {
                Platform.runLater(() -> status.setText("Failed: " + e.getMessage()));
            }
        });

        accept.setOnAction(e -> {
            TeamInvite inv = table.getSelectionModel().getSelectedItem();
            if (inv == null || inv.token == null) {
                status.setText("Select an invite.");
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    inviteApi.accept(inv.token);
                    Platform.runLater(() -> {
                        status.setText("Accepted.");
                        if (onChanged != null) onChanged.run();
                    });
                    load.run();
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Accept failed: " + ex.getMessage()));
                }
            });
        });

        decline.setOnAction(e -> {
            TeamInvite inv = table.getSelectionModel().getSelectedItem();
            if (inv == null || inv.token == null) {
                status.setText("Select an invite.");
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    inviteApi.decline(inv.token);
                    Platform.runLater(() -> {
                        status.setText("Declined.");
                        if (onChanged != null) onChanged.run();
                    });
                    load.run();
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Decline failed: " + ex.getMessage()));
                }
            });
        });

        close.setOnAction(e -> stage.close());

        load.run();

        Scene scene = new Scene(root, 700, 460);
        var css = InvitesDialog.class.getResource("/css/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
