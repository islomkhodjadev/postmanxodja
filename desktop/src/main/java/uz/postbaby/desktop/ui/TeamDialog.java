package uz.postbaby.desktop.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import uz.postbaby.desktop.api.BackendException;
import uz.postbaby.desktop.api.InviteApi;
import uz.postbaby.desktop.api.TeamApi;
import uz.postbaby.desktop.model.Team;
import uz.postbaby.desktop.model.TeamInvite;
import uz.postbaby.desktop.model.TeamMember;
import uz.postbaby.desktop.model.User;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TeamDialog {

    public static void show(Team team, User currentUser, TeamApi teamApi, InviteApi inviteApi, Runnable onChanged) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Team — " + team.name);

        Label title = new Label(team.name);
        title.getStyleClass().add("title");
        Label status = new Label();
        status.getStyleClass().add("status");

        TableView<TeamMember> members = new TableView<>();
        TableColumn<TeamMember, String> nameCol = new TableColumn<>("Name");
        TableColumn<TeamMember, String> emailCol = new TableColumn<>("Email");
        TableColumn<TeamMember, String> roleCol = new TableColumn<>("Role");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().user == null ? "" : c.getValue().user.name));
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().user == null ? "" : c.getValue().user.email));
        roleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().role));
        nameCol.setPrefWidth(160);
        emailCol.setPrefWidth(280);
        roleCol.setPrefWidth(80);
        members.getColumns().add(nameCol);
        members.getColumns().add(emailCol);
        members.getColumns().add(roleCol);

        TextField emailField = new TextField();
        emailField.setPromptText("name@example.com");
        Button sendInvite = new Button("Send invite");
        sendInvite.getStyleClass().add("primary");

        TableView<TeamInvite> pending = new TableView<>();
        TableColumn<TeamInvite, String> pendingEmail = new TableColumn<>("Email");
        TableColumn<TeamInvite, String> pendingExpiry = new TableColumn<>("Expires");
        pendingEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().invitee_email));
        pendingExpiry.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().expires_at));
        pendingEmail.setPrefWidth(280);
        pendingExpiry.setPrefWidth(220);
        pending.getColumns().add(pendingEmail);
        pending.getColumns().add(pendingExpiry);

        Button removeBtn = new Button("Remove member");
        Button leaveBtn = new Button("Leave team");
        Button deleteBtn = new Button("Delete team");
        deleteBtn.getStyleClass().add("danger");
        Button closeBtn = new Button("Close");

        VBox root = new VBox(10,
                title,
                new Label("Members"), members,
                new HBox(8, removeBtn, spacer(), leaveBtn, deleteBtn),
                new Label("Invite by email"),
                new HBox(8, emailField, sendInvite),
                new Label("Pending invites"), pending,
                new HBox(8, status, spacer(), closeBtn)
        );
        VBox.setVgrow(members, Priority.SOMETIMES);
        VBox.setVgrow(pending, Priority.SOMETIMES);
        root.setPadding(new Insets(14));

        Runnable loadMembers = () -> CompletableFuture.runAsync(() -> {
            try {
                List<TeamMember> list = teamApi.members(team.id);
                Platform.runLater(() -> members.setItems(FXCollections.observableArrayList(list)));
            } catch (BackendException e) {
                Platform.runLater(() -> status.setText("Failed to load members: " + e.getMessage()));
            }
        });
        Runnable loadInvites = () -> CompletableFuture.runAsync(() -> {
            try {
                List<TeamInvite> list = inviteApi.listForTeam(team.id);
                Platform.runLater(() -> pending.setItems(FXCollections.observableArrayList(list)));
            } catch (BackendException e) {
                Platform.runLater(() -> status.setText("Failed to load invites: " + e.getMessage()));
            }
        });

        sendInvite.setOnAction(e -> {
            String email = emailField.getText() == null ? "" : emailField.getText().trim();
            if (email.isEmpty()) return;
            sendInvite.setDisable(true);
            CompletableFuture.runAsync(() -> {
                try {
                    inviteApi.create(team.id, email);
                    Platform.runLater(() -> {
                        emailField.clear();
                        status.setText("Invite sent.");
                        sendInvite.setDisable(false);
                    });
                    loadInvites.run();
                } catch (BackendException ex) {
                    Platform.runLater(() -> {
                        status.setText("Invite failed: " + ex.getMessage());
                        sendInvite.setDisable(false);
                    });
                }
            });
        });

        removeBtn.setOnAction(e -> {
            TeamMember selected = members.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a member to remove.");
                return;
            }
            if ("owner".equals(selected.role)) {
                status.setText("Cannot remove the owner.");
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    teamApi.removeMember(team.id, selected.user_id);
                    Platform.runLater(() -> status.setText("Member removed."));
                    loadMembers.run();
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Remove failed: " + ex.getMessage()));
                }
            });
        });

        leaveBtn.setOnAction(e -> {
            if (!confirm("Leave team", "Are you sure you want to leave " + team.name + "?")) return;
            CompletableFuture.runAsync(() -> {
                try {
                    teamApi.leave(team.id);
                    Platform.runLater(() -> {
                        if (onChanged != null) onChanged.run();
                        stage.close();
                    });
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Leave failed: " + ex.getMessage()));
                }
            });
        });

        deleteBtn.setOnAction(e -> {
            if (!confirm("Delete team", "This deletes the team for everyone. Continue?")) return;
            CompletableFuture.runAsync(() -> {
                try {
                    teamApi.delete(team.id);
                    Platform.runLater(() -> {
                        if (onChanged != null) onChanged.run();
                        stage.close();
                    });
                } catch (BackendException ex) {
                    Platform.runLater(() -> status.setText("Delete failed: " + ex.getMessage()));
                }
            });
        });

        closeBtn.setOnAction(e -> stage.close());

        loadMembers.run();
        loadInvites.run();

        Scene scene = new Scene(root, 720, 600);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(title);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
