package uz.postbaby.desktop.api;

import uz.postbaby.desktop.model.User;

public class AuthApi {

    private final BackendClient client;

    public AuthApi(BackendClient client) {
        this.client = client;
    }

    public User getCurrentUser() {
        return client.get("/auth/me", User.class);
    }

    public void logout() {
        client.sendRaw("POST", "/auth/logout", null);
    }
}
