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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.PostBabyApp;
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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    /**
     * Sentinel team id used when the user isn't signed in. Server team ids start at 1.
     */
    private static final long LOCAL_TEAM_ID = 0L;
    /**
     * User id used to key local-only files when no real user is signed in.
     */
    private static final long ANON_USER_ID = 0L;

    @FXML
    private ComboBox<Team> teamCombo;
    @FXML
    private ComboBox<Environment> environmentCombo;
    @FXML
    private Label statusLabel;
    @FXML
    private Label userLabel;
    @FXML
    private Button invitesButton;
    @FXML
    private Button signInButton;

    @FXML
    private TreeView<TreeNodeRef> collectionTree;

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
    /**
     * Built programmatically inside bodyTabRoot — RichTextFX needs VirtualizedScrollPane.
     */
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

    private PostBabyApp app;
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
    /**
     * Re-entrancy guard for URL ⇄ Params two-way sync.
     */
    private boolean syncingUrlParams = false;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "postbaby-tabs-sync");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pendingTabsSync;

    public void bind(PostBabyApp app, AuthService auth, BackendClient backend, LocalStore store) {
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
    }

    private void refreshAuthIndicator() {
        boolean signedIn = auth.isAuthenticated();
        if (signedIn) {
            String label = auth.user() == null ? ""
                    : (auth.user().email == null ? auth.user().name : auth.user().email);
            userLabel.setText(label);
            signInButton.setVisible(false);
            signInButton.setManaged(false);
            invitesButton.setVisible(true);
            invitesButton.setManaged(true);
        } else {
            userLabel.setText("Offline");
            signInButton.setVisible(true);
            signInButton.setManaged(true);
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

    /**
     * Run the action if signed in; otherwise show a friendly nudge.
     */
    private void requireSignIn(String reason, Runnable action) {
        if (auth.isAuthenticated()) {
            action.run();
            return;
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Sign in required");
        a.setHeaderText("Sign in to " + reason);
        a.setContentText("This action talks to the PostBaby server. Click Sign in on the toolbar to continue.");
        a.showAndWait();
    }

    /* ================================================================
     *  Request editor (URL / method / params / headers / body)
     * ================================================================ */

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

        // WebView's natural size tracks its content — make it fill the tab instead.
        if (responseBodyView != null) {
            responseBodyView.setMinSize(0, 0);
            responseBodyView.setPrefSize(100, 100);
            responseBodyView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        // Defer divider positioning until after the SplitPane has its own size,
        // and force-style the divider in case the CSS rule loses the cascade.
        if (editorResponseSplit != null) {
            // Allow the children to shrink so the divider is actually draggable
            for (javafx.scene.Node child : editorResponseSplit.getItems()) {
                if (child instanceof javafx.scene.layout.Region r) r.setMinHeight(40);
            }
            javafx.application.Platform.runLater(() -> {
                editorResponseSplit.setDividerPositions(0.5);
                forceDividerStyle(editorResponseSplit);
            });
        }

        initBodyEditor();
        initAuthTab();

        // Debounced sync on field edits — also detect cURL paste
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

    /**
     * JavaFX's modena CSS often outranks user CSS for SplitPane dividers in
     * the cascade. We look the divider up after layout and apply inline style
     * so it's guaranteed to be visible and draggable.
     */
    private static void forceDividerStyle(javafx.scene.control.SplitPane sp) {
        for (javafx.scene.Node n : sp.lookupAll(".split-pane-divider")) {
            if (n instanceof javafx.scene.layout.Region r) {
                r.setStyle(
                        "-fx-background-color:#3a3a3e;" +
                                "-fx-background-insets:0;" +
                                "-fx-padding:4 0 4 0;" +
                                "-fx-min-height:8;" +
                                "-fx-pref-height:8;" +
                                "-fx-cursor:v-resize;");
                r.setOnMouseEntered(e -> r.setStyle(
                        "-fx-background-color:#ff6c37;" +
                                "-fx-background-insets:0;" +
                                "-fx-padding:4 0 4 0;" +
                                "-fx-min-height:8;" +
                                "-fx-pref-height:8;" +
                                "-fx-cursor:v-resize;"));
                r.setOnMouseExited(e -> r.setStyle(
                        "-fx-background-color:#3a3a3e;" +
                                "-fx-background-insets:0;" +
                                "-fx-padding:4 0 4 0;" +
                                "-fx-min-height:8;" +
                                "-fx-pref-height:8;" +
                                "-fx-cursor:v-resize;"));
            }
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

    /**
     * Smart-paste: when the URL field's contents look like a curl command,
     * parse it and populate method, URL, headers, params, body, and basic
     * auth across the editor. Returns true if the import succeeded so the
     * caller can skip the normal URL→Params syncing.
     */
    private boolean importCurl(String raw) {
        CurlParser.Parsed c = CurlParser.parse(raw);
        if (c == null || c.url == null || c.url.isBlank()) return false;

        // Suppress the URL listener and the URL⇄Params guard during the rebuild
        boolean prevSuppress = suppressUiSync;
        boolean prevSyncing = syncingUrlParams;
        suppressUiSync = true;
        syncingUrlParams = true;
        try {
            methodCombo.getSelectionModel().select(c.method == null ? "GET" : c.method);

            // Headers — wipe non-auto rows, then drop in the parsed ones above any auto rows
            ObservableList<KeyValueRow> headers = headersTable.getItems();
            headers.removeIf(r -> !r.isAuto() && !r.isBlank());
            int insertAt = 0;
            for (KeyValueRow row : headers) {
                if (row.isAuto()) break;
                insertAt++;
            }
            if (!c.headers.isEmpty()) headers.addAll(Math.min(insertAt, headers.size()), c.headers);
            ensureBlankRow(headers);

            // Body
            if (c.body != null && !c.body.isEmpty()) {
                bodyTypeCombo.getSelectionModel().select("raw");
                bodyArea.replaceText(c.body);
            } else {
                bodyTypeCombo.getSelectionModel().select("none");
                bodyArea.replaceText("");
            }

            // Auth (currently only Basic from -u user:pass)
            if (c.auth != null) {
                loadAuthIntoUi(c.auth);
            }

            // Replace URL with the parsed URL — release the URL guard for this call only
            // so syncUrlIntoParams below can do its work.
            urlField.setText(c.url);
            syncingUrlParams = false;
            syncUrlIntoParams(c.url);
            // Re-sync the auth-injected header rows now that headers were rewritten
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

    /**
     * Recompute and apply JSON syntax-highlighting style spans. Cheap to call.
     */
    private void applyJsonHighlight() {
        if (bodyArea == null) return;
        String text = bodyArea.getText();
        if (text == null) text = "";
        try {
            bodyArea.setStyleSpans(0, JsonStyle.compute(text));
        } catch (Exception ignored) {
            // Tokenizer is permissive but defensive — never let a styling glitch crash typing
        }
    }

    /* ================================================================
     *  Authorization tab
     * ================================================================ */

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

        // Field-level listeners so edits sync into headers + debounce-save into the tab
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
            default -> { /* noauth */ }
        }
        return a;
    }

    /* ================================================================
     *  URL ⇄ Params bidirectional sync
     * ================================================================ */

    private void syncUrlIntoParams(String url) {
        if (syncingUrlParams) return;
        syncingUrlParams = true;
        try {
            List<KeyValueRow> parsed = parseQueryFromUrl(url);
            ObservableList<KeyValueRow> items = paramsTable.getItems();
            // Strip non-auto rows; preserve auto rows (none today, but defensive)
            items.removeIf(r -> !r.isAuto() && !r.isBlank());
            // Insert parsed rows above any auto rows, in their original order
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

    /**
     * Strip everything from the first '?' to the (optional) '#' fragment.
     */
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
        // Pull off fragment so we put query before it
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

    /**
     * Decode that preserves {{var}} placeholders untouched.
     */
    private static String safeDecode(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains("{{") || s.contains("}}")) return s;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /**
     * Encode that preserves {{var}} placeholders untouched.
     */
    private static String safeEncode(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains("{{") || s.contains("}}")) return s;
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /* ================================================================
     *  Auth → Headers/Params auto-row sync
     * ================================================================ */

    /**
     * Maintains an auto-generated row in the Headers table (or Params, for API key
     * in query mode) reflecting the current Authorization tab. Rows that the user
     * has manually edited are left alone — see EditingCell.commitEdit.
     */
    private void syncAuthIntoHeaders() {
        if (paramsTable.getItems() == null || headersTable.getItems() == null) return;
        // Drop any prior auto rows
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
                        // Show the encoded value the same way it's sent. Variable
                        // substitution for the Basic header happens at send time.
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
                default -> { /* noauth */ }
            }
        }
        if (row != null) {
            row.setAuto(true);
            ObservableList<KeyValueRow> target = toQuery ? paramsTable.getItems() : headersTable.getItems();
            // Insert above any blank trailing row
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

    /**
     * Apply the tab's auth into the request being sent. Variable substitution happens here too.
     */
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
            default -> { /* noauth — nothing */ }
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

    /* ================================================================
     *  Open-request tabs
     * ================================================================ */

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

    /**
     * Load tabs from local store, then (if signed in) fetch /tabs and merge.
     */
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
            // Always have at least one tab
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

    /**
     * Replace the tab's label with a TextField until the user commits / cancels.
     */
    private void startInlineRename(Tab tab, Label label, TabState state) {
        TextField field = new TextField(state.name == null ? "" : state.name);
        field.getStyleClass().add("open-tab-rename");
        field.setPrefWidth(Math.max(label.getWidth() + 30, 140));
        Runnable commit = () -> {
            String typed = field.getText() == null ? "" : field.getText().trim();
            if (!typed.isEmpty()) {
                state.name = typed;
                if (state.itemPath != null) state.itemPath = typed; // keep tab→collection link aligned
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
        // Push UI state into the model and refresh tab title
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
        // s.responseBody is the source of truth — only updated by applyResponse;
        // the WebView is purely a renderer of it.
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

            // Strip any auto rows from the previous render so we start clean
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

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Debounce-saves the open tab set to the local store + (if signed in) /tabs.
     */
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

    /* ================================================================
     *  Teams + environments
     * ================================================================ */

    private void loadTeams() {
        // Always start with at least the Local workspace, then layer on cached + fresh server teams.
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
                        setStatus("Session expired — sign in to sync.");
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

    /* ================================================================
     *  Collection tree
     * ================================================================ */

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
                String prefix = switch (ref.kind) {
                    case COLLECTION -> "📁 ";
                    case FOLDER -> "📂 ";
                    case REQUEST -> methodLabel(ref.item) + " ";
                };
                setText(prefix + ref.toString());
                setContextMenu(buildContextMenu(ref));
            }
        });
        collectionTree.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null && val.getValue() != null && val.getValue().kind == TreeNodeRef.Kind.REQUEST) {
                openOrFocusRequestTab(val.getValue());
            }
        });
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
                MenuItem delete = new MenuItem("Delete collection");
                delete.setOnAction(e -> onDeleteNode(ref));
                menu.getItems().addAll(addReq, addFolder, new SeparatorMenuItem(), rename, delete);
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
        // If a tab is already open for this collectionId+itemPath, focus it
        for (Tab t : openTabsPane.getTabs()) {
            if (t.getUserData() instanceof TabState s
                    && Objects.equals(s.collectionId, ref.collection.id)
                    && Objects.equals(s.itemPath, pathOf(ref))) {
                openTabsPane.getSelectionModel().select(t);
                return;
            }
        }
        // Else open a new tab populated from the request
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
        TreeItem<TreeNodeRef> root = collectionTree.getRoot();
        root.getChildren().clear();
        for (Collection c : collections) {
            TreeItem<TreeNodeRef> cItem = new TreeItem<>(TreeNodeRef.collection(c));
            cItem.setExpanded(true);
            PostmanCollection pc = parsePostman(c.raw_json);
            if (pc != null) {
                for (PostmanItem item : pc.item) {
                    cItem.getChildren().add(buildItemNode(c, item, null));
                }
            }
            root.getChildren().add(cItem);
        }
    }

    private TreeItem<TreeNodeRef> buildItemNode(Collection c, PostmanItem item, PostmanItem parent) {
        if (item.isFolder()) {
            TreeItem<TreeNodeRef> folder = new TreeItem<>(TreeNodeRef.folder(c, item, parent));
            folder.setExpanded(false);
            for (PostmanItem child : item.item) {
                folder.getChildren().add(buildItemNode(c, child, item));
            }
            return folder;
        }
        return new TreeItem<>(TreeNodeRef.request(c, item, parent));
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

    /* ================================================================
     *  Send request
     * ================================================================ */

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
            responseStatusLabel.setText(resp.status + " " + (resp.status_text == null ? "" : resp.status_text));
            String tier = resp.status / 100 + "xx";
            responseStatusLabel.getStyleClass().setAll("status-pill", "status-" + tier);
        }
        responseTimeLabel.setText(elapsedMs + " ms");
        String pretty = prettifyJsonIfPossible(resp.body);
        renderResponseBody(pretty);
        if (currentTab != null) {
            currentTab.responseHeaders.setAll(new ArrayList<>(resp.headers.entrySet()));
            currentTab.responseStatus = resp.status;
            currentTab.responseStatusText = responseStatusLabel.getText();
            currentTab.responseTimeMs = elapsedMs;
            currentTab.responseBody = pretty;
        }
        responseHeadersTable.setItems(currentTab != null ? currentTab.responseHeaders
                : FXCollections.observableArrayList(new ArrayList<>(resp.headers.entrySet())));
        setStatus("Done");
    }

    /**
     * Render the (already-pretty-printed) body into the response WebView with JSON syntax highlighting.
     */
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

    /* ================================================================
     *  Save current tab back into its source collection
     * ================================================================ */

    /**
     * Right-click "Sync from backend" — open the tree's request in a tab and pull fresh.
     */
    private void syncRequestNode(TreeNodeRef ref) {
        if (ref == null || ref.kind != TreeNodeRef.Kind.REQUEST) return;
        openOrFocusRequestTab(ref);
        onSyncRequest();
    }

    /**
     * Pull the latest version of the current tab's linked request from the backend.
     */
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
            setStatus("Sign in to sync from the backend.");
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
                // Cache the fresh collection
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
                        setStatus("Synced.");
                    }
                });
            } catch (BackendException e) {
                Platform.runLater(() -> {
                    syncRequestButton.setDisable(false);
                    setStatus(e.isNetwork() ? "Offline — can't sync right now."
                            : "Sync failed: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Replace a tab's editor state with the contents of a Postman item from disk.
     */
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
        // Reload the currently-displayed tab UI so user sees the fresh content
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

    /**
     * Save a tab that's already linked to a collection request.
     */
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
            // Request was renamed / deleted under us — fall back to creating a new entry
            saveAsNewRequest(team, tab);
            return;
        }
        applyTabToItem(tab, item);
        target.raw_json = Json.stringify(pc);
        target.dirty = true;
        persistCollectionLocal(target);
        pushCollectionIfOnline(team, target);
    }

    /**
     * Open the Save dialog and create a fresh request in the chosen collection.
     */
    private void saveAsNewRequest(Team team, TabState tab) {
        List<Collection> all = store.loadCollections(team.id);
        String defaultName = deriveRequestName(tab);
        Optional<SaveRequestDialog.Result> picked =
                SaveRequestDialog.show(all, defaultName, "Save request to collection");
        if (picked.isEmpty()) return;
        SaveRequestDialog.Result r = picked.get();
        createRequestInCollection(team, r.collection, r.folderPath, r.name, tab, /*openAsNewTab=*/false);
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

    /**
     * Insert a new request into a collection (optionally inside a nested folder),
     * persist locally, push to the backend if online, and either link the existing
     * tab to it or open a brand-new tab.
     */
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
            setStatus("Saved locally.");
            return;
        }
        setStatus("Saving…");
        runAsync(() -> {
            try {
                Collection updated = collectionApi.update(team.id, collection.id, null, collection.raw_json);
                collection.raw_json = updated.raw_json;
                collection.dirty = false;
                persistCollectionLocal(collection);
                Platform.runLater(() -> setStatus("Saved & synced."));
            } catch (BackendException e) {
                Platform.runLater(() -> setStatus(
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
            if (row.isAuto()) continue; // auth-injected — saved via Authorization tab instead
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

    /* ================================================================
     *  Collections menu actions
     * ================================================================ */

    @FXML
    public void onNewCollection() {
        Team team = teamCombo.getValue();
        if (team == null) {
            setStatus("Select a team first.");
            return;
        }
        TextInputDialog dlg = new TextInputDialog("New Collection");
        dlg.setHeaderText("Create collection");
        dlg.setContentText("Name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;

            // Local workspace or offline: create entirely in the local store with a synthetic id.
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
        createRequestInCollection(team, r.collection, r.folderPath, r.name, null, /*openAsNewTab=*/true);
    }

    /**
     * Used by the tree context menu to add a request directly under a collection or folder.
     */
    private void onAddRequestUnder(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;
        // Build folder path leading to ref (empty if ref is the collection root)
        List<String> folderPath = new ArrayList<>();
        if (ref.kind == TreeNodeRef.Kind.FOLDER) {
            folderPath.add(ref.item.name == null ? "" : ref.item.name);
            // Note: we don't currently track ancestor folder names on TreeNodeRef.
            // For now, a single-level folder selection works; nested folders use the
            // SaveRequestDialog folder picker.
        }
        TextInputDialog dlg = new TextInputDialog("New Request");
        dlg.setHeaderText("Add request to " + (ref.kind == TreeNodeRef.Kind.FOLDER
                ? ref.item.name : ref.collection.name));
        dlg.setContentText("Name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            createRequestInCollection(team, ref.collection, folderPath, name.trim(),
                    null, /*openAsNewTab=*/true);
        });
    }

    /**
     * Used by the tree context menu to add a folder.
     */
    private void onAddFolderUnder(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;
        TextInputDialog dlg = new TextInputDialog("New Folder");
        dlg.setHeaderText("Add folder");
        dlg.setContentText("Folder name:");
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            // Find collection in cache, mutate, persist
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
                dest = ref.item.item; // this mutates the ref's folder live; we'll rewrite raw_json
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

    /**
     * Rename a tree node (collection root, folder, or request).
     */
    private void onRenameNode(TreeNodeRef ref) {
        Team team = teamCombo.getValue();
        if (team == null || ref == null) return;
        String oldName = ref.kind == TreeNodeRef.Kind.COLLECTION ? ref.collection.name : ref.item.name;
        TextInputDialog dlg = new TextInputDialog(oldName == null ? "" : oldName);
        dlg.setHeaderText("Rename");
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(value -> {
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
            // Delete collection — backend if online; local only for Local team
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

        // Folder or request — locate in raw_json by walking and remove
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
                        setStatus("Synced.");
                    });
                } catch (BackendException e) {
                    Platform.runLater(() -> setStatus("Sync failed: " + e.getMessage()));
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

    /* ================================================================
     *  Phase 2 dialogs
     * ================================================================ */

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
        // Stop any pending tabs sync — login swap rebuilds the controller
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

    /* ================================================================
     *  Misc
     * ================================================================ */

    @FXML
    public void onSignOut() {
        if (pendingTabsSync != null) pendingTabsSync.cancel(false);
        commitTabsSync();
        auth.signOut();
        // Stay in the main window — just refresh into offline mode
        app.showMain();
    }

    @FXML
    public void onAbout() {
        Alert a = new Alert(Alert.AlertType.INFORMATION,
                "PostBaby Desktop\nVersion 0.1.0\nBackend: " + backend.baseUrl());
        a.setHeaderText("About");
        a.showAndWait();
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
        // Flush any pending state before the controller is rebuilt
        if (pendingTabsSync != null) pendingTabsSync.cancel(false);
        commitTabsSync();
        store.saveTheme(theme);
        app.showMain();
    }

    /* ================================================================
     *  Helpers
     * ================================================================ */

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
}
