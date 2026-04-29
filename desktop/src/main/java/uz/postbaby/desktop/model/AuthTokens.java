package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthTokens {
    public String access_token;
    public String refresh_token;
    public long expires_in;
    public User user;
    public long stored_at;

    public AuthTokens() {
    }

    public AuthTokens(String access, String refresh, long expiresIn, User user) {
        this.access_token = access;
        this.refresh_token = refresh;
        this.expires_in = expiresIn;
        this.user = user;
        this.stored_at = System.currentTimeMillis() / 1000;
    }
}
