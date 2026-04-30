package uz.postbaby.desktop.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import uz.postbaby.desktop.PostBabyApp;
import uz.postbaby.desktop.auth.AuthService;
import uz.postbaby.desktop.config.AppConfig;

public class LoginController {

    @FXML
    private Button openSignInButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressIndicator spinner;
    @FXML
    private Hyperlink endpointLink;

    private PostBabyApp app;
    private AuthService auth;

    public void bind(PostBabyApp app, AuthService auth) {
        this.app = app;
        this.auth = auth;
//        refreshEndpointLabel();
    }

    @FXML
    public void onOpenSignInPage() {
        setBusy(true, "Opening browser… complete sign-in there.");
        auth.signInWithDesktopFlow().whenComplete((user, error) ->
                javafx.application.Platform.runLater(() -> {
                    setBusy(false, "");
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        statusLabel.setText("Sign-in failed: " + cause.getMessage());
                        return;
                    }
                    if (app != null) app.showMain();
                }));
    }

    @FXML
    public void onBack() {
        if (app != null) app.showMain();
    }

    @FXML
    public void onChangeEndpoint() {
        TextInputDialog dialog = new TextInputDialog(AppConfig.apiBaseUrl());
        dialog.setTitle("Backend endpoint");
        dialog.setHeaderText("API base URL (advanced)");
        dialog.setContentText("URL:");
        dialog.showAndWait().ifPresent(value -> {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                System.setProperty("postbaby.api.base", trimmed);
//                refreshEndpointLabel();
            }
        });
    }

//    private void refreshEndpointLabel() {
//        endpointLink.setText("Endpoint: " + AppConfig.apiBaseUrl());
//    }

    private void setBusy(boolean busy, String message) {
        spinner.setVisible(busy);
        openSignInButton.setDisable(busy);
        statusLabel.setText(message);
    }
}
