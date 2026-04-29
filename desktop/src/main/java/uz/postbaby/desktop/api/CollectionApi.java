package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import uz.postbaby.desktop.model.Collection;

import java.util.List;
import java.util.Map;

public class CollectionApi {

    private final BackendClient client;

    public CollectionApi(BackendClient client) {
        this.client = client;
    }

    public List<Collection> list(long teamId) {
        return client.get("/teams/" + teamId + "/collections",
                new TypeReference<List<Collection>>() {
                });
    }

    public Collection get(long teamId, long id) {
        return client.get("/teams/" + teamId + "/collections/" + id, Collection.class);
    }

    public Collection create(long teamId, String name, String description) {
        return client.post("/teams/" + teamId + "/collections",
                Map.of("name", name, "description", description == null ? "" : description),
                Collection.class);
    }

    public Collection update(long teamId, long id, String name, String rawJson) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (name != null) body.put("name", name);
        if (rawJson != null) body.put("raw_json", rawJson);
        return client.put("/teams/" + teamId + "/collections/" + id, body, Collection.class);
    }

    public void delete(long teamId, long id) {
        client.delete("/teams/" + teamId + "/collections/" + id);
    }
}
