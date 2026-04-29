package uz.postbaby.desktop.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uz.postbaby.desktop.model.Authorization;
import uz.postbaby.desktop.model.SavedTab;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tab state held by MainController. The wire-format SavedTab is a strict
 * subset of this — auth, body type, response, and source-collection links are
 * desktop-only and never round-trip through /tabs.
 */
public class TabState {

    public final String tabId;
    public String name;
    public String method = "GET";
    public String url = "";
    public String bodyType = "none";
    public String body = "";

    public final ObservableList<KeyValueRow> headers = FXCollections.observableArrayList();
    public final ObservableList<KeyValueRow> params = FXCollections.observableArrayList();

    /**
     * Last response (kept in memory only).
     */
    public Integer responseStatus;
    public String responseStatusText;
    public Long responseTimeMs;
    public String responseBody;
    public final ObservableList<Map.Entry<String, String>> responseHeaders = FXCollections.observableArrayList();

    /**
     * If this tab was opened from a request inside a collection, save links here for save-back.
     */
    public Long collectionId;
    public String itemPath;

    /**
     * Per-tab auth config. Stored locally; backend's /tabs schema ignores it.
     */
    public Authorization authorization = Authorization.noauth();

    /**
     * True until the tab is synced through /tabs.
     */
    public boolean dirty = true;

    public TabState() {
        this.tabId = UUID.randomUUID().toString();
        this.name = "Untitled";
    }

    public TabState(String tabId, String name) {
        this.tabId = tabId == null ? UUID.randomUUID().toString() : tabId;
        this.name = name == null ? "Untitled" : name;
    }

    public SavedTab toWire(int sortOrder, boolean isActive) {
        SavedTab t = new SavedTab();
        t.tab_id = tabId;
        t.name = name;
        t.method = method;
        t.url = url;
        t.body = body == null ? "" : body;
        t.headers = new LinkedHashMap<>();
        for (KeyValueRow row : headers) {
            if (row == null || row.isBlank() || row.getKey() == null || row.getKey().isBlank()) continue;
            if (row.isAuto()) continue; // auth-injected rows live in the Authorization block
            t.headers.put(row.getKey(), row.getValue() == null ? "" : row.getValue());
        }
        t.query_params = new LinkedHashMap<>();
        for (KeyValueRow row : params) {
            if (row == null || row.isBlank() || row.getKey() == null || row.getKey().isBlank()) continue;
            if (row.isAuto()) continue;
            t.query_params.put(row.getKey(), row.getValue() == null ? "" : row.getValue());
        }
        t.is_active = isActive;
        t.sort_order = sortOrder;
        t.authorization = authorization;
        return t;
    }

    public static TabState fromWire(SavedTab w) {
        TabState s = new TabState(w.tab_id, w.name);
        s.method = w.method == null ? "GET" : w.method;
        s.url = w.url == null ? "" : w.url;
        s.body = w.body == null ? "" : w.body;
        if (w.body != null && !w.body.isEmpty()) s.bodyType = "raw";
        if (w.headers != null) {
            w.headers.forEach((k, v) -> s.headers.add(new KeyValueRow(k, v)));
        }
        if (w.query_params != null) {
            w.query_params.forEach((k, v) -> s.params.add(new KeyValueRow(k, v)));
        }
        if (w.authorization != null) s.authorization = w.authorization;
        return s;
    }
}
