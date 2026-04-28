package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import uz.postbaby.desktop.model.TeamInvite;

import java.util.List;
import java.util.Map;

public class InviteApi {

    private final BackendClient client;

    public InviteApi(BackendClient client) {
        this.client = client;
    }

    /** Pending invites for the signed-in user. */
    public List<TeamInvite> listForUser() {
        return client.get("/invites", new TypeReference<List<TeamInvite>>() {});
    }

    /** Pending invites for a team (visible to owner). */
    public List<TeamInvite> listForTeam(long teamId) {
        return client.get("/teams/" + teamId + "/invites", new TypeReference<List<TeamInvite>>() {});
    }

    public TeamInvite create(long teamId, String email) {
        return client.post("/teams/" + teamId + "/invites",
                Map.of("email", email), TeamInvite.class);
    }

    public void accept(String token) {
        client.sendRaw("POST", "/invites/" + token + "/accept", null);
    }

    public void decline(String token) {
        client.sendRaw("POST", "/invites/" + token + "/decline", null);
    }
}
