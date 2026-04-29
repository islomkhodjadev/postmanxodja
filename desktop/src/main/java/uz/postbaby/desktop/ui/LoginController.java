package uz.postbaby.desktop.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import uz.postbaby.desktop.PostBabyApp;
import uz.postbaby.desktop.auth.AuthService;
import uz.postbaby.desktop.config.AppConfig;

public class LoginController {

    @FXML
    private Button googleButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressIndicator spinner;
    @FXML
    private Hyperlink endpointLink;
    @FXML
    private VBox fallbackPanel;
    @FXML
    private TextArea callbackInput;
    @FXML
    private Button usePastedButton;

    private PostBabyApp app;
    private AuthService auth;
    private PauseTransition fallbackReveal;

    public void bind(PostBabyApp app, AuthService auth) {
        this.app = app;
        this.auth = auth;
        refreshEndpointLabel();
    }

    @FXML
    public void onGoogleSignIn() {
        setBusy(true, "Opening browser…");
        scheduleFallbackReveal();
        auth.signInWithGoogle().whenComplete((user, error) ->
                Platform.runLater(() -> {
                    setBusy(false, "");
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        statusLabel.setText("Sign-in failed: " + cause.getMessage());
                        // Keep fallback panel visible — they may still have a URL to paste
                        showFallbackPanel();
                        return;
                    }
                    app.showMain();
                }));
    }

    @FXML
    public void onUsePasted() {
        String text = callbackInput.getText() == null ? "" : callbackInput.getText().trim();
        if (text.isEmpty()) {
            statusLabel.setText("Paste your access token first.");
            return;
        }
        setBusy(true, "Validating token…");
        usePastedButton.setDisable(true);
        auth.signInFromCallbackInput(text).whenComplete((user, error) ->
                Platform.runLater(() -> {
                    setBusy(false, "");
                    usePastedButton.setDisable(false);
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        statusLabel.setText("Couldn't sign in: " + cause.getMessage());
                        return;
                    }
                    app.showMain();
                }));
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
                refreshEndpointLabel();
            }
        });
    }

    private void refreshEndpointLabel() {
        endpointLink.setText("Endpoint: " + AppConfig.apiBaseUrl());
    }

    private void setBusy(boolean busy, String message) {
        spinner.setVisible(busy);
        googleButton.setDisable(busy);
        statusLabel.setText(message);
    }

    /**
     * Reveal the "paste your URL" fallback after a few seconds — the loopback flow
     * is the happy path, and we don't want to confuse users with a paste box up-front.
     */
    private void scheduleFallbackReveal() {
        if (fallbackReveal != null) fallbackReveal.stop();
        fallbackReveal = new PauseTransition(Duration.seconds(8));
        fallbackReveal.setOnFinished(e -> showFallbackPanel());
        fallbackReveal.play();
    }

    private void showFallbackPanel() {
        if (fallbackPanel.isVisible()) return;
        fallbackPanel.setVisible(true);
        fallbackPanel.setManaged(true);
    }
}
