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
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
import uz.postbaby.desktop.util.Json;
import uz.postbaby.desktop.util.Variables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainController {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);
    private static final long TABS_SYNC_DEBOUNCE_MS = 1500;
    /** Sentinel team id used when the user isn't signed in. Server team ids start at 1. */
    private static final long LOCAL_TEAM_ID = 0L;
    /** User id used to key local-only files when no real user is signed in. */
    private static final long ANON_USER_ID = 0L;

    @FXML private ComboBox<Team> teamCombo;
    @FXML private ComboBox<Environment> environmentCombo;
    @FXML private Label statusLabel;
    @FXML private Label userLabel;
    @FXML private Button invitesButton;
    @FXML private Button signInButton;

    @FXML private TreeView<TreeNodeRef> collectionTree;

    @FXML private TabPane openTabsPane;
    @FXML private Button newTabButton;

    @FXML private ComboBox<String> methodCombo;
    @FXML private TextField urlField;
    @FXML private Button sendButton;

    @FXML private TabPane editorTabs;

    @FXML private TableView<KeyValueRow> paramsTable;
    @FXML private TableColumn<KeyValueRow, String> paramKeyCol;
    @FXML private TableColumn<KeyValueRow, String> paramValueCol;
    @FXML private TableColumn<KeyValueRow, String> paramRemoveCol;

    @FXML private TableView<KeyValueRow> headersTable;
    @FXML private TableColumn<KeyValueRow, String> headerKeyCol;
    @FXML private TableColumn<KeyValueRow, String> headerValueCol;
    @FXML private TableColumn<KeyValueRow, String> headerRemoveCol;

    @FXML private ComboBox<String> bodyTypeCombo;
    @FXML private TextArea bodyArea;

    @FXML private Label responseStatusLabel;
    @FXML private Label responseTimeLabel;
    @FXML private TextArea responseBodyArea;
    @FXML private TableView<Map.Entry<String, String>> responseHeadersTable;
    @FXML private TableColumn<Map.Entry<String, String>, String> respHeaderKeyCol;
    @FXML private TableColumn<Map.Entry<String, String>, String> respHeaderValueCol;

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

    /** Run the action if signed in; otherwise show a friendly nudge. */
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

        // Debounced sync on field edits
        urlField.textProperty().addListener((obs, old, val) -> onTabFieldChanged());
        methodCombo.valueProperty().addListener((obs, old, val) -> onTabFieldChanged());
        bodyArea.textProperty().addListener((obs, old, val) -> onTabFieldChanged());
        bodyTypeCombo.valueProperty().addListener((obs, old, val) -> onTabFieldChanged());
    }

    private void configureKeyValueTable(TableView<KeyValueRow> table,
                                         TableColumn<KeyValueRow, String> keyCol,
                                         TableColumn<KeyValueRow, String> valCol,
                                         TableColumn<KeyValueRow, String> removeCol) {
        table.setEditable(true);
        keyCol.setCellValueFactory(new PropertyValueFactory<>("key"));
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        keyCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valCol.setCellFactory(TextFieldTableCell.forTableColumn());
        keyCol.setOnEditCommit(e -> {
            e.getRowValue().setKey(e.getNewValue());
            ensureBlankRow(table.getItems());
            onTabFieldChanged();
        });
        valCol.setOnEditCommit(e -> {
            e.getRowValue().setValue(e.getNewValue());
            ensureBlankRow(table.getItems());
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
                    onTabFieldChanged();
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
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

    /** Load tabs from local store, then (if signed in) fetch /tabs and merge. */
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
                        toSelect = t; break;
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
        tab.setText(displayTitle(state));
        tab.setOnClosed(ev -> onTabClosed(state));
        tabsByState.put(state.tabId, tab);
        openTabsPane.getTabs().add(tab);
        if (select) openTabsPane.getSelectionModel().select(tab);
        return tab;
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
        Tab t = tabsByState.get(currentTab.tabId);
        if (t != null) t.setText(displayTitle(currentTab));
        scheduleTabsSync();
    }

    private void flushUiToTab(TabState s) {
        s.method = methodCombo.getValue() == null ? "GET" : methodCombo.getValue();
        s.url = urlField.getText() == null ? "" : urlField.getText();
        s.bodyType = bodyTypeCombo.getValue() == null ? "none" : bodyTypeCombo.getValue();
        s.body = bodyArea.getText() == null ? "" : bodyArea.getText();
        s.params.setAll(new ArrayList<>(paramsTable.getItems()));
        s.headers.setAll(new ArrayList<>(headersTable.getItems()));
        s.responseStatus = parseIntOrNull(responseStatusLabel.getText());
        s.responseStatusText = responseStatusLabel.getText();
        s.responseTimeMs = parseLongOrNull(responseTimeLabel.getText());
        s.responseBody = responseBodyArea.getText();
        s.responseHeaders.setAll(responseHeadersTable.getItems());
        s.dirty = true;
    }

    private void loadTabIntoUi(TabState s) {
        suppressUiSync = true;
        try {
            methodCombo.getSelectionModel().select(s.method == null ? "GET" : s.method);
            urlField.setText(s.url == null ? "" : s.url);
            bodyTypeCombo.getSelectionModel().select(s.bodyType == null ? "none" : s.bodyType);
            bodyArea.setText(s.body == null ? "" : s.body);

            ensureBlankRow(s.params);
            ensureBlankRow(s.headers);
            paramsTable.setItems(s.params);
            headersTable.setItems(s.headers);

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
            responseBodyArea.setText(s.responseBody == null ? "" : s.responseBody);
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
        } catch (Exception e) { return null; }
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return null; }
    }

    /** Debounce-saves the open tab set to the local store + (if signed in) /tabs. */
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
            @Override public String toString(Team t) { return t == null ? "" : t.name; }
            @Override public Team fromString(String s) { return null; }
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
            @Override public String toString(Environment e) { return e == null ? "" : e.name; }
            @Override public Environment fromString(String s) { return null; }
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
            @Override protected void updateItem(TreeNodeRef ref, boolean empty) {
                super.updateItem(ref, empty);
                if (empty || ref == null) {
                    setText(null); setGraphic(null); return;
                }
                String prefix = switch (ref.kind) {
                    case COLLECTION -> "📁 ";
                    case FOLDER -> "📂 ";
                    case REQUEST -> methodLabel(ref.item) + " ";
                };
                setText(prefix + ref.toString());
            }
        });
        collectionTree.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null && val.getValue() != null && val.getValue().kind == TreeNodeRef.Kind.REQUEST) {
                openOrFocusRequestTab(val.getValue());
            }
        });
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
                        } catch (Exception ignored) {}
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
        responseBodyArea.setText(prettifyJsonIfPossible(resp.body));
        if (currentTab != null) {
            currentTab.responseHeaders.setAll(new ArrayList<>(resp.headers.entrySet()));
            currentTab.responseStatus = resp.status;
            currentTab.responseStatusText = responseStatusLabel.getText();
            currentTab.responseTimeMs = elapsedMs;
            currentTab.responseBody = responseBodyArea.getText();
        }
        responseHeadersTable.setItems(currentTab != null ? currentTab.responseHeaders
                : FXCollections.observableArrayList(new ArrayList<>(resp.headers.entrySet())));
        setStatus("Done");
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
        bodyArea.setText(prettifyJsonIfPossible(bodyArea.getText()));
    }

    @FXML
    public void onCopyResponse() {
        ClipboardContent c = new ClipboardContent();
        c.putString(responseBodyArea.getText());
        Clipboard.getSystemClipboard().setContent(c);
    }

    /* ================================================================
     *  Save current tab back into its source collection
     * ================================================================ */

    @FXML
    public void onSaveRequest() {
        if (currentTab == null || currentTab.collectionId == null) {
            setStatus("This tab isn't linked to a collection — open a request from the tree first.");
            return;
        }
        Team team = teamCombo.getValue();
        if (team == null) return;

        // Find collection in cache
        List<Collection> all = store.loadCollections(team.id);
        Collection target = null;
        for (Collection c : all) {
            if (Objects.equals(c.id, currentTab.collectionId)) { target = c; break; }
        }
        if (target == null) {
            setStatus("Source collection not found locally. Try refreshing.");
            return;
        }

        PostmanCollection pc = parsePostman(target.raw_json);
        if (pc == null) {
            setStatus("Source collection is not parseable.");
            return;
        }
        PostmanItem item = findItemByPath(pc.item, currentTab.itemPath);
        if (item == null) {
            setStatus("Source request not found in collection.");
            return;
        }
        applyTabToItem(currentTab, item);
        target.raw_json = Json.stringify(pc);
        target.dirty = true;
        persistCollectionLocal(target);

        // Skip the network round-trip for the Local workspace or when signed out
        if (team.id == LOCAL_TEAM_ID || !auth.isAuthenticated()) {
            setStatus("Saved locally.");
            return;
        }

        Collection finalTarget = target;
        runAsync(() -> {
            try {
                Collection updated = collectionApi.update(team.id, finalTarget.id, null, finalTarget.raw_json);
                finalTarget.raw_json = updated.raw_json;
                finalTarget.dirty = false;
                persistCollectionLocal(finalTarget);
                Platform.runLater(() -> setStatus("Saved & synced."));
            } catch (BackendException e) {
                Platform.runLater(() -> setStatus(
                        e.isNetwork() ? "Saved locally. Will sync when online." : "Saved locally. Sync failed: " + e.getMessage()));
            }
        });
        setStatus("Saved locally.");
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
            if (Objects.equals(all.get(i).id, c.id)) { all.set(i, c); replaced = true; break; }
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
                            } catch (BackendException ignored) {}
                        }
                    }
                    store.saveCollections(team.id, local);
                    List<Collection> fresh = collectionApi.list(team.id);
                    for (Collection c : fresh) {
                        if (c.raw_json == null || c.raw_json.isBlank()) {
                            try { c.raw_json = collectionApi.get(team.id, c.id).raw_json; } catch (Exception ignored) {}
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
            } catch (BackendException ignored) {}
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
