package uz.postbaby.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.api.BackendClient;
import uz.postbaby.desktop.auth.AuthService;
import uz.postbaby.desktop.store.LocalStore;
import uz.postbaby.desktop.ui.LoginController;
import uz.postbaby.desktop.ui.MainController;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

public class PostBabyApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(PostBabyApp.class);
    private static final String DEEP_LINK_SCHEME = "postbaby://";

    private LocalStore store;
    private BackendClient backend;
    private AuthService auth;
    private Stage primaryStage;

    /** Cold-start deep link captured in init(); applied after the main scene mounts. */
    private volatile String pendingDeepLink;

    @Override
    public void init() {
        for (String arg : getParameters().getRaw()) {
            if (arg != null && arg.startsWith(DEEP_LINK_SCHEME)) {
                pendingDeepLink = arg;
                break;
            }
        }
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.store = new LocalStore();
        this.backend = new BackendClient();
        this.auth = new AuthService(store, backend);

        stage.setTitle("PostBaby");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);

        URL iconUrl = PostBabyApp.class.getResource("/icons/postbaby.png");
        if (iconUrl != null) {
            try {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } catch (Exception e) {
                LOG.debug("Failed to set window icon: {}", e.getMessage());
            }
        }

        registerDeepLinkHandler();

        showMain();
        stage.show();

        if (pendingDeepLink != null) {
            String url = pendingDeepLink;
            pendingDeepLink = null;
            handleDeepLink(url);
        }
    }

    /**
     * On macOS, when the user clicks a {@code postbaby://...} link while the
     * app is already running, AppKit dispatches the URL to the JVM via this
     * handler. {@link java.awt.Desktop#setOpenURIHandler} is unsupported on
     * Linux/Windows — they need a separate single-instance + scheme story.
     */
    private void registerDeepLinkHandler() {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                d.setOpenURIHandler(event -> {
                    URI u = event.getURI();
                    if (u == null) return;
                    String url = u.toString();
                    Platform.runLater(() -> handleDeepLink(url));
                });
                LOG.info("Registered deep-link URI handler");
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            LOG.debug("Desktop URI handler not available on this platform: {}", e.getMessage());
        }
    }

    private void handleDeepLink(String url) {
        if (url == null || !url.startsWith(DEEP_LINK_SCHEME)) return;
        LOG.info("Received deep link sign-in");
        auth.signInFromCallbackInput(url).whenComplete((user, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        LOG.warn("Deep-link sign-in failed: {}", cause.getMessage());
                        return;
                    }
                    showMain();
                }));
    }

    public void showLogin() {
        try {
            FXMLLoader loader = loaderFor("/fxml/login.fxml");
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.bind(this, auth);
            setScene(root, "Sign in");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load login.fxml", e);
        }
    }

    public void showMain() {
        try {
            FXMLLoader loader = loaderFor("/fxml/main.fxml");
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.bind(this, auth, backend, store);
            setScene(root, "PostBaby");
            controller.onShown();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load main.fxml", e);
        }
    }

    private FXMLLoader loaderFor(String resource) {
        URL url = PostBabyApp.class.getResource(resource);
        if (url == null) throw new IllegalStateException("Missing resource " + resource);
        return new FXMLLoader(url);
    }

    private void setScene(Parent root, String title) {
        Scene scene = new Scene(root);
        URL css = PostBabyApp.class.getResource("/css/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        if ("light".equals(store.loadTheme())) {
            URL light = PostBabyApp.class.getResource("/css/theme-light.css");
            if (light != null) scene.getStylesheets().add(light.toExternalForm());
        }
        primaryStage.setTitle("PostBaby — " + title);
        primaryStage.setScene(scene);
    }

    @Override
    public void stop() {
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
