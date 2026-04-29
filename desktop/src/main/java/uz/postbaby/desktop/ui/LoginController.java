package uz.postbaby.desktop.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.PostBabyApp;
import uz.postbaby.desktop.auth.AuthService;
import uz.postbaby.desktop.config.AppConfig;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class LoginController {

    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);

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
        refreshEndpointLabel();
    }

    @FXML
    public void onOpenSignInPage() {
        String url = AppConfig.desktopSignInUrl();
        try {
            openBrowser(url);
            statusLabel.setText("Sign in on the web, then click \"Open in PostBaby Desktop\".");
            spinner.setVisible(true);
        } catch (Exception e) {
            spinner.setVisible(false);
            statusLabel.setText("Couldn't open the browser. Visit: " + url);
        }
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
                refreshEndpointLabel();
            }
        });
    }

    private void refreshEndpointLabel() {
        endpointLink.setText("Endpoint: " + AppConfig.apiBaseUrl());
    }

    private void openBrowser(String url) throws IOException {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            LOG.warn("Desktop.browse failed: {}", e.getMessage());
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            new ProcessBuilder("open", url).start();
        } else if (os.contains("windows")) {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
        } else {
            new ProcessBuilder("xdg-open", url).start();
        }
    }
}
