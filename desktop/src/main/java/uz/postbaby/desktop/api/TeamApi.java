package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import uz.postbaby.desktop.model.Team;
import uz.postbaby.desktop.model.TeamMember;

import java.util.List;
import java.util.Map;

public class TeamApi {

    private final BackendClient client;

    public TeamApi(BackendClient client) {
        this.client = client;
    }

    public List<Team> list() {
        return client.get("/teams", new TypeReference<List<Team>>() {
        });
    }

    public Team create(String name) {
        return client.post("/teams", Map.of("name", name), Team.class);
    }

    public Team get(long teamId) {
        return client.get("/teams/" + teamId, Team.class);
    }

    public Team update(long teamId, String name) {
        return client.put("/teams/" + teamId, Map.of("name", name), Team.class);
    }

    public void delete(long teamId) {
        client.delete("/teams/" + teamId);
    }

    public List<TeamMember> members(long teamId) {
        return client.get("/teams/" + teamId + "/members",
                new TypeReference<List<TeamMember>>() {
                });
    }

    public void removeMember(long teamId, long userId) {
        client.delete("/teams/" + teamId + "/members/" + userId);
    }

    public void leave(long teamId) {
        client.sendRaw("POST", "/teams/" + teamId + "/leave", null);
    }
}
