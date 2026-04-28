package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import uz.postbaby.desktop.model.SavedTab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TabApi {

    private final BackendClient client;

    public TabApi(BackendClient client) {
        this.client = client;
    }

    public List<SavedTab> list() {
        return client.get("/tabs", new TypeReference<List<SavedTab>>() {});
    }

    public void save(List<SavedTab> tabs, String activeTabId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tabs", tabs);
        body.put("active_tab_id", activeTabId == null ? "" : activeTabId);
        client.sendRaw("POST", "/tabs", body);
    }
}
