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
import uz.postbaby.desktop.ui.Theme;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

public class PostbabyApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(PostbabyApp.class);
    private static final String DEEP_LINK_SCHEME = "postbaby://";

    private static final AtomicReference<String> EARLY_DEEP_LINK = new AtomicReference<>();
    private static volatile PostbabyApp instance;

    private LocalStore store;
    private BackendClient backend;
    private AuthService auth;
    private Stage primaryStage;

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

        URL iconUrl = PostbabyApp.class.getResource("/icons/postbaby.png");
        if (iconUrl != null) {
            try {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } catch (Exception e) {
                LOG.debug("Failed to set window icon: {}", e.getMessage());
            }
        }

        instance = this;

        showMain();
        stage.show();

        Platform.runLater(this::reRegisterUriHandler);
        Platform.runLater(this::registerGlassUrlHandler);

        String early = EARLY_DEEP_LINK.getAndSet(null);
        if (early != null) handleDeepLink(early);
        if (pendingDeepLink != null) {
            String url = pendingDeepLink;
            pendingDeepLink = null;
            handleDeepLink(url);
        }
    }

    private void registerGlassUrlHandler() {
        try {
            com.sun.glass.ui.Application glassApp = com.sun.glass.ui.Application.GetApplication();
            if (glassApp == null) {
                LOG.debug("Glass Application not initialized; skipping URL handler override");
                return;
            }
            com.sun.glass.ui.Application.EventHandler existing = glassApp.getEventHandler();
            glassApp.setEventHandler(new com.sun.glass.ui.Application.EventHandler() {
                @Override
                public void handleOpenFilesAction(com.sun.glass.ui.Application app, long time, String[] files) {
                    if (files != null) {
                        for (String f : files) {
                            System.err.println("[postbaby] Glass openFiles entry: " + f);
                            if (f != null && f.startsWith(DEEP_LINK_SCHEME)) {
                                Platform.runLater(() -> handleDeepLink(f));
                            }
                        }
                    }
                    if (existing != null) existing.handleOpenFilesAction(app, time, files);
                }

                @Override
                public void handleQuitAction(com.sun.glass.ui.Application app, long time) {
                    if (existing != null) existing.handleQuitAction(app, time);
                }
            });
            System.err.println("[postbaby] Registered Glass URL handler");
            LOG.info("Registered Glass URL handler");
        } catch (Throwable t) {
            System.err.println("[postbaby] Failed to register Glass URL handler: " + t);
            LOG.warn("Failed to register Glass URL handler (deep links may not work): {}", t.toString());
        }
    }

    private void reRegisterUriHandler() {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                d.setOpenURIHandler(event -> {
                    URI u = event.getURI();
                    if (u == null) return;
                    String url = u.toString();
                    System.err.println("[postbaby] AWT received deep link (post-FX): " + url);
                    LOG.info("AWT received deep link (post-FX)");
                    Platform.runLater(() -> handleDeepLink(url));
                });
                System.err.println("[postbaby] Re-registered URI handler after JavaFX init");
                LOG.info("Re-registered URI handler after JavaFX init");
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            LOG.debug("Re-register failed: {}", e.getMessage());
        }
    }

    private void handleDeepLink(String url) {
        if (url == null || !url.startsWith(DEEP_LINK_SCHEME)) return;
        System.err.println("[postbaby] handleDeepLink: " + url);
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
        URL url = PostbabyApp.class.getResource(resource);
        if (url == null) throw new IllegalStateException("Missing resource " + resource);
        return new FXMLLoader(url);
    }

    private void setScene(Parent root, String title) {
        Theme.set(store.loadTheme());
        Scene scene = new Scene(root);
        Theme.apply(scene);
        primaryStage.setTitle("PostBaby — " + title);
        primaryStage.setScene(scene);
    }

    @Override
    public void stop() {
        Platform.exit();
    }

    public static void main(String[] args) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                d.setOpenURIHandler(event -> {
                    URI u = event.getURI();
                    if (u == null) return;
                    String url = u.toString();
                    System.err.println("[postbaby] AWT received deep link: " + url);
                    LOG.info("AWT received deep link");
                    PostbabyApp app = instance;
                    if (app != null) {
                        Platform.runLater(() -> app.handleDeepLink(url));
                    } else {
                        EARLY_DEEP_LINK.set(url);
                    }
                });
                System.err.println("[postbaby] Registered deep-link URI handler in main()");
                LOG.info("Registered deep-link URI handler in main()");
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            LOG.debug("Desktop URI handler not available: {}", e.getMessage());
        }
        launch(args);
    }
}
