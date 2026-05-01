package uz.postbaby.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.PostbabyApp;
import uz.postbaby.desktop.api.ApiKeyApi;
import uz.postbaby.desktop.api.BackendClient;
import uz.postbaby.desktop.api.BackendException;
import uz.postbaby.desktop.api.CollectionApi;
import uz.postbaby.desktop.api.EnvironmentApi;
import uz.postbaby.desktop.api.InviteApi;
import uz.postbaby.desktop.api.RequestExecutor;
import uz.postbaby.desktop.api.TabApi;
import uz.postbaby.desktop.api.TeamApi;
import uz.postbaby.desktop.auth.AuthService;
import uz.postbaby.desktop.model.Authorization;
import uz.postbaby.desktop.model.Collection;
import uz.postbaby.desktop.model.Environment;
import uz.postbaby.desktop.model.ExecuteRequest;
import uz.postbaby.desktop.model.ExecuteResponse;
import uz.postbaby.desktop.model.PostmanBody;
import uz.postbaby.desktop.model.PostmanCollection;
import uz.postbaby.desktop.model.PostmanItem;
import uz.postbaby.desktop.model.PostmanKeyValue;
import uz.postbaby.desktop.model.PostmanRequest;
import uz.postbaby.desktop.model.SavedTab;
import uz.postbaby.desktop.model.Team;
import uz.postbaby.desktop.model.TeamInvite;
import uz.postbaby.desktop.store.LocalStore;
import uz.postbaby.desktop.util.CurlParser;
import uz.postbaby.desktop.util.Json;
import uz.postbaby.desktop.util.JsonHighlighter;
import uz.postbaby.desktop.util.JsonStyle;
import uz.postbaby.desktop.util.Variables;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainController {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);
    private static final long TABS_SYNC_DEBOUNCE_MS = 1500;
    private static final long LOCAL_TEAM_ID = 0L;
    private static final long ANON_USER_ID = 0L;

    private final ComboBox<Team> teamCombo = new ComboBox<>();
    private final ComboBox<Environment> environmentCombo = new ComboBox<>();

    @FXML
    private Button teamButton;
    @FXML
    private Button environmentButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label userLabel;
    @FXML
    private Button invitesButton;
    @FXML
    private Button signInButton;
    @FXML
    private Button signOutButton;

    @FXML
    private TreeView<TreeNodeRef> collectionTree;
    @FXML
    private TextField collectionSearchField;

    private List<Collection> currentCollections = java.util.Collections.emptyList();

    private javafx.scene.layout.HBox bodyFindBar;
    private TextField bodyFindField;
    private Label bodyFindCountLabel;
    private final List<int[]> bodyFindMatches = new java.util.ArrayList<>();
    private int bodyFindIndex = -1;

    private javafx.scene.layout.HBox responseFindBar;
    private TextField responseFindField;

    @FXML
    private TabPane openTabsPane;
    @FXML
    private Button newTabButton;

    @FXML
    private ComboBox<String> methodCombo;
    @FXML
    private TextField urlField;
    @FXML
    private Button sendButton;
    @FXML
    private Button syncRequestButton;

    @FXML
    private TabPane editorTabs;
    @FXML
    private javafx.scene.control.SplitPane editorResponseSplit;

    private javafx.scene.layout.StackPane responseCollapseButton;
    private javafx.scene.shape.SVGPath responseCollapseChevron;
    private double savedDividerPosition = 0.5;
    private boolean responseCollapsed = false;

    @FXML
    private TableView<KeyValueRow> paramsTable;
    @FXML
    private TableColumn<KeyValueRow, String> paramKeyCol;
    @FXML
    private TableColumn<KeyValueRow, String> paramValueCol;
    @FXML
    private TableColumn<KeyValueRow, String> paramRemoveCol;

    @FXML
    private TableView<KeyValueRow> headersTable;
    @FXML
    private TableColumn<KeyValueRow, String> headerKeyCol;
    @FXML
    private TableColumn<KeyValueRow, String> headerValueCol;
    @FXML
    private TableColumn<KeyValueRow, String> headerRemoveCol;

    @FXML
    private ComboBox<String> bodyTypeCombo;
    @FXML
    private VBox bodyTabRoot;

    private CodeArea bodyArea;

    @FXML
    private ComboBox<String> authTypeCombo;
    @FXML
    private VBox authNoAuthPanel;
    @FXML
    private VBox authBearerPanel;
    @FXML
    private VBox authBasicPanel;
    @FXML
    private VBox authApiKeyPanel;
    @FXML
    private TextField bearerTokenField;
    @FXML
    private TextField basicUsernameField;
    @FXML
    private PasswordField basicPasswordField;
    @FXML
    private TextField apiKeyKeyField;
    @FXML
    private TextField apiKeyValueField;
    @FXML
    private ComboBox<String> apiKeyAddToCombo;

    @FXML
    private Label responseStatusLabel;
    @FXML
    private Label responseTimeLabel;
    @FXML
    private Label responseSizeLabel;
    @FXML
    private WebView responseBodyView;
    @FXML
    private TableView<Map.Entry<String, String>> responseHeadersTable;
    @FXML
    private RadioMenuItem darkThemeItem;
    @FXML
    private RadioMenuItem lightThemeItem;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> respHeaderKeyCol;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> respHeaderValueCol;

    private PostbabyApp app;
    private AuthService auth;
    private BackendClient backend;
    private LocalStore store;

    private TeamApi teamApi;
    private CollectionApi collectionApi;
    private EnvironmentApi environmentApi;
    private TabApi tabApi;
    private InviteApi inviteApi;
    private ApiKeyApi apiKeyApi;
    private RequestExecutor executor;

    private final Map<String, Tab> tabsByState = new HashMap<>();
    private TabState currentTab;
    private boolean suppressUiSync = false;

    private boolean syncingUrlParams = false;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "postbaby-tabs-sync");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pendingTabsSync;

    public void bind(PostbabyApp app, AuthService auth, BackendClient backend, LocalStore store) {
        this.app = app;
        this.auth = auth;
        this.backend = backend;
        this.store = store;
        this.teamApi = new TeamApi(backend);
        this.collectionApi = new CollectionApi(backend);
        this.environmentApi = new EnvironmentApi(backend);
        this.tabApi = new TabApi(backend);
        this.inviteApi = new InviteApi(backend);
        this.apiKeyApi = new ApiKeyApi(backend);
        this.executor = new RequestExecutor();

        initRequestEditor();
        initTabs();
        initTree();
        initPickerButtons();
        refreshAuthIndicator();
        if ("light".equals(store.loadTheme())) {
            if (lightThemeItem != null) lightThemeItem.setSelected(true);
        } else {
            if (darkThemeItem != null) darkThemeItem.setSelected(true);
        }
    }

    public void onShown() {
        loadTeams();
        loadOpenTabs();
        if (auth.isAuthenticated()) refreshInvitesBadge();
        installFindAccelerator();
        installZoomAccelerators();
    }

    private void refreshAuthIndicator() {
        boolean signedIn = auth.isAuthenticated();
        if (signedIn) {
            String label = auth.user() == null ? ""
                    : (auth.user().email == null ? auth.user().name : auth.user().email);
            userLabel.setText(label);
            signInButton.setVisible(false);
            signInButton.setManaged(false);
            if (signOutButton != null) {
                signOutButton.setVisible(true);
                signOutButton.setManaged(true);
            }
            invitesButton.setVisible(true);
            invitesButton.setManaged(true);
        } else {
            userLabel.setText("Offline");
            signInButton.setVisible(true);
            signInButton.setManaged(true);
            if (signOutButton != null) {
                signOutButton.setVisible(false);
                signOutButton.setManaged(false);
            }
            invitesButton.setVisible(false);
            invitesButton.setManaged(false);
        }
    }

    private long effectiveUserId() {
        return auth.user() == null ? ANON_USER_ID : auth.user().id;
    }

    private static Team localTeam() {
        Team t = new Team();
        t.id = LOCAL_TEAM_ID;
        t.name = "Local";
        return t;
    }

    private void requireSignIn(String reason, Runnable action) {
        if (auth.isAuthenticated()) {
            action.run();
            return;
        }
        showToast("Sign in to " + reason + ".");
    }

    private void initRequestEditor() {
        methodCombo.setItems(FXCollections.observableArrayList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        methodCombo.getSelectionModel().select("GET");

        bodyTypeCombo.setItems(FXCollections.observableArrayList("none", "raw"));
        bodyTypeCombo.getSelectionModel().select("none");

        configureKeyValueTable(paramsTable, paramKeyCol, paramValueCol, paramRemoveCol);
        configureKeyValueTable(headersTable, headerKeyCol, headerValueCol, headerRemoveCol);

        respHeaderKeyCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        respHeaderValueCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));

        responseStatusLabel.setText("");
        responseTimeLabel.setText("");
        if (responseSizeLabel != null) responseSizeLabel.setText("");

        if (responseBodyView != null) {
            responseBodyView.setMinSize(0, 0);
            responseBodyView.setPrefSize(100, 100);
            responseBodyView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        if (editorResponseSplit != null) {
            for (javafx.scene.Node child : editorResponseSplit.getItems()) {
                if (child instanceof javafx.scene.layout.Region r) r.setMinHeight(40);
            }
            javafx.application.Platform.runLater(() -> {
                editorResponseSplit.setDividerPositions(0.5);
                forceDividerStyle(editorResponseSplit);
                setupResponseCollapseButton();
            });
        }

        initBodyEditor();
        initAuthTab();
        initFindBars();

        urlField.textProperty().addListener((obs, old, val) -> {
            if (CurlParser.looksLikeCurl(val)) {
                if (importCurl(val)) return;
            }
            syncUrlIntoParams(val);
            onTabFieldChanged();
        });
        methodCombo.valueProperty().addListener((obs, old, val) -> onTabFieldChanged());
        bodyArea.textProperty().addListener((obs, old, val) -> {
            applyJsonHighlight();
            onTabFieldChanged();
        });
        bodyTypeCombo.valueProperty().addListener((obs, old, val) -> onTabFieldChanged());
    }

    private static void forceDividerStyle(javafx.scene.control.SplitPane sp) {
        String base =
                "-fx-background-color:#3b556b;" +
                        "-fx-background-insets:0;" +
                        "-fx-padding:0;" +
                        "-fx-min-height:4;" +
                        "-fx-pref-height:4;" +
                        "-fx-max-height:4;" +
                        "-fx-cursor:v-resize;";
        String hover =
                "-fx-background-color:#ff6c37;" +
                        "-fx-background-insets:0;" +
                        "-fx-padding:0;" +
                        "-fx-min-height:4;" +
                        "-fx-pref-height:4;" +
                        "-fx-max-height:4;" +
                        "-fx-cursor:v-resize;";
        for (javafx.scene.Node n : sp.lookupAll(".split-pane-divider")) {
            if (n instanceof javafx.scene.layout.Region r) {
                r.setStyle(base);
                r.setOnMouseEntered(e -> r.setStyle(hover));
                r.setOnMouseExited(e -> r.setStyle(base));
            }
        }
    }

    private static final String COLLAPSE_PILL_BASE =
            "-fx-background-color: #1f2733;" +
                    "-fx-background-radius: 6;" +
                    "-fx-border-color: #3b556b;" +
                    "-fx-border-radius: 6;" +
                    "-fx-border-width: 1;" +
                    "-fx-cursor: hand;";
    private static final String COLLAPSE_PILL_HOVER =
            "-fx-background-color: #2a3340;" +
                    "-fx-background-radius: 6;" +
                    "-fx-border-color: #ff6c37;" +
                    "-fx-border-radius: 6;" +
                    "-fx-border-width: 1;" +
                    "-fx-cursor: hand;";
    private static final javafx.scene.paint.Color CHEVRON_COLOR = javafx.scene.paint.Color.web("#c8d4e3");
    private static final javafx.scene.paint.Color CHEVRON_HOVER = javafx.scene.paint.Color.web("#ff6c37");

    private void setupResponseCollapseButton() {
        if (editorResponseSplit == null) return;
        if (responseCollapseButton != null) return;

        responseCollapseChevron = new javafx.scene.shape.SVGPath();
        responseCollapseChevron.setContent("M2 4 L8 10 L14 4");
        responseCollapseChevron.setFill(null);
        responseCollapseChevron.setStroke(CHEVRON_COLOR);
        responseCollapseChevron.setStrokeWidth(2);
        responseCollapseChevron.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        responseCollapseChevron.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        responseCollapseButton = new javafx.scene.layout.StackPane(responseCollapseChevron);
        responseCollapseButton.setStyle(COLLAPSE_PILL_BASE);
        responseCollapseButton.setMinSize(40, 18);
        responseCollapseButton.setPrefSize(40, 18);
        responseCollapseButton.setMaxSize(40, 18);
        responseCollapseButton.setPadding(new javafx.geometry.Insets(2, 12, 2, 12));
        javafx.scene.control.Tooltip.install(responseCollapseButton,
                new javafx.scene.control.Tooltip("Collapse / expand the response panel"));

        responseCollapseButton.setOnMouseEntered(e -> {
            responseCollapseButton.setStyle(COLLAPSE_PILL_HOVER);
            responseCollapseChevron.setStroke(CHEVRON_HOVER);
        });
        responseCollapseButton.setOnMouseExited(e -> {
            responseCollapseButton.setStyle(COLLAPSE_PILL_BASE);
            responseCollapseChevron.setStroke(CHEVRON_COLOR);
        });
        responseCollapseButton.setOnMouseClicked(e -> {
            toggleResponseCollapse();
            e.consume();
        });

        Platform.runLater(this::attachCollapseButtonToDivider);
    }

    private void attachCollapseButtonToDivider() {
        if (editorResponseSplit == null || responseCollapseButton == null) return;
        javafx.scene.Node divider = editorResponseSplit.lookup(".split-pane-divider");
        if (!(divider instanceof javafx.scene.layout.StackPane sp)) {
            Platform.runLater(this::attachCollapseButtonToDivider);
            return;
        }
        if (responseCollapseButton.getParent() == sp) return;
        responseCollapseButton.setManaged(false);
        sp.getChildren().add(responseCollapseButton);

        Runnable positionPill = () -> {
            double dw = sp.getWidth();
            double bw = responseCollapseButton.getWidth();
            double dh = sp.getHeight();
            double bh = responseCollapseButton.getHeight();
            if (dw <= 0 || bw <= 0) return;
            responseCollapseButton.setLayoutX((dw - bw) / 2.0);
            responseCollapseButton.setLayoutY((dh - bh) / 2.0);
        };
        sp.widthProperty().addListener((obs, o, n) -> positionPill.run());
        sp.heightProperty().addListener((obs, o, n) -> positionPill.run());
        responseCollapseButton.widthProperty().addListener((obs, o, n) -> positionPill.run());
        responseCollapseButton.heightProperty().addListener((obs, o, n) -> positionPill.run());
        Platform.runLater(() -> {
            responseCollapseButton.autosize();
            positionPill.run();
        });
    }

    private void toggleResponseCollapse() {
        if (editorResponseSplit == null) return;
        var dividers = editorResponseSplit.getDividers();
        if (dividers.isEmpty()) return;
        if (responseCollapsed) {
            editorResponseSplit.setDividerPositions(savedDividerPosition);
            responseCollapsed = false;
        } else {
            savedDividerPosition = dividers.get(0).getPosition();
            if (savedDividerPosition >= 0.98) savedDividerPosition = 0.5;
            editorResponseSplit.setDividerPositions(1.0);
            responseCollapsed = true;
        }
        if (responseCollapseChevron != null) {
            responseCollapseChevron.setRotate(responseCollapsed ? 180 : 0);
        }
    }

    private void initBodyEditor() {
        bodyArea = new CodeArea();
        bodyArea.setParagraphGraphicFactory(LineNumberFactory.get(bodyArea));
        bodyArea.getStyleClass().add("json-editor");
        bodyArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(bodyArea);
        scroll.getStyleClass().add("json-editor-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        bodyTabRoot.getChildren().add(scroll);
    }



    private void initFindBars() {

        bodyFindCountLabel = new Label("");
        bodyFindCountLabel.getStyleClass().add("status");
        bodyFindBar = buildFindBar(bodyFindCountLabel,
                this::onBodyFindPrev, this::onBodyFindNext, this::hideBodyFindBar);
        bodyFindField = (TextField) bodyFindBar.getProperties().get("findField");
        hideBar(bodyFindBar);
        if (bodyTabRoot != null) bodyTabRoot.getChildren().add(0, bodyFindBar);
        bodyFindField.textProperty().addListener((obs, old, val) -> recomputeBodyMatches(val));


        responseFindBar = buildFindBar(null,
                this::onResponseFindPrev, this::onResponseFindNext, this::hideResponseFindBar);
        responseFindField = (TextField) responseFindBar.getProperties().get("findField");
        hideBar(responseFindBar);
        Platform.runLater(() -> {
            if (responseBodyView != null
                    && responseBodyView.getParent() instanceof javafx.scene.layout.VBox vbox
                    && !vbox.getChildren().contains(responseFindBar)) {
                vbox.getChildren().add(0, responseFindBar);
            }
        });
        responseFindField.textProperty().addListener((obs, old, val) -> {

            findInWebView(val, false, true);
        });
    }

    private javafx.scene.layout.HBox buildFindBar(Label countLabel,
                                                  Runnable onPrev, Runnable onNext, Runnable onClose) {
        TextField field = new TextField();
        field.setPromptText("Find…");
        javafx.scene.layout.HBox.setHgrow(field, Priority.ALWAYS);

        Button prev = new Button("▲");
        prev.getStyleClass().add("ghost");
        prev.setFocusTraversable(false);
        prev.setOnAction(e -> onPrev.run());

        Button next = new Button("▼");
        next.getStyleClass().add("ghost");
        next.setFocusTraversable(false);
        next.setOnAction(e -> onNext.run());

        Button close = new Button("✕");
        close.getStyleClass().add("ghost");
        close.setFocusTraversable(false);
        close.setOnAction(e -> onClose.run());

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(6);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.setPadding(new javafx.geometry.Insets(4, 6, 4, 6));
        bar.getStyleClass().add("find-bar");
        bar.getChildren().add(field);
        if (countLabel != null) bar.getChildren().add(countLabel);
        bar.getChildren().addAll(prev, next, close);
        bar.getProperties().put("findField", field);

        field.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                onClose.run();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (e.isShiftDown()) onPrev.run();
                else onNext.run();
                e.consume();
            }
        });
        return bar;
    }

    private static void hideBar(javafx.scene.layout.HBox bar) {
        bar.setVisible(false);
        bar.setManaged(false);
    }

    private static void showBar(javafx.scene.layout.HBox bar) {
        bar.setVisible(true);
        bar.setManaged(true);
    }



    private double uiZoom = 1.0;
    private static final double UI_ZOOM_MIN = 0.6;
    private static final double UI_ZOOM_MAX = 2.5;
    private static final double UI_ZOOM_STEP = 0.1;
    private static final double UI_BASE_FONT = 13.0;

    private void installZoomAccelerators() {
        if (collectionTree == null || collectionTree.getScene() == null) return;
        javafx.scene.Scene scene = collectionTree.getScene();
        var accel = scene.getAccelerators();

        accel.putIfAbsent(javafx.scene.input.KeyCombination.keyCombination("Shortcut+EQUALS"),
                () -> setUiZoom(uiZoom + UI_ZOOM_STEP));
        accel.putIfAbsent(javafx.scene.input.KeyCombination.keyCombination("Shortcut+PLUS"),
                () -> setUiZoom(uiZoom + UI_ZOOM_STEP));
        accel.putIfAbsent(javafx.scene.input.KeyCombination.keyCombination("Shortcut+MINUS"),
                () -> setUiZoom(uiZoom - UI_ZOOM_STEP));
        accel.putIfAbsent(javafx.scene.input.KeyCombination.keyCombination("Shortcut+DIGIT0"),
                () -> setUiZoom(1.0));
    }

    private void setUiZoom(double zoom) {
        uiZoom = Math.max(UI_ZOOM_MIN, Math.min(UI_ZOOM_MAX, zoom));
        javafx.scene.Scene scene = collectionTree == null ? null : collectionTree.getScene();
        if (scene != null && scene.getRoot() != null) {
            applyFontSize(scene.getRoot(), UI_BASE_FONT * uiZoom);
        }
        if (bodyArea != null) {
            applyFontSize(bodyArea, UI_BASE_FONT * uiZoom);
        }
        if (responseBodyView != null) {
            responseBodyView.setZoom(uiZoom);
        }
        setStatus(String.format("Zoom %d%%", Math.round(uiZoom * 100)));
    }


    private static void applyFontSize(javafx.scene.Node node, double sizePx) {
        String style = node.getStyle();
        if (style == null) style = "";
        style = style.replaceAll("(?i)-fx-font-size\\s*:\\s*[^;]*;?\\s*", "");
        if (!style.isEmpty() && !style.endsWith(";")) style += ";";
        style += "-fx-font-size: " + sizePx + "px;";
        node.setStyle(style);
    }



    private void installFindAccelerator() {
        if (collectionTree == null || collectionTree.getScene() == null) return;
        javafx.scene.Scene scene = collectionTree.getScene();
        javafx.scene.input.KeyCombination combo =
                javafx.scene.input.KeyCombination.keyCombination("Shortcut+F");

        if (!scene.getAccelerators().containsKey(combo)) {
            scene.getAccelerators().put(combo, this::onFindShortcut);
        }
    }

    private void onFindShortcut() {
        javafx.scene.Scene scene = collectionTree == null ? null : collectionTree.getScene();
        if (scene == null) return;
        javafx.scene.Node owner = scene.getFocusOwner();
        if (isAncestor(bodyArea, owner) || isAncestor(bodyTabRoot, owner)) {
            showBodyFindBar();
        } else if (isAncestor(responseBodyView, owner)) {
            showResponseFindBar();
        } else {
            if (collectionSearchField != null) {
                collectionSearchField.requestFocus();
                collectionSearchField.selectAll();
            }
        }
    }

    private static boolean isAncestor(javafx.scene.Node maybeAncestor, javafx.scene.Node node) {
        if (maybeAncestor == null) return false;
        javafx.scene.Node n = node;
        while (n != null) {
            if (n == maybeAncestor) return true;
            n = n.getParent();
        }
        return false;
    }



    private void showBodyFindBar() {
        if (bodyFindBar == null) return;
        showBar(bodyFindBar);
        bodyFindField.requestFocus();
        bodyFindField.selectAll();
        recomputeBodyMatches(bodyFindField.getText());
    }

    private void hideBodyFindBar() {
        if (bodyFindBar == null) return;
        hideBar(bodyFindBar);
        if (bodyArea != null) bodyArea.requestFocus();
    }

    private void recomputeBodyMatches(String query) {
        bodyFindMatches.clear();
        bodyFindIndex = -1;
        if (bodyArea == null || query == null || query.isEmpty()) {
            updateBodyFindCount();
            return;
        }
        String text = bodyArea.getText();
        if (text == null || text.isEmpty()) {
            updateBodyFindCount();
            return;
        }
        String haystack = text.toLowerCase();
        String needle = query.toLowerCase();
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            bodyFindMatches.add(new int[]{from, from + needle.length()});
            from += Math.max(1, needle.length());
        }
        if (!bodyFindMatches.isEmpty()) {
            bodyFindIndex = 0;
            applyBodyFindSelection();
        }
        updateBodyFindCount();
    }

    private void applyBodyFindSelection() {
        if (bodyArea == null || bodyFindIndex < 0 || bodyFindIndex >= bodyFindMatches.size()) return;
        int[] r = bodyFindMatches.get(bodyFindIndex);
        bodyArea.selectRange(r[0], r[1]);
        bodyArea.requestFollowCaret();
        updateBodyFindCount();
    }

    private void updateBodyFindCount() {
        if (bodyFindCountLabel == null) return;
        if (bodyFindMatches.isEmpty()) {
            bodyFindCountLabel.setText(bodyFindField != null && !bodyFindField.getText().isEmpty()
                    ? "0/0" : "");
        } else {
            bodyFindCountLabel.setText((bodyFindIndex + 1) + "/" + bodyFindMatches.size());
        }
    }

    private void onBodyFindNext() {
        if (bodyFindMatches.isEmpty()) return;
        bodyFindIndex = (bodyFindIndex + 1) % bodyFindMatches.size();
        applyBodyFindSelection();
    }

    private void onBodyFindPrev() {
        if (bodyFindMatches.isEmpty()) return;
        bodyFindIndex = (bodyFindIndex - 1 + bodyFindMatches.size()) % bodyFindMatches.size();
        applyBodyFindSelection();
    }



    private void showResponseFindBar() {
        if (responseFindBar == null) return;

        if (responseBodyView != null
                && responseBodyView.getParent() instanceof javafx.scene.layout.VBox vbox
                && !vbox.getChildren().contains(responseFindBar)) {
            vbox.getChildren().add(0, responseFindBar);
        }
        showBar(responseFindBar);
        responseFindField.requestFocus();
        responseFindField.selectAll();
        if (!responseFindField.getText().isEmpty()) {
            findInWebView(responseFindField.getText(), false, true);
        }
    }

    private void hideResponseFindBar() {
        if (responseFindBar == null) return;
        hideBar(responseFindBar);
        if (responseBodyView != null) {
            try {
                responseBodyView.getEngine().executeScript(
                        "if(window.getSelection)window.getSelection().removeAllRanges();");
            } catch (Exception ignored) {
            }
            responseBodyView.requestFocus();
        }
    }

    private void onResponseFindNext() {
        findInWebView(responseFindField.getText(), false, false);
    }

    private void onResponseFindPrev() {
        findInWebView(responseFindField.getText(), true, false);
    }


    private void findInWebView(String query, boolean backwards, boolean fromStart) {
        if (responseBodyView == null || query == null || query.isEmpty()) return;
        String escaped = query.replace("\\", "\\\\").replace("'", "\\'");
        try {
            if (fromStart) {
                responseBodyView.getEngine().executeScript(
                        "if(window.getSelection)window.getSelection().removeAllRanges();");
            }
            String script = String.format(
                    "window.find && window.find('%s', false, %s, true, false, false, false)",
                    escaped, backwards ? "true" : "false");
            responseBodyView.getEngine().executeScript(script);
        } catch (Exception ignored) {
        }
    }


    private boolean importCurl(String raw) {
        CurlParser.Parsed c = CurlParser.parse(raw);
        if (c == null || c.url == null || c.url.isBlank()) return false;


        boolean prevSuppress = suppressUiSync;
        boolean prevSyncing = syncingUrlParams;
        suppressUiSync = true;
        syncingUrlParams = true;
        try {
            methodCombo.getSelectionModel().select(c.method == null ? "GET" : c.method);


            ObservableList<KeyValueRow> headers = headersTable.getItems();
            headers.removeIf(r -> !r.isAuto() && !r.isBlank());
            int insertAt = 0;
            for (KeyValueRow row : headers) {
                if (row.isAuto()) break;
                insertAt++;
            }
            if (!c.headers.isEmpty()) headers.addAll(Math.min(insertAt, headers.size()), c.headers);
            ensureBlankRow(headers);


            if (c.body != null && !c.body.isEmpty()) {
                bodyTypeCombo.getSelectionModel().select("raw");
                bodyArea.replaceText(c.body);
            } else {
                bodyTypeCombo.getSelectionModel().select("none");
                bodyArea.replaceText("");
            }


            if (c.auth != null) {
                loadAuthIntoUi(c.auth);
            }



            urlField.setText(c.url);
            syncingUrlParams = false;
            syncUrlIntoParams(c.url);

            syncAuthIntoHeaders();
        } finally {
            syncingUrlParams = prevSyncing;
            suppressUiSync = prevSuppress;
        }

        if (currentTab != null) {
            flushUiToTab(currentTab);
            setTabTitle(tabsByState.get(currentTab.tabId), currentTab);
            scheduleTabsSync();
        }
        setStatus("Imported cURL command — " + c.method + " " + c.url);
        return true;
    }


    private void applyJsonHighlight() {
        if (bodyArea == null) return;
        String text = bodyArea.getText();
        if (text == null) text = "";
        try {
            bodyArea.setStyleSpans(0, JsonStyle.compute(text));
        } catch (Exception ignored) {

        }
    }



    private static final String AUTH_NOAUTH = "No Auth";
    private static final String AUTH_BEARER = "Bearer Token";
    private static final String AUTH_BASIC = "Basic Auth";
    private static final String AUTH_APIKEY = "API Key";

    private void initAuthTab() {
        authTypeCombo.setItems(FXCollections.observableArrayList(
                AUTH_NOAUTH, AUTH_BEARER, AUTH_BASIC, AUTH_APIKEY));
        apiKeyAddToCombo.setItems(FXCollections.observableArrayList("Header", "Query Params"));
        apiKeyAddToCombo.getSelectionModel().select("Header");

        authTypeCombo.valueProperty().addListener((obs, old, val) -> {
            updateAuthPanelVisibility(val);
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });


        bearerTokenField.textProperty().addListener((obs, old, val) -> {
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });
        basicUsernameField.textProperty().addListener((obs, old, val) -> {
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });
        basicPasswordField.textProperty().addListener((obs, old, val) -> {
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });
        apiKeyKeyField.textProperty().addListener((obs, old, val) -> {
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });
        apiKeyValueField.textProperty().addListener((obs, old, val) -> {
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });
        apiKeyAddToCombo.valueProperty().addListener((obs, old, val) -> {
            syncAuthIntoHeaders();
            onTabFieldChanged();
        });

        authTypeCombo.getSelectionModel().select(AUTH_NOAUTH);
        updateAuthPanelVisibility(AUTH_NOAUTH);
    }

    private void updateAuthPanelVisibility(String label) {
        boolean noauth = AUTH_NOAUTH.equals(label) || label == null;
        boolean bearer = AUTH_BEARER.equals(label);
        boolean basic = AUTH_BASIC.equals(label);
        boolean apikey = AUTH_APIKEY.equals(label);
        toggle(authNoAuthPanel, noauth);
        toggle(authBearerPanel, bearer);
        toggle(authBasicPanel, basic);
        toggle(authApiKeyPanel, apikey);
    }

    private static void toggle(VBox panel, boolean visible) {
        panel.setVisible(visible);
        panel.setManaged(visible);
    }

    private static String authLabelFromType(String type) {
        if (type == null) return AUTH_NOAUTH;
        return switch (type) {
            case "bearer" -> AUTH_BEARER;
            case "basic" -> AUTH_BASIC;
            case "apikey" -> AUTH_APIKEY;
            default -> AUTH_NOAUTH;
        };
    }

    private static String authTypeFromLabel(String label) {
        if (label == null) return "noauth";
        return switch (label) {
            case AUTH_BEARER -> "bearer";
            case AUTH_BASIC -> "basic";
            case AUTH_APIKEY -> "apikey";
            default -> "noauth";
        };
    }

    private void loadAuthIntoUi(Authorization a) {
        if (a == null) a = Authorization.noauth();
        authTypeCombo.getSelectionModel().select(authLabelFromType(a.type));
        bearerTokenField.setText(a.bearer != null && a.bearer.token != null ? a.bearer.token : "");
        basicUsernameField.setText(a.basic != null && a.basic.username != null ? a.basic.username : "");
        basicPasswordField.setText(a.basic != null && a.basic.password != null ? a.basic.password : "");
        apiKeyKeyField.setText(a.apikey != null && a.apikey.key != null ? a.apikey.key : "");
        apiKeyValueField.setText(a.apikey != null && a.apikey.value != null ? a.apikey.value : "");
        apiKeyAddToCombo.getSelectionModel().select(
                a.apikey != null && "query".equals(a.apikey.addTo) ? "Query Params" : "Header");
        updateAuthPanelVisibility(authLabelFromType(a.type));
    }

    private Authorization readAuthFromUi() {
        Authorization a = new Authorization();
        a.type = authTypeFromLabel(authTypeCombo.getValue());
        switch (a.type) {
            case "bearer" -> {
                a.bearer = new Authorization.Bearer();
                a.bearer.token = bearerTokenField.getText();
            }
            case "basic" -> {
                a.basic = new Authorization.Basic();
                a.basic.username = basicUsernameField.getText();
                a.basic.password = basicPasswordField.getText();
            }
            case "apikey" -> {
                a.apikey = new Authorization.ApiKey();
                a.apikey.key = apiKeyKeyField.getText();
                a.apikey.value = apiKeyValueField.getText();
                a.apikey.addTo = "Query Params".equals(apiKeyAddToCombo.getValue()) ? "query" : "header";
            }
            default -> {
            }
        }
        return a;
    }

    private void syncUrlIntoParams(String url) {
        if (syncingUrlParams) return;
        syncingUrlParams = true;
        try {
            List<KeyValueRow> parsed = parseQueryFromUrl(url);
            ObservableList<KeyValueRow> items = paramsTable.getItems();

            items.removeIf(r -> !r.isAuto() && !r.isBlank());

            int insertAt = 0;
            for (KeyValueRow row : items) {
                if (row.isAuto()) break;
                insertAt++;
            }
            items.addAll(Math.min(insertAt, items.size()), parsed);
            ensureBlankRow(items);
        } finally {
            syncingUrlParams = false;
        }
    }

    private void syncParamsIntoUrl() {
        if (syncingUrlParams) return;
        syncingUrlParams = true;
        try {
            String base = stripQueryString(urlField.getText());
            String built = appendQuery(base, paramsTable.getItems());
            urlField.setText(built);
        } finally {
            syncingUrlParams = false;
        }
    }

    private static String stripQueryString(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        if (q < 0) return url;
        int hash = url.indexOf('#', q);
        if (hash < 0) return url.substring(0, q);
        return url.substring(0, q) + url.substring(hash);
    }

    private static String appendQuery(String urlBase, List<KeyValueRow> rows) {
        if (urlBase == null) urlBase = "";

        String fragment = "";
        int hash = urlBase.indexOf('#');
        if (hash >= 0) {
            fragment = urlBase.substring(hash);
            urlBase = urlBase.substring(0, hash);
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (KeyValueRow r : rows) {
            if (r == null || r.isBlank() || r.getKey() == null || r.getKey().isBlank()) continue;
            if (r.isAuto()) continue;
            sb.append(first ? '?' : '&');
            first = false;
            sb.append(safeEncode(r.getKey()));
            sb.append('=');
            sb.append(safeEncode(r.getValue() == null ? "" : r.getValue()));
        }
        return urlBase + sb.toString() + fragment;
    }

    private static List<KeyValueRow> parseQueryFromUrl(String url) {
        List<KeyValueRow> out = new ArrayList<>();
        if (url == null) return out;
        int q = url.indexOf('?');
        if (q < 0) return out;
        String rest = url.substring(q + 1);
        int hash = rest.indexOf('#');
        if (hash >= 0) rest = rest.substring(0, hash);
        if (rest.isEmpty()) return out;
        for (String part : rest.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : part.substring(eq + 1);
            out.add(new KeyValueRow(safeDecode(k), safeDecode(v)));
        }
        return out;
    }


    private static String safeDecode(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains("{{") || s.contains("}}")) return s;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }


    private static String safeEncode(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains("{{") || s.contains("}}")) return s;
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }




    private void syncAuthIntoHeaders() {
        if (paramsTable.getItems() == null || headersTable.getItems() == null) return;

        Iterator<KeyValueRow> hi = headersTable.getItems().iterator();
        while (hi.hasNext()) if (hi.next().isAuto()) hi.remove();
        Iterator<KeyValueRow> pi = paramsTable.getItems().iterator();
        while (pi.hasNext()) if (pi.next().isAuto()) pi.remove();

        Authorization a = readAuthFromUi();
        KeyValueRow row = null;
        boolean toQuery = false;
        if (a != null && a.type != null) {
            switch (a.type) {
                case "bearer" -> {
                    if (a.bearer != null && a.bearer.token != null && !a.bearer.token.isBlank()) {
                        row = new KeyValueRow("Authorization", "Bearer " + a.bearer.token);
                    }
                }
                case "basic" -> {
                    if (a.basic != null && a.basic.username != null && !a.basic.username.isBlank()) {


                        String u = a.basic.username == null ? "" : a.basic.username;
                        String p = a.basic.password == null ? "" : a.basic.password;
                        if (u.contains("{{") || p.contains("{{")) {
                            row = new KeyValueRow("Authorization", "Basic <computed>");
                        } else {
                            String encoded = Base64.getEncoder()
                                    .encodeToString((u + ":" + p).getBytes(StandardCharsets.UTF_8));
                            row = new KeyValueRow("Authorization", "Basic " + encoded);
                        }
                    }
                }
                case "apikey" -> {
                    if (a.apikey != null && a.apikey.key != null && !a.apikey.key.isBlank()) {
                        row = new KeyValueRow(a.apikey.key, a.apikey.value == null ? "" : a.apikey.value);
                        toQuery = "query".equals(a.apikey.addTo);
                    }
                }
                default -> {
                }
            }
        }
        if (row != null) {
            row.setAuto(true);
            ObservableList<KeyValueRow> target = toQuery ? paramsTable.getItems() : headersTable.getItems();

            int idx = target.size();
            for (int i = target.size() - 1; i >= 0; i--) {
                if (target.get(i).isBlank()) idx = i;
                else break;
            }
            target.add(idx, row);
        }
        ensureBlankRow(headersTable.getItems());
        ensureBlankRow(paramsTable.getItems());
    }


    private void applyAuthorization(ExecuteRequest req, Authorization a, Map<String, String> vars) {
        if (a == null || a.type == null) return;
        switch (a.type) {
            case "bearer" -> {
                if (a.bearer != null && a.bearer.token != null && !a.bearer.token.isBlank()) {
                    String token = Variables.replace(a.bearer.token, vars);
                    req.headers.put("Authorization", "Bearer " + token);
                }
            }
            case "basic" -> {
                if (a.basic != null && a.basic.username != null && !a.basic.username.isBlank()) {
                    String u = Variables.replace(a.basic.username, vars);
                    String p = Variables.replace(a.basic.password == null ? "" : a.basic.password, vars);
                    String encoded = Base64.getEncoder()
                            .encodeToString((u + ":" + p).getBytes(StandardCharsets.UTF_8));
                    req.headers.put("Authorization", "Basic " + encoded);
                }
            }
            case "apikey" -> {
                if (a.apikey != null && a.apikey.key != null && !a.apikey.key.isBlank()) {
                    String k = Variables.replace(a.apikey.key, vars);
                    String v = Variables.replace(a.apikey.value == null ? "" : a.apikey.value, vars);
                    if ("query".equals(a.apikey.addTo)) {
                        req.query_params.put(k, v);
                    } else {
                        req.headers.put(k, v);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void configureKeyValueTable(TableView<KeyValueRow> table,
                                        TableColumn<KeyValueRow, String> keyCol,
                                        TableColumn<KeyValueRow, String> valCol,
                                        TableColumn<KeyValueRow, String> removeCol) {
        table.setEditable(true);
        keyCol.setCellValueFactory(new PropertyValueFactory<>("key"));
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        keyCol.setCellFactory(c -> new EditingCell());
        valCol.setCellFactory(c -> new EditingCell());
        keyCol.setOnEditCommit(e -> {
            e.getRowValue().setKey(e.getNewValue());
            ensureBlankRow(table.getItems());
            if (table == paramsTable) syncParamsIntoUrl();
            onTabFieldChanged();
        });
        valCol.setOnEditCommit(e -> {
            e.getRowValue().setValue(e.getNewValue());
            ensureBlankRow(table.getItems());
            if (table == paramsTable) syncParamsIntoUrl();
            onTabFieldChanged();
        });
        removeCol.setCellValueFactory(c -> new SimpleStringProperty(""));
        removeCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");

            {
                btn.getStyleClass().add("ghost");
                btn.setOnAction(e -> {
                    KeyValueRow row = getTableView().getItems().get(getIndex());
                    getTableView().getItems().remove(row);
                    ensureBlankRow(getTableView().getItems());
                    if (getTableView() == paramsTable) syncParamsIntoUrl();
                    onTabFieldChanged();
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private void ensureBlankRow(ObservableList<KeyValueRow> rows) {
        if (rows.isEmpty() || !rows.get(rows.size() - 1).isBlank()) {
            rows.add(new KeyValueRow());
        }
    }



    private void initTabs() {
        openTabsPane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                currentTab = null;
                return;
            }
            if (oldT != null) {
                Object data = oldT.getUserData();
                if (data instanceof TabState s) flushUiToTab(s);
            }
            Object data = newT.getUserData();
            if (data instanceof TabState s) {
                currentTab = s;
                loadTabIntoUi(s);
                if (auth.user() != null) store.saveActiveTabId(s.tabId);
            }
        });
    }


    private void loadOpenTabs() {
        long uid = effectiveUserId();
        List<SavedTab> cached = store.loadTabs(uid);
        applyTabsFromWire(cached, store.loadActiveTabId());

        if (!auth.isAuthenticated()) return;

        runAsync(() -> {
            try {
                List<SavedTab> remote = tabApi.list();
                store.saveTabs(uid, remote);
                Platform.runLater(() -> {
                    if (openTabsPane.getTabs().isEmpty() || cachedDifferent(cached, remote)) {
                        applyTabsFromWire(remote, findActive(remote));
                    }
                });
            } catch (BackendException e) {
                LOG.debug("Skipping /tabs fetch: {}", e.getMessage());
            }
        });
    }

    private static boolean cachedDifferent(List<SavedTab> a, List<SavedTab> b) {
        if (a.size() != b.size()) return true;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).tab_id, b.get(i).tab_id)) return true;
        }
        return false;
    }

    private static String findActive(List<SavedTab> tabs) {
        for (SavedTab t : tabs) if (t.is_active) return t.tab_id;
        return tabs.isEmpty() ? null : tabs.get(0).tab_id;
    }

    private void applyTabsFromWire(List<SavedTab> wire, String activeId) {
        suppressUiSync = true;
        openTabsPane.getTabs().clear();
        tabsByState.clear();
        for (SavedTab w : wire) {
            TabState s = TabState.fromWire(w);
            s.dirty = false;
            addTabFor(s, false);
        }
        if (openTabsPane.getTabs().isEmpty()) {

            createBlankTab();
        } else {
            Tab toSelect = null;
            if (activeId != null) {
                for (Tab t : openTabsPane.getTabs()) {
                    if (t.getUserData() instanceof TabState s && activeId.equals(s.tabId)) {
                        toSelect = t;
                        break;
                    }
                }
            }
            openTabsPane.getSelectionModel().select(toSelect != null ? toSelect : openTabsPane.getTabs().get(0));
        }
        suppressUiSync = false;
    }

    private Tab addTabFor(TabState state, boolean select) {
        Tab tab = new Tab();
        tab.setUserData(state);
        Label titleLabel = new Label(displayTitle(state));
        titleLabel.getStyleClass().add("open-tab-title");
        titleLabel.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) startInlineRename(tab, titleLabel, state);
        });
        tab.setGraphic(titleLabel);
        tab.setOnClosed(ev -> onTabClosed(state));
        tabsByState.put(state.tabId, tab);
        openTabsPane.getTabs().add(tab);
        if (select) openTabsPane.getSelectionModel().select(tab);
        return tab;
    }


    private void startInlineRename(Tab tab, Label label, TabState state) {
        TextField field = new TextField(state.name == null ? "" : state.name);
        field.getStyleClass().add("open-tab-rename");
        field.setPrefWidth(Math.max(label.getWidth() + 30, 140));
        Runnable commit = () -> {
            String typed = field.getText() == null ? "" : field.getText().trim();
            if (!typed.isEmpty()) {
                state.name = typed;
                if (state.itemPath != null) state.itemPath = typed;
            }
            label.setText(displayTitle(state));
            tab.setGraphic(label);
            scheduleTabsSync();
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, was, isNow) -> {
            if (was && !isNow) commit.run();
        });
        field.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tab.setGraphic(label);
                ev.consume();
            }
        });
        tab.setGraphic(field);
        field.requestFocus();
        field.selectAll();
    }

    private static void setTabTitle(Tab tab, TabState state) {
        if (tab == null) return;
        if (tab.getGraphic() instanceof Label l) {
            l.setText(displayTitle(state));
        } else {
            tab.setText(displayTitle(state));
        }
    }

    private void onTabClosed(TabState state) {
        tabsByState.remove(state.tabId);
        if (openTabsPane.getTabs().isEmpty()) {
            createBlankTab();
        }
        scheduleTabsSync();
    }

    private static String displayTitle(TabState s) {
        if (s.name != null && !s.name.isBlank() && !"Untitled".equals(s.name)) return s.name;
        if (s.url != null && !s.url.isBlank()) {
            String shown = s.url.replaceFirst("^https?://", "");
            return shown.length() > 32 ? shown.substring(0, 30) + "…" : shown;
        }
        return "Untitled";
    }

    private void createBlankTab() {
        TabState s = new TabState();
        ensureBlankRow(s.params);
        ensureBlankRow(s.headers);
        addTabFor(s, true);
    }

    @FXML
    public void onNewBlankTab() {
        createBlankTab();
        scheduleTabsSync();
    }

    private void onTabFieldChanged() {
        if (suppressUiSync || currentTab == null) return;

        flushUiToTab(currentTab);
        setTabTitle(tabsByState.get(currentTab.tabId), currentTab);
        scheduleTabsSync();
    }

    private void flushUiToTab(TabState s) {
        s.method = methodCombo.getValue() == null ? "GET" : methodCombo.getValue();
        s.url = urlField.getText() == null ? "" : urlField.getText();
        s.bodyType = bodyTypeCombo.getValue() == null ? "none" : bodyTypeCombo.getValue();
        s.body = bodyArea.getText() == null ? "" : bodyArea.getText();
        s.params.setAll(new ArrayList<>(paramsTable.getItems()));
        s.headers.setAll(new ArrayList<>(headersTable.getItems()));
        s.authorization = readAuthFromUi();
        s.responseStatus = parseIntOrNull(responseStatusLabel.getText());
        s.responseStatusText = responseStatusLabel.getText();
        s.responseTimeMs = parseLongOrNull(responseTimeLabel.getText());


        s.responseHeaders.setAll(responseHeadersTable.getItems());
        s.dirty = true;
    }

    private void loadTabIntoUi(TabState s) {
        suppressUiSync = true;
        try {
            methodCombo.getSelectionModel().select(s.method == null ? "GET" : s.method);
            urlField.setText(s.url == null ? "" : s.url);
            bodyTypeCombo.getSelectionModel().select(s.bodyType == null ? "none" : s.bodyType);
            bodyArea.replaceText(s.body == null ? "" : s.body);


            s.params.removeIf(KeyValueRow::isAuto);
            s.headers.removeIf(KeyValueRow::isAuto);
            ensureBlankRow(s.params);
            ensureBlankRow(s.headers);
            paramsTable.setItems(s.params);
            headersTable.setItems(s.headers);

            loadAuthIntoUi(s.authorization);
            syncAuthIntoHeaders();

            if (s.responseStatus != null) {
                responseStatusLabel.setText(s.responseStatusText == null
                        ? String.valueOf(s.responseStatus) : s.responseStatusText);
                String tier = s.responseStatus / 100 + "xx";
                responseStatusLabel.getStyleClass().setAll("status-pill", "status-" + tier);
            } else {
                responseStatusLabel.setText("");
                responseStatusLabel.getStyleClass().setAll("status-pill");
            }
            responseTimeLabel.setText(s.responseTimeMs == null ? "" : s.responseTimeMs + " ms");
            responseSizeLabel.setText(s.responseSizeBytes == null ? "" : formatBytes(s.responseSizeBytes));
            renderResponseBody(s.responseBody);
            responseHeadersTable.setItems(s.responseHeaders);
        } finally {
            suppressUiSync = false;
        }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String head = s.split(" ")[0];
            return Integer.parseInt(head);
        } catch (Exception e) {
            return null;
        }
    }


    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.2f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }


    private void scheduleTabsSync() {
        if (pendingTabsSync != null) pendingTabsSync.cancel(false);
        pendingTabsSync = scheduler.schedule(() -> Platform.runLater(this::commitTabsSync),
                TABS_SYNC_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void commitTabsSync() {
        long uid = effectiveUserId();
        List<SavedTab> wire = new ArrayList<>();
        Tab selected = openTabsPane.getSelectionModel().getSelectedItem();
        String activeId = selected != null && selected.getUserData() instanceof TabState s ? s.tabId : null;
        int i = 0;
        for (Tab t : openTabsPane.getTabs()) {
            if (!(t.getUserData() instanceof TabState s)) continue;
            wire.add(s.toWire(i++, s.tabId.equals(activeId)));
        }
        store.saveTabs(uid, wire);
        if (!auth.isAuthenticated()) return;
        runAsync(() -> {
            try {
                tabApi.save(wire, activeId);
                LOG.debug("Synced {} tabs to backend", wire.size());
            } catch (BackendException e) {
                LOG.debug("Tabs sync deferred: {}", e.getMessage());
            }
        });
    }



    private void initPickerButtons() {
        teamButton.setText("Select team…");
        environmentButton.setText("(no environment)");
        teamCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) ->
                teamButton.setText(val == null ? "Select team…" : val.name));
        environmentCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) ->
                environmentButton.setText(val == null ? "(no environment)" : val.name));
    }

    @FXML
    public void onChooseTeam() {
        List<Team> items = teamCombo.getItems();
        if (items == null || items.isEmpty()) return;
        PickerDialog.<Team>builder()
                .title("Select team")
                .subtitle("Pick the workspace to load.")
                .items(items)
                .selected(teamCombo.getValue())
                .toString(t -> t == null ? "" : t.name)
                .show()
                .ifPresent(t -> teamCombo.getSelectionModel().select(t));
    }

    @FXML
    public void onChooseEnvironment() {
        List<Environment> items = environmentCombo.getItems();
        if (items == null || items.isEmpty()) return;
        PickerDialog.<Environment>builder()
                .title("Select environment")
                .subtitle("Variables from this environment will be substituted into requests.")
                .items(items)
                .selected(environmentCombo.getValue())
                .toString(e -> e == null ? "" : e.name)
                .show()
                .ifPresent(e -> environmentCombo.getSelectionModel().select(e));
    }

    private void loadTeams() {

        List<Team> initial = new ArrayList<>();
        initial.add(localTeam());
        for (Team t : store.loadTeams()) {
            if (t.id != LOCAL_TEAM_ID) initial.add(t);
        }
        applyTeams(initial);

        if (!auth.isAuthenticated()) return;

        runAsync(() -> {
            try {
                List<Team> fresh = teamApi.list();
                store.saveTeams(fresh);
                List<Team> merged = new ArrayList<>();
                merged.add(localTeam());
                merged.addAll(fresh);
                Platform.runLater(() -> applyTeams(merged));
            } catch (BackendException e) {
                Platform.runLater(() -> {
                    if (e.isUnauthorized()) {
                        auth.signOut();
                        refreshAuthIndicator();
                        showToast("Session expired — sign in to sync.");
                    } else if (e.isNetwork()) {
                        setStatus("Offline — Local workspace only");
                    } else {
                        setStatus("Failed to load teams: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void applyTeams(List<Team> teams) {
        teamCombo.setItems(FXCollections.observableArrayList(teams));
        teamCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Team t) {
                return t == null ? "" : t.name;
            }

            @Override
            public Team fromString(String s) {
                return null;
            }
        });

        if (teams.isEmpty()) {
            setStatus("No teams available.");
            return;
        }
        Long active = store.loadActiveTeamId();
        Team selected = teams.stream().filter(t -> active != null && Objects.equals(t.id, active))
                .findFirst().orElse(teams.get(0));
        teamCombo.getSelectionModel().select(selected);
        teamCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> onTeamSelected(val));
        onTeamSelected(selected);
    }

    private void onTeamSelected(Team team) {
        if (team == null) return;
        store.saveActiveTeamId(team.id);
        loadEnvironments(team.id);
        loadCollections(team.id);
    }

    private void loadEnvironments(long teamId) {
        List<Environment> cached = store.loadEnvironments(teamId);
        applyEnvironments(cached);
        if (teamId == LOCAL_TEAM_ID || !auth.isAuthenticated()) return;
        runAsync(() -> {
            try {
                List<Environment> fresh = environmentApi.list(teamId);
                store.saveEnvironments(teamId, fresh);
                Platform.runLater(() -> applyEnvironments(fresh));
            } catch (BackendException e) {
                LOG.debug("env load failed: {}", e.getMessage());
            }
        });
    }

    private void applyEnvironments(List<Environment> envs) {
        List<Environment> withNone = new ArrayList<>();
        Environment none = new Environment();
        none.id = null;
        none.name = "(no environment)";
        withNone.add(none);
        withNone.addAll(envs);

        environmentCombo.setItems(FXCollections.observableArrayList(withNone));
        environmentCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Environment e) {
                return e == null ? "" : e.name;
            }

            @Override
            public Environment fromString(String s) {
                return null;
            }
        });

        Long active = store.loadActiveEnvironmentId();
        Environment selected = withNone.stream()
                .filter(e -> Objects.equals(e.id, active)).findFirst().orElse(withNone.get(0));
        environmentCombo.getSelectionModel().select(selected);
        environmentCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) store.saveActiveEnvironmentId(val.id);
        });
    }



    private void initTree() {
        TreeItem<TreeNodeRef> root = new TreeItem<>();
        root.setExpanded(true);
        collectionTree.setRoot(root);
        collectionTree.setShowRoot(false);
        collectionTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(TreeNodeRef ref, boolean empty) {
                super.updateItem(ref, empty);
                if (empty || ref == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }
                switch (ref.kind) {
                    case COLLECTION -> {
                        setGraphic(buildTreeIcon(true));
                        setText(ref.toString());
                    }
                    case FOLDER -> {
                        setGraphic(buildTreeIcon(false));
                        setText(ref.toString());
                    }
                    case REQUEST -> {
                        setGraphic(null);
                        setText(methodLabel(ref.item) + " " + ref.toString());
                    }
                }
                setContextMenu(buildContextMenu(ref));
            }
        });
        collectionTree.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null && val.getValue() != null && val.getValue().kind == TreeNodeRef.Kind.REQUEST) {
                openOrFocusRequestTab(val.getValue());
            }
        });

        if (collectionSearchField != null) {
            collectionSearchField.textProperty().addListener((obs, old, val) -> rebuildCollectionTree(val));
        }
    }


    private static javafx.scene.Node buildTreeIcon(boolean collection) {
        javafx.scene.shape.SVGPath p = new javafx.scene.shape.SVGPath();

        p.setContent("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z");
        if (collection) {
            p.setFill(javafx.scene.paint.Color.web("#ff6c37"));
            p.setStroke(javafx.scene.paint.Color.web("#ff6c37"));
        } else {
            p.setFill(javafx.scene.paint.Color.web("#5b6470"));
            p.setStroke(javafx.scene.paint.Color.web("#7b8290"));
        }
        p.setStrokeWidth(0.5);

        p.setScaleX(0.62);
        p.setScaleY(0.62);


        javafx.scene.layout.StackPane wrap = new javafx.scene.layout.StackPane(p);
        wrap.setMinSize(16, 16);
        wrap.setPrefSize(16, 16);
        wrap.setMaxSize(16, 16);
        return wrap;
    }

    private ContextMenu buildContextMenu(TreeNodeRef ref) {
        ContextMenu menu = new ContextMenu();
        switch (ref.kind) {
            case COLLECTION -> {
                MenuItem addReq = new MenuItem("Add request");
                addReq.setOnAction(e -> onAddRequestUnder(ref));
                MenuItem addFolder = new MenuItem("Add folder");
                addFolder.setOnAction(e -> onAddFolderUnder(ref));
                MenuItem rename = new MenuItem("Rename");
                rename.setOnAction(e -> onRenameNode(ref));
                MenuItem export = new MenuItem("Export…");
                export.setOnAction(e -> exportCollectionToFile(ref.collection));
                MenuItem delete = new MenuItem("Delete collection");
                delete.setOnAction(e -> onDeleteNode(ref));
                menu.getItems().addAll(addReq, addFolder, new SeparatorMenuItem(), rename, export, delete);
            }
            case FOLDER -> {
                MenuItem addReq = new MenuItem("Add request");
                addReq.setOnAction(e -> onAddRequestUnder(ref));
                MenuItem addFolder = new MenuItem("Add folder");
                addFolder.setOnAction(e -> onAddFolderUnder(ref));
                MenuItem rename = new MenuItem("Rename");
                rename.setOnAction(e -> onRenameNode(ref));
                MenuItem delete = new MenuItem("Delete folder");
                delete.setOnAction(e -> onDeleteNode(ref));
                menu.getItems().addAll(addReq, addFolder, new SeparatorMenuItem(), rename, delete);
            }
            case REQUEST -> {
                MenuItem open = new MenuItem("Open in tab");
                open.setOnAction(e -> openOrFocusRequestTab(ref));
                MenuItem sync = new MenuItem("Sync from backend");
                sync.setOnAction(e -> syncRequestNode(ref));
                MenuItem rename = new MenuItem("Rename");
                rename.setOnAction(e -> onRenameNode(ref));
                MenuItem delete = new MenuItem("Delete");
                delete.setOnAction(e -> onDeleteNode(ref));
                menu.getItems().addAll(open, sync, new SeparatorMenuItem(), rename, delete);
            }
        }
        return menu;
    }

    private static String methodLabel(PostmanItem item) {
        if (item == null || item.request == null || item.request.method == null) return "•";
        return item.request.method.toUpperCase();
    }

    private void openOrFocusRequestTab(TreeNodeRef ref) {

        for (Tab t : openTabsPane.getTabs()) {
            if (t.getUserData() instanceof TabState s
                    && Objects.equals(s.collectionId, ref.collection.id)
                    && Objects.equals(s.itemPath, pathOf(ref))) {
                openTabsPane.getSelectionModel().select(t);
                return;
            }
        }

        TabState s = new TabState();
        s.name = ref.item.name == null ? "Untitled" : ref.item.name;
        s.collectionId = ref.collection.id;
        s.itemPath = pathOf(ref);

        PostmanRequest r = ref.item.request;
        if (r != null) {
            s.method = r.method == null ? "GET" : r.method.toUpperCase();
            s.url = extractUrl(r.url);
            if (r.header != null) {
                for (PostmanKeyValue h : r.header) {
                    if (Boolean.TRUE.equals(h.disabled)) continue;
                    s.headers.add(new KeyValueRow(h.key, h.stringValue()));
                }
            }
            if (r.url instanceof Map<?, ?> m) {
                Object q = m.get("query");
                if (q instanceof List<?> list) {
                    for (Object kv : list) {
                        if (kv instanceof Map<?, ?> kvm) {
                            Object kRaw = kvm.get("key");
                            Object vRaw = kvm.get("value");
                            String k = kRaw == null ? "" : String.valueOf(kRaw);
                            String v = vRaw == null ? "" : String.valueOf(vRaw);
                            s.params.add(new KeyValueRow(k, v));
                        }
                    }
                }
            }
            if (r.body != null && "raw".equals(r.body.mode)) {
                s.bodyType = "raw";
                s.body = r.body.raw == null ? "" : r.body.raw;
            }
        }
        ensureBlankRow(s.params);
        ensureBlankRow(s.headers);
        addTabFor(s, true);
        scheduleTabsSync();
    }

    private static String pathOf(TreeNodeRef ref) {
        return ref.item == null ? "" : ref.item.name == null ? "" : ref.item.name;
    }

    private void loadCollections(long teamId) {
        List<Collection> cached = store.loadCollections(teamId);
        applyCollections(cached);
        if (teamId == LOCAL_TEAM_ID || !auth.isAuthenticated()) return;
        runAsync(() -> {
            try {
                List<Collection> fresh = collectionApi.list(teamId);
                for (Collection c : fresh) {
                    if (c.raw_json == null || c.raw_json.isBlank()) {
                        try {
                            Collection full = collectionApi.get(teamId, c.id);
                            c.raw_json = full.raw_json;
                        } catch (Exception ignored) {
                        }
                    }
                }
                store.saveCollections(teamId, fresh);
                Platform.runLater(() -> applyCollections(fresh));
            } catch (BackendException e) {
                Platform.runLater(() -> setStatus(
                        e.isNetwork() ? "Offline — using cached collections" : "Failed to load collections: " + e.getMessage()));
            }
        });
    }

    private void applyCollections(List<Collection> collections) {
        this.currentCollections = collections == null ? java.util.Collections.emptyList() : collections;
        rebuildCollectionTree(collectionSearchField == null ? "" : collectionSearchField.getText());
    }


    private void rebuildCollectionTree(String query) {
        if (collectionTree == null) return;
        TreeItem<TreeNodeRef> root = collectionTree.getRoot();
        if (root == null) return;
        root.getChildren().clear();

        String q = query == null ? "" : query.trim().toLowerCase();
        boolean filtering = !q.isEmpty();

        for (Collection c : currentCollections) {
            PostmanCollection pc = parsePostman(c.raw_json);
            TreeItem<TreeNodeRef> cItem = new TreeItem<>(TreeNodeRef.collection(c));
            if (pc != null && pc.item != null) {
                for (PostmanItem item : pc.item) {
                    TreeItem<TreeNodeRef> child = buildItemNode(c, item, null, q);
                    if (child != null) cItem.getChildren().add(child);
                }
            }

            boolean nameMatches = !filtering
                    || (c.name != null && c.name.toLowerCase().contains(q));
            boolean hasMatchingChildren = !cItem.getChildren().isEmpty();

            if (!filtering) {
                cItem.setExpanded(true);
                root.getChildren().add(cItem);
            } else if (nameMatches || hasMatchingChildren) {
                cItem.setExpanded(hasMatchingChildren);
                root.getChildren().add(cItem);
            }
        }
    }


    private TreeItem<TreeNodeRef> buildItemNode(Collection c, PostmanItem item, PostmanItem parent, String q) {
        boolean filtering = !q.isEmpty();
        if (item.isFolder()) {
            TreeItem<TreeNodeRef> folder = new TreeItem<>(TreeNodeRef.folder(c, item, parent));
            if (item.item != null) {
                for (PostmanItem child : item.item) {
                    TreeItem<TreeNodeRef> sub = buildItemNode(c, child, item, q);
                    if (sub != null) folder.getChildren().add(sub);
                }
            }
            boolean nameMatches = !filtering
                    || (item.name != null && item.name.toLowerCase().contains(q));
            boolean hasMatchingChildren = !folder.getChildren().isEmpty();
            if (!filtering) {
                folder.setExpanded(false);
                return folder;
            }
            if (nameMatches || hasMatchingChildren) {
                folder.setExpanded(hasMatchingChildren);
                return folder;
            }
            return null;
        }
        if (!filtering || requestMatchesQuery(item, q)) {
            return new TreeItem<>(TreeNodeRef.request(c, item, parent));
        }
        return null;
    }

    private static boolean requestMatchesQuery(PostmanItem item, String q) {
        if (item.name != null && item.name.toLowerCase().contains(q)) return true;
        PostmanRequest req = item.request;
        if (req == null) return false;
        String url = extractUrl(req.url);
        if (url != null && url.toLowerCase().contains(q)) return true;
        PostmanBody body = req.body;
        if (body != null) {
            if (body.raw != null && body.raw.toLowerCase().contains(q)) return true;
            if (body.formdata != null) {
                for (PostmanKeyValue kv : body.formdata) {
                    if (kv.key != null && kv.key.toLowerCase().contains(q)) return true;
                    String v = kv.stringValue();
                    if (!v.isEmpty() && v.toLowerCase().contains(q)) return true;
                }
            }
            if (body.urlencoded != null) {
                for (PostmanKeyValue kv : body.urlencoded) {
                    if (kv.key != null && kv.key.toLowerCase().contains(q)) return true;
                    String v = kv.stringValue();
                    if (!v.isEmpty() && v.toLowerCase().contains(q)) return true;
                }
            }
        }
        return false;
    }

    private static String extractUrl(Object url) {
        if (url == null) return "";
        if (url instanceof String s) return s;
        if (url instanceof Map<?, ?> m) {
            Object raw = m.get("raw");
            if (raw != null) return String.valueOf(raw);
        }
        return "";
    }



    @FXML
    public void onSend() {
        if (currentTab != null) flushUiToTab(currentTab);
        sendButton.setDisable(true);
        setStatus("Sending…");

        ExecuteRequest req = buildRequest();
        long started = System.currentTimeMillis();

        runAsync(() -> {
            ExecuteResponse resp = executor.execute(req);
            long elapsed = System.currentTimeMillis() - started;
            Platform.runLater(() -> {
                sendButton.setDisable(false);
                applyResponse(resp, elapsed);
            });
        });
    }

    private ExecuteRequest buildRequest() {
        ExecuteRequest req = new ExecuteRequest();
        req.method = methodCombo.getValue() == null ? "GET" : methodCombo.getValue();

        Map<String, String> vars = currentEnvironmentVariables();
        req.url = Variables.replace(urlField.getText() == null ? "" : urlField.getText().trim(), vars);

        for (KeyValueRow row : headersTable.getItems()) {
            if (row.isBlank() || row.getKey() == null || row.getKey().isBlank()) continue;
            req.headers.put(row.getKey().trim(),
                    Variables.replace(row.getValue() == null ? "" : row.getValue(), vars));
        }
        for (KeyValueRow row : paramsTable.getItems()) {
            if (row.isBlank() || row.getKey() == null || row.getKey().isBlank()) continue;
            req.query_params.put(row.getKey().trim(),
                    Variables.replace(row.getValue() == null ? "" : row.getValue(), vars));
        }
        if ("raw".equals(bodyTypeCombo.getValue()) && bodyArea.getText() != null) {
            req.body = Variables.replace(bodyArea.getText(), vars);
        } else {
            req.body = "";
        }
        applyAuthorization(req, readAuthFromUi(), vars);
        return req;
    }

    private Map<String, String> currentEnvironmentVariables() {
        Environment env = environmentCombo.getValue();
        if (env == null || env.variables == null) return Collections.emptyMap();
        return env.variables;
    }

    private void applyResponse(ExecuteResponse resp, long elapsedMs) {
        if (resp.status == 0) {
            responseStatusLabel.setText("ERROR");
            responseStatusLabel.getStyleClass().setAll("status-pill", "error");
        } else {
            String phrase = resp.status_text == null ? "" : resp.status_text.trim();
            String text = phrase.isEmpty() ? String.valueOf(resp.status)
                    : resp.status + " " + phrase;
            responseStatusLabel.setText(text);
            String tier = resp.status / 100 + "xx";
            responseStatusLabel.getStyleClass().setAll("status-pill", "status-" + tier);
        }
        responseTimeLabel.setText(elapsedMs + " ms");
        responseSizeLabel.setText(formatBytes(resp.size));
        String pretty = prettifyJsonIfPossible(resp.body);
        renderResponseBody(pretty);
        if (currentTab != null) {
            currentTab.responseHeaders.setAll(new ArrayList<>(resp.headers.entrySet()));
            currentTab.responseStatus = resp.status;
            currentTab.responseStatusText = responseStatusLabel.getText();
            currentTab.responseTimeMs = elapsedMs;
            currentTab.responseSizeBytes = resp.size;
            currentTab.responseBody = pretty;
        }
        responseHeadersTable.setItems(currentTab != null ? currentTab.responseHeaders
                : FXCollections.observableArrayList(new ArrayList<>(resp.headers.entrySet())));
        setStatus("Done");
    }


    private void renderResponseBody(String body) {
        if (responseBodyView == null) return;
        JsonHighlighter.Theme theme = "light".equals(store.loadTheme())
                ? JsonHighlighter.Theme.LIGHT
                : JsonHighlighter.Theme.DARK;
        String html = JsonHighlighter.toHtml(body == null ? "" : body, theme);
        responseBodyView.getEngine().loadContent(html, "text/html");
    }

    private static String prettifyJsonIfPossible(String body) {
        if (body == null || body.isEmpty()) return "";
        String trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return body;
        try {
            ObjectMapper m = Json.MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT);
            Object node = m.readValue(trimmed, Object.class);
            return m.writeValueAsString(node);
        } catch (Exception e) {
            return body;
        }
    }

    @FXML
    public void onPrettyBody() {
        bodyArea.replaceText(prettifyJsonIfPossible(bodyArea.getText()));
    }

    @FXML
    public void onCopyResponse() {
        ClipboardContent c = new ClipboardContent();
        String text = currentTab != null && currentTab.responseBody != null ? currentTab.responseBody : "";
        c.putString(text);
        Clipboard.getSystemClipboard().setContent(c);
    }




    private void syncRequestNode(TreeNodeRef ref) {
        if (ref == null || ref.kind != TreeNodeRef.Kind.REQUEST) return;
        openOrFocusRequestTab(ref);
        onSyncRequest();
    }


    @FXML
    public void onSyncRequest() {
        if (currentTab == null) {
            setStatus("Open a tab first.");
            return;
        }
        if (currentTab.collectionId == null) {
            setStatus("This tab isn't linked to a collection — nothing to sync.");
            return;
        }
        Team team = teamCombo.getValue();
        if (team == null) return;
        if (team.id == LOCAL_TEAM_ID) {
            setStatus("Local workspace stays on this device — nothing to sync.");
            return;
        }
        if (!auth.isAuthenticated()) {
            showToast("Sign in to sync from the backend.");
            return;
        }

        long collectionId = currentTab.collectionId;
        String itemPath = currentTab.itemPath;
        TabState target = currentTab;
        setStatus("Syncing request…");
        syncRequestButton.setDisable(true);

        runAsync(() -> {
            try {
                Collection fresh = collectionApi.get(team.id, collectionId);

                List<Collection> all = store.loadCollections(team.id);
                boolean replaced = false;
                for (int i = 0; i < all.size(); i++) {
                    if (Objects.equals(all.get(i).id, fresh.id)) {
                        all.set(i, fresh);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) all.add(fresh);
                store.saveCollections(team.id, all);

                PostmanCollection pc = parsePostman(fresh.raw_json);
                PostmanItem item = pc == null ? null : findItemByPath(pc.item, itemPath);

                Platform.runLater(() -> {
                    syncRequestButton.setDisable(false);
                    applyCollections(store.loadCollections(team.id));
                    if (item == null) {
                        setStatus("Request \"" + itemPath + "\" no longer exists in the collection.");
                        return;
                    }
                    if (target == currentTab) {
                        replaceTabFromItem(target, item);
                        showToast("Request synced.");
                    }
                });
            } catch (BackendException e) {
                Platform.runLater(() -> {
                    syncRequestButton.setDisable(false);
                    showToast(e.isNetwork() ? "Offline — can't sync right now."
                            : "Sync failed: " + e.getMessage());
                });
            }
        });
    }


    private void replaceTabFromItem(TabState s, PostmanItem item) {
        s.headers.clear();
        s.params.clear();
        s.body = "";
        s.bodyType = "none";

        PostmanRequest r = item.request;
        if (r != null) {
            s.method = r.method == null ? "GET" : r.method.toUpperCase();
            s.url = extractUrl(r.url);
            if (r.header != null) {
                for (PostmanKeyValue h : r.header) {
                    if (Boolean.TRUE.equals(h.disabled)) continue;
                    s.headers.add(new KeyValueRow(h.key, h.stringValue()));
                }
            }
            if (r.url instanceof Map<?, ?> m) {
                Object q = m.get("query");
                if (q instanceof List<?> list) {
                    for (Object kv : list) {
                        if (kv instanceof Map<?, ?> kvm) {
                            Object kRaw = kvm.get("key");
                            Object vRaw = kvm.get("value");
                            String k = kRaw == null ? "" : String.valueOf(kRaw);
                            String v = vRaw == null ? "" : String.valueOf(vRaw);
                            s.params.add(new KeyValueRow(k, v));
                        }
                    }
                }
            }
            if (r.body != null && "raw".equals(r.body.mode)) {
                s.bodyType = "raw";
                s.body = r.body.raw == null ? "" : r.body.raw;
            }
        }
        ensureBlankRow(s.params);
        ensureBlankRow(s.headers);

        if (s == currentTab) loadTabIntoUi(s);
    }

    @FXML
    public void onSaveRequest() {
        if (currentTab == null) {
            setStatus("Open a tab first.");
            return;
        }
        Team team = teamCombo.getValue();
        if (team == null) {
            setStatus("Select a team first.");
            return;
        }
        flushUiToTab(currentTab);

        if (currentTab.collectionId != null) {
            saveExistingRequest(team, currentTab);
        } else {
            saveAsNewRequest(team, currentTab);
        }
    }


    private void saveExistingRequest(Team team, TabState tab) {
        List<Collection> all = store.loadCollections(team.id);
        Collection target = null;
        for (Collection c : all) {
            if (Objects.equals(c.id, tab.collectionId)) {
                target = c;
                break;
            }
        }
        if (target == null) {
            setStatus("Source collection not found locally — try refreshing.");
            return;
        }
        PostmanCollection pc = parsePostman(target.raw_json);
        if (pc == null) {
            setStatus("Source collection is not parseable.");
            return;
        }
        PostmanItem item = findItemByPath(pc.item, tab.itemPath);
        if (item == null) {

            saveAsNewRequest(team, tab);
            return;
        }
        applyTabToItem(tab, item);
        target.raw_json = Json.stringify(pc);
        target.dirty = true;
        persistCollectionLocal(target);
        pushCollectionIfOnline(team, target);
    }


    private void saveAsNewRequest(Team team, TabState tab) {
        List<Collection> all = store.loadCollections(team.id);
        String defaultName = deriveRequestName(tab);
        Optional<SaveRequestDialog.Result> picked =
                SaveRequestDialog.show(all, defaultName, "Save request to collection");
        if (picked.isEmpty()) return;
        SaveRequestDialog.Result r = picked.get();
        createRequestInCollection(team, r.collection, r.folderPath, r.name, tab, false);
    }

    private static String deriveRequestName(TabState s) {
        if (s.name != null && !s.name.isBlank() && !"Untitled".equals(s.name)) return s.name;
        if (s.url != null && !s.url.isBlank()) {
            String stripped = s.url.replaceFirst("^https?://", "");
            int q = stripped.indexOf('?');
            return (s.method == null ? "GET" : s.method) + " "
                    + (q < 0 ? stripped : stripped.substring(0, q));
        }
        return "New Request";
    }


    private void createRequestInCollection(Team team, Collection collection, List<String> folderPath,
                                           String name, TabState sourceTab, boolean openAsNewTab) {
        PostmanCollection pc = parsePostman(collection.raw_json);
        if (pc == null) {
            pc = new PostmanCollection();
            pc.info.name = collection.name == null ? "Untitled" : collection.name;
        }
        List<PostmanItem> targetList = pc.item;
        if (folderPath != null && !folderPath.isEmpty()) {
            PostmanItem folder = findFolder(pc.item, folderPath, 0);
            if (folder != null) targetList = folder.item;
        }
        PostmanItem item = new PostmanItem();
        item.name = name;
        item.request = new PostmanRequest();
        if (sourceTab != null) {
            applyTabToItem(sourceTab, item);
        } else {
            item.request.method = "GET";
            item.request.url = "";
        }
        targetList.add(item);

        collection.raw_json = Json.stringify(pc);
        collection.dirty = true;
        persistCollectionLocal(collection);

        TabState target = openAsNewTab ? new TabState() : sourceTab;
        if (target != null) {
            target.collectionId = collection.id;
            target.itemPath = name;
            target.name = name;
            if (openAsNewTab) {
                if (item.request != null) {
                    target.method = item.request.method == null ? "GET" : item.request.method;
                    target.url = item.request.url == null ? "" : String.valueOf(item.request.url);
                }
                ensureBlankRow(target.params);
                ensureBlankRow(target.headers);
                addTabFor(target, true);
                scheduleTabsSync();
            } else {
                setTabTitle(tabsByState.get(target.tabId), target);
            }
        }

        applyCollections(store.loadCollections(team.id));
        pushCollectionIfOnline(team, collection);
    }

    private static PostmanItem findFolder(List<PostmanItem> items, List<String> path, int depth) {
        if (items == null || depth >= path.size()) return null;
        String want = path.get(depth);
        for (PostmanItem it : items) {
            if (!it.isFolder()) continue;
            if (Objects.equals(it.name, want)) {
                if (depth == path.size() - 1) return it;
                PostmanItem nested = findFolder(it.item, path, depth + 1);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private void pushCollectionIfOnline(Team team, Collection collection) {
        if (team.id == LOCAL_TEAM_ID || !auth.isAuthenticated()
                || collection.id == null || collection.id <= 0) {
            showToast("Saved locally.");
            return;
        }
        setStatus("Saving…");
        runAsync(() -> {
            try {
                Collection updated = collectionApi.update(team.id, collection.id, null, collection.raw_json);
                collection.raw_json = updated.raw_json;
                collection.dirty = false;
                persistCollectionLocal(collection);
                Platform.runLater(() -> showToast("Saved & synced."));
            } catch (BackendException e) {
                Platform.runLater(() -> showToast(
                        e.isNetwork() ? "Saved locally. Will sync when online."
                                : "Saved locally. Sync failed: " + e.getMessage()));
            }
        });
    }

    private static PostmanItem findItemByPath(List<PostmanItem> items, String path) {
        if (items == null || path == null) return null;
        for (PostmanItem item : items) {
            if (path.equals(item.name)) return item;
            if (item.item != null && !item.item.isEmpty()) {
                PostmanItem nested = findItemByPath(item.item, path);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private void applyTabToItem(TabState s, PostmanItem item) {
        if (item.request == null) item.request = new PostmanRequest();
        item.request.method = s.method;
        item.request.url = s.url;
        item.request.header = new ArrayList<>();
        for (KeyValueRow row : s.headers) {
            if (row.isBlank() || row.getKey() == null || row.getKey().isBlank()) continue;
            if (row.isAuto()) continue;
            item.request.header.add(new PostmanKeyValue(row.getKey(), row.getValue()));
        }
        if ("raw".equals(s.bodyType)) {
            PostmanBody body = new PostmanBody();
            body.mode = "raw";
            body.raw = s.body;
            item.request.body = body;
        } else {
            item.request.body = null;
        }
    }

    private void persistCollectionLocal(Collection c) {
        Team team = teamCombo.getValue();
        if (team == null) return;
        List<Collection> all = store.loadCollections(team.id);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (Objects.equals(all.get(i).id, c.id)) {
                all.set(i, c);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(c);
        store.saveCollections(team.id, all);
    }



    @FXML
    public void onNewCollection() {
        Team team = teamCombo.getValue();
        if (team == null) {
            setStatus("Select a team first.");
            return;
        }
        NameDialog.builder()
                .title("New collection")
                .subtitle("Group related requests, environments, and tests under one collection.")
                .fieldLabel("Collection name")
                .placeholder("e.g. Billing API")
                .initial("New Collection")
                .okText("Create collection")
                .show()
                .ifPresent(name -> {
                    if (name.isBlank()) return;


                    if (team.id == LOCAL_TEAM_ID || !auth.isAuthenticated()) {
                        Collection created = new Collection();
                        created.id = -System.currentTimeMillis();
                        created.team_id = team.id;
                        created.name = name;
                        created.localOnly = true;
                        PostmanCollection pc = new PostmanCollection();
                        pc.info.name = name;
                        created.raw_json = Json.stringify(pc);
                        persistCollectionLocal(created);
                        applyCollections(store.loadCollections(team.id));
                        setStatus("Collection created locally.");
                        return;
                    }

                    runAsync(() -> {
                        try {
                            Collection created = collectionApi.create(team.id, name, "");
                            if (created.raw_json == null || created.raw_json.isBlank()) {
                                PostmanCollection pc = new PostmanCollection();
                                pc.info.name = name;
                                created.raw_json = Json.stringify(pc);
                            }
                            persistCollectionLocal(created);
                            Platform.runLater(() -> {
                                loadCollections(team.id);
                                setStatus("Collection created.");
                            });
                        } catch (BackendException e) {
                            Platform.runLater(() -> setStatus(
                                    e.isNetwork() ? "Offline — collection creation requires online." : "Failed: " + e.getMessage()));
                        }
                    });
                });
    }

    @FXML
    public void onRefreshCollections() {
        Team team = teamCombo.getValue();
        if (team != null) loadCollections(team.id);
    }

    @FXML
    public void onImportCollection() {
        Team team = teamCombo.getValue();
        if (team == null) {
            setStatus("Select a team first.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Postman collection");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Postman collection (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File chosen = fc.showOpenDialog(ownerWindow());
        if (chosen == null) return;

        String raw;
        try {
            raw = Files.readString(chosen.toPath());
        } catch (IOException ex) {
            setStatus("Failed to read file: " + ex.getMessage());
            return;
        }
        PostmanCollection pc = parsePostman(raw);
        if (pc == null || pc.info == null) {
            setStatus("Not a valid Postman collection.");
            return;
        }
        String name = (pc.info.name == null || pc.info.name.isBlank())
                ? chosen.getName().replaceFirst("(?i)\\.postman_collection\\.json$", "")
                .replaceFirst("(?i)\\.json$", "")
                : pc.info.name;
        pc.info.name = name;

        pc.info._postman_id = null;
        String pretty = Json.stringify(pc);

        if (team.id == LOCAL_TEAM_ID || !auth.isAuthenticated()) {
            Collection imported = new Collection();
            imported.id = -System.currentTimeMillis();
            imported.team_id = team.id;
            imported.name = name;
            imported.localOnly = true;
            imported.raw_json = pretty;
            persistCollectionLocal(imported);
            applyCollections(store.loadCollections(team.id));
            showToast("Imported \"" + name + "\".");
            return;
        }

        setStatus("Importing \"" + name + "\"…");
        runAsync(() -> {
            try {
                Collection created = collectionApi.create(team.id, name, "");
                Collection updated = collectionApi.update(team.id, created.id, null, pretty);
                persistCollectionLocal(updated);
                Platform.runLater(() -> {
                    loadCollections(team.id);
                    showToast("Imported \"" + name + "\".");
                });
            } catch (BackendException ex) {
                Platform.runLater(() -> setStatus(ex.isNetwork()
                        ? "Offline — import requires online for server teams."
                        : "Import failed: " + ex.getMessage()));
            }
        });
    }

    @FXML
    public void onExportCollection() {
        Team team = teamCombo.getValue();
        if (team == null) {
            setStatus("Select a team first.");
            return;
        }
        List<Collection> all = store.loadCollections(team.id);
        if (all.isEmpty()) {
            setStatus("No collections to export.");
            return;
        }
        Collection picked = all.size() == 1 ? all.get(0)
                : PickerDialog.<Collection>builder()
                .title("Export collection")
                .subtitle("Choose which collection to export as Postman v2.1.0 JSON.")
                .items(all)
                .selected(all.get(0))
                .toString(c -> c == null ? "" : c.name)
                .show()
                .orElse(null);
        if (picked == null) return;
        exportCollectionToFile(picked);
    }

    private void exportCollectionToFile(Collection c) {
        if (c == null) return;
        String raw;
        PostmanCollection pc = parsePostman(c.raw_json);
        if (pc == null) pc = new PostmanCollection();
        if (pc.info == null) pc.info = new PostmanCollection.Info();
        if (pc.info.name == null || pc.info.name.isBlank()) pc.info.name = c.name;
        raw = Json.stringify(pc);

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Postman collection");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Postman collection (*.json)", "*.json"));
        fc.setInitialFileName(safeFileName(c.name) + ".postman_collection.json");
        File chosen = fc.showSaveDialog(ownerWindow());
        if (chosen == null) return;
        try {
            Files.writeString(chosen.toPath(), raw);
            showToast("Exported \"" + c.name + "\".");
        } catch (IOException ex) {
            setStatus("Export failed: " + ex.getMessage());
        }
    }

    private static String safeFileName(String s) {
        if (s == null || s.isBlank()) return "collection";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private javafx.stage.Window ownerWindow() {
        return statusLabel != null && statusLabel.getScene() != null
                ? statusLabel.getScene().getWindow() : null;
    }

    @FXML
    public void onNewRequest() {
        Team team = teamCombo.getValue();
        if (team == null) {
            setStatus("Select a team first.");
            return;
        }
        List<Collection> all = store.loadCollections(team.id);
        if (all.isEmpty()) {
            setStatus("Create a collection first, then add requests to it.");
            return;
        }
        Optional<SaveRequestDialog.Result> picked =
                SaveRequestDialog.show(all, "New Request", "Create new request");
        if (picked.isEmpty()) return;
        SaveRequestDialog.Result r = picked.get();
        createRequestInCollection(team, r.collection, r.folderPath, r.name, null, true);
    }


    private void onAddRequestUnder(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;

        List<String> folderPath = new ArrayList<>();
        if (ref.kind == TreeNodeRef.Kind.FOLDER) {
            folderPath.add(ref.item.name == null ? "" : ref.item.name);



        }
        String parentName = ref.kind == TreeNodeRef.Kind.FOLDER ? ref.item.name : ref.collection.name;
        NameDialog.builder()
                .title("New request")
                .subtitle("Adding to " + (parentName == null ? "this collection" : parentName) + ".")
                .fieldLabel("Request name")
                .placeholder("e.g. Get user profile")
                .initial("New Request")
                .okText("Add request")
                .show()
                .ifPresent(name -> {
                    if (name.isBlank()) return;
                    createRequestInCollection(team, ref.collection, folderPath, name.trim(),
                            null, true);
                });
    }


    private void onAddFolderUnder(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;
        NameDialog.builder()
                .title("New folder")
                .subtitle("Folders help organize requests inside a collection.")
                .fieldLabel("Folder name")
                .placeholder("e.g. Auth")
                .initial("New Folder")
                .okText("Add folder")
                .show()
                .ifPresent(name -> {
                    if (name.isBlank()) return;

                    List<Collection> all = store.loadCollections(team.id);
                    Collection target = null;
                    for (Collection c : all) {
                        if (Objects.equals(c.id, ref.collection.id)) {
                            target = c;
                            break;
                        }
                    }
                    if (target == null) {
                        setStatus("Collection not found.");
                        return;
                    }
                    PostmanCollection pc = parsePostman(target.raw_json);
                    if (pc == null) {
                        pc = new PostmanCollection();
                        pc.info.name = target.name == null ? "Untitled" : target.name;
                    }
                    List<PostmanItem> dest = pc.item;
                    if (ref.kind == TreeNodeRef.Kind.FOLDER) {
                        dest = ref.item.item;
                    }
                    PostmanItem folder = new PostmanItem();
                    folder.name = name.trim();
                    dest.add(folder);
                    target.raw_json = Json.stringify(pc);
                    target.dirty = true;
                    persistCollectionLocal(target);
                    applyCollections(store.loadCollections(team.id));
                    pushCollectionIfOnline(team, target);
                });
    }


    private void onRenameNode(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;
        String oldName = ref.kind == TreeNodeRef.Kind.COLLECTION ? ref.collection.name : ref.item.name;
        String kindLabel = ref.kind == TreeNodeRef.Kind.COLLECTION ? "collection"
                : ref.kind == TreeNodeRef.Kind.FOLDER ? "folder" : "request";
        NameDialog.builder()
                .title("Rename " + kindLabel)
                .subtitle(oldName == null || oldName.isBlank()
                        ? "Pick a new name."
                        : "Currently named " + oldName + ".")
                .fieldLabel("New name")
                .initial(oldName == null ? "" : oldName)
                .okText("Rename")
                .show()
                .ifPresent(value -> {
                    String newName = value == null ? "" : value.trim();
                    if (newName.isEmpty() || newName.equals(oldName)) return;

                    List<Collection> all = store.loadCollections(team.id);
                    Collection target = null;
                    for (Collection c : all) {
                        if (Objects.equals(c.id, ref.collection.id)) {
                            target = c;
                            break;
                        }
                    }
                    if (target == null) return;
                    PostmanCollection pc = parsePostman(target.raw_json);

                    if (ref.kind == TreeNodeRef.Kind.COLLECTION) {
                        target.name = newName;
                        if (pc != null) {
                            pc.info.name = newName;
                            target.raw_json = Json.stringify(pc);
                        }
                    } else {
                        if (pc == null) return;
                        if (!renameByName(pc.item, oldName, newName)) return;
                        target.raw_json = Json.stringify(pc);
                    }
                    target.dirty = true;
                    persistCollectionLocal(target);
                    applyCollections(store.loadCollections(team.id));
                    pushCollectionIfOnline(team, target);
                });
    }

    private static boolean renameByName(List<PostmanItem> items, String oldName, String newName) {
        if (items == null || oldName == null) return false;
        for (PostmanItem cur : items) {
            if (oldName.equals(cur.name)) {
                cur.name = newName;
                return true;
            }
            if (cur.item != null && renameByName(cur.item, oldName, newName)) return true;
        }
        return false;
    }

    private void onDeleteNode(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + (ref.kind == TreeNodeRef.Kind.COLLECTION ? "collection \"" + ref.collection.name + "\""
                        : "\"" + (ref.item == null ? "?" : ref.item.name) + "\"") + "?");
        confirm.setHeaderText("Confirm delete");
        if (confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;

        if (ref.kind == TreeNodeRef.Kind.COLLECTION) {

            List<Collection> all = store.loadCollections(team.id);
            all.removeIf(c -> Objects.equals(c.id, ref.collection.id));
            store.saveCollections(team.id, all);
            applyCollections(all);
            if (team.id != LOCAL_TEAM_ID && auth.isAuthenticated()
                    && ref.collection.id != null && ref.collection.id > 0) {
                runAsync(() -> {
                    try {
                        collectionApi.delete(team.id, ref.collection.id);
                    } catch (BackendException ignored) {
                    }
                });
            }
            return;
        }


        List<Collection> all = store.loadCollections(team.id);
        Collection target = null;
        for (Collection c : all) {
            if (Objects.equals(c.id, ref.collection.id)) {
                target = c;
                break;
            }
        }
        if (target == null) return;
        PostmanCollection pc = parsePostman(target.raw_json);
        if (pc == null) return;
        if (removeByName(pc.item, ref.item.name)) {
            target.raw_json = Json.stringify(pc);
            target.dirty = true;
            persistCollectionLocal(target);
            applyCollections(store.loadCollections(team.id));
            pushCollectionIfOnline(team, target);
        }
    }

    private static boolean removeByName(List<PostmanItem> items, String name) {
        if (items == null || name == null) return false;
        Iterator<PostmanItem> it = items.iterator();
        while (it.hasNext()) {
            PostmanItem cur = it.next();
            if (name.equals(cur.name)) {
                it.remove();
                return true;
            }
            if (cur.item != null && removeByName(cur.item, name)) return true;
        }
        return false;
    }

    @FXML
    public void onSync() {
        Team team = teamCombo.getValue();
        if (team == null) return;
        if (team.id == LOCAL_TEAM_ID) {
            setStatus("Local workspace stays on this device — nothing to sync.");
            return;
        }
        requireSignIn("sync collections", () -> {
            setStatus("Syncing…");
            runAsync(() -> {
                try {
                    List<Collection> local = store.loadCollections(team.id);
                    for (Collection c : local) {
                        if (c.dirty && c.id != null && c.id > 0) {
                            try {
                                collectionApi.update(team.id, c.id, null, c.raw_json);
                                c.dirty = false;
                            } catch (BackendException ignored) {
                            }
                        }
                    }
                    store.saveCollections(team.id, local);
                    List<Collection> fresh = collectionApi.list(team.id);
                    for (Collection c : fresh) {
                        if (c.raw_json == null || c.raw_json.isBlank()) {
                            try {
                                c.raw_json = collectionApi.get(team.id, c.id).raw_json;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    store.saveCollections(team.id, fresh);
                    Platform.runLater(() -> {
                        applyCollections(fresh);
                        showToast("Collections synced.");
                    });
                } catch (BackendException e) {
                    Platform.runLater(() -> showToast("Sync failed: " + e.getMessage()));
                }
            });
            commitTabsSync();
        });
    }

    @FXML
    public void onManageEnvironments() {
        Team team = teamCombo.getValue();
        if (team == null) return;
        if (team.id == LOCAL_TEAM_ID) {
            setStatus("Environments for the Local workspace are coming in a later phase.");
            return;
        }
        requireSignIn("manage environments", () ->
                EnvironmentDialog.show(team, environmentCombo.getValue(), environmentApi, store,
                        () -> loadEnvironments(team.id)));
    }



    @FXML
    public void onShowTeam() {
        Team team = teamCombo.getValue();
        if (team == null) return;
        if (team.id == LOCAL_TEAM_ID) {
            setStatus("Local workspace has no members.");
            return;
        }
        requireSignIn("manage team members", () ->
                TeamDialog.show(team, auth.user(), teamApi, inviteApi, () -> {
                    loadTeams();
                    refreshInvitesBadge();
                }));
    }

    @FXML
    public void onCreateTeam() {
        requireSignIn("create a team", () -> NameDialog.builder()
                .title("New team")
                .subtitle("Teams scope collections, environments, and members on the server.")
                .fieldLabel("Team name")
                .placeholder("e.g. Billing API squad")
                .okText("Create team")
                .show()
                .ifPresent(name -> {
                    setStatus("Creating team…");
                    runAsync(() -> {
                        try {
                            Team created = teamApi.create(name);
                            // Make the new team active so applyTeams selects it after the
                            // refresh — applyTeams reads store.loadActiveTeamId() to pick.
                            store.saveActiveTeamId(created.id);
                            // Seed the local cache too so the picker shows the team
                            // immediately, before the background list refresh returns.
                            List<Team> cached = new ArrayList<>(store.loadTeams());
                            boolean seen = false;
                            for (Team t : cached) {
                                if (Objects.equals(t.id, created.id)) {
                                    seen = true;
                                    break;
                                }
                            }
                            if (!seen) cached.add(created);
                            store.saveTeams(cached);
                            Platform.runLater(() -> {
                                loadTeams();
                                showToast("Team \"" + created.name + "\" created.");
                            });
                        } catch (BackendException ex) {
                            Platform.runLater(() -> showToast(ex.isNetwork()
                                    ? "Offline — team creation requires online."
                                    : "Failed to create team: " + ex.getMessage()));
                        }
                    });
                }));
    }

    @FXML
    public void onShowInvites() {
        requireSignIn("see your invites", () ->
                InvitesDialog.show(inviteApi, () -> {
                    loadTeams();
                    refreshInvitesBadge();
                }));
    }

    @FXML
    public void onShowApiKeys() {
        Team team = teamCombo.getValue();
        if (team == null) return;
        if (team.id == LOCAL_TEAM_ID) {
            setStatus("API keys belong to a server team — switch to one first.");
            return;
        }
        requireSignIn("manage API keys", () -> ApiKeysDialog.show(team, apiKeyApi));
    }

    @FXML
    public void onSignIn() {

        if (pendingTabsSync != null) pendingTabsSync.cancel(false);
        commitTabsSync();
        app.showLogin();
    }

    private void refreshInvitesBadge() {
        runAsync(() -> {
            try {
                List<TeamInvite> pending = inviteApi.listForUser();
                int n = pending == null ? 0 : pending.size();
                Platform.runLater(() -> invitesButton.setText(n > 0 ? "Invites (" + n + ")" : "Invites"));
            } catch (BackendException ignored) {
            }
        });
    }



    @FXML
    public void onSignOut() {
        if (pendingTabsSync != null) pendingTabsSync.cancel(false);
        commitTabsSync();
        auth.signOut();

        app.showMain();
    }

    @FXML
    public void onAbout() {
        AboutDialog.show(backend.baseUrl());
    }

    @FXML
    public void onUseDarkTheme() {
        switchTheme("dark");
    }

    @FXML
    public void onUseLightTheme() {
        switchTheme("light");
    }

    private void switchTheme(String theme) {
        if (theme.equals(store.loadTheme())) return;

        if (pendingTabsSync != null) pendingTabsSync.cancel(false);
        commitTabsSync();
        store.saveTheme(theme);
        app.showMain();
    }



    private static PostmanCollection parsePostman(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            return Json.parse(rawJson, PostmanCollection.class);
        } catch (Exception e) {
            LOG.warn("Failed to parse collection json: {}", e.getMessage());
            return null;
        }
    }

    private void runAsync(Runnable r) {
        CompletableFuture.runAsync(r).exceptionally(t -> {
            LOG.error("Background task failed", t);
            Platform.runLater(() -> setStatus("Error: " + t.getMessage()));
            return null;
        });
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }


    private void showToast(String message) {


        setStatus(message);

        if (statusLabel == null || statusLabel.getScene() == null) return;
        javafx.stage.Window owner = statusLabel.getScene().getWindow();
        if (owner == null) return;

        Label label = new Label(message);
        label.setStyle(
                "-fx-background-color: #1f2733;" +
                        "-fx-border-color: #ff6c37;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-text-fill: #f0f4f8;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: 600;" +
                        "-fx-padding: 12 20 12 20;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 18, 0.2, 0, 4);"
        );
        label.setOpacity(0);




        label.applyCss();
        label.layout();
        double w = Math.max(label.prefWidth(-1), 240);
        double h = Math.max(label.prefHeight(-1), 40);

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.getContent().add(label);
        popup.setAutoFix(false);
        popup.setHideOnEscape(false);

        double x = owner.getX() + (owner.getWidth() - w) / 2.0;
        double y = owner.getY() + owner.getHeight() - h - 80;
        popup.setX(x);
        popup.setY(y);
        popup.show(owner);



        popup.setOnShown(e -> {
            double rw = label.getWidth();
            double rh = label.getHeight();
            popup.setX(owner.getX() + (owner.getWidth() - rw) / 2.0);
            popup.setY(owner.getY() + owner.getHeight() - rh - 80);
        });

        javafx.animation.FadeTransition fadeIn =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(160), label);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        javafx.animation.PauseTransition hold =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3.5));

        javafx.animation.FadeTransition fadeOut =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(260), label);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> popup.hide());

        new javafx.animation.SequentialTransition(fadeIn, hold, fadeOut).play();
    }
}
