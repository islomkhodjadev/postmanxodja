package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import uz.postbaby.desktop.model.Environment;

import java.util.List;

public class EnvironmentApi {

    private final BackendClient client;

    public EnvironmentApi(BackendClient client) {
        this.client = client;
    }

    public List<Environment> list(long teamId) {
        return client.get("/teams/" + teamId + "/environments",
                new TypeReference<List<Environment>>() {
                });
    }

    public Environment create(long teamId, Environment env) {
        return client.post("/teams/" + teamId + "/environments", env, Environment.class);
    }

    public Environment update(long teamId, long id, Environment env) {
        return client.put("/teams/" + teamId + "/environments/" + id, env, Environment.class);
    }

    public void delete(long teamId, long id) {
        client.delete("/teams/" + teamId + "/environments/" + id);
    }
}
