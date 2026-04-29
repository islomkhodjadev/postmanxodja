package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire-format matching the backend /tabs endpoint. Intentionally narrow —
 * the desktop's per-tab UI state (auth, body type, response, etc.) is held
 * elsewhere and only the subset that round-trips to the server lives here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SavedTab {
    public String tab_id;
    public String name;
    public String method;
    public String url;
    public Map<String, String> headers = new LinkedHashMap<>();
    public String body;
    public Map<String, String> query_params = new LinkedHashMap<>();
    public boolean is_active;
    public int sort_order;

    /**
     * Desktop-only — the backend's tab schema ignores this field.
     */
    public Authorization authorization;

    public SavedTab() {
    }
}
