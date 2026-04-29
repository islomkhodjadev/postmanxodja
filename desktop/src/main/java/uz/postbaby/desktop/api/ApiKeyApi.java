package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import uz.postbaby.desktop.model.ApiKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ApiKeyApi(BackendClient client) {

    public List<ApiKey> list(long teamId) {
        return client.get("/teams/" + teamId + "/api-keys", new TypeReference<List<ApiKey>>() {
        });
    }

    public ApiKey create(long teamId, String name, String permissions, Integer expiresInDays) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        if (permissions != null && !permissions.isBlank()) body.put("permissions", permissions);
        if (expiresInDays != null && expiresInDays > 0) body.put("expires_in", expiresInDays);
        return client.post("/teams/" + teamId + "/api-keys", body, ApiKey.class);
    }

    public void delete(long teamId, long keyId) {
        client.delete("/teams/" + teamId + "/api-keys/" + keyId);
    }
}
