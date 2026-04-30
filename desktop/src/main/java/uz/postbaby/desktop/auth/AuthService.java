package uz.postbaby.desktop.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.api.AuthApi;
import uz.postbaby.desktop.api.BackendClient;
import uz.postbaby.desktop.api.BackendException;
import uz.postbaby.desktop.model.AuthTokens;
import uz.postbaby.desktop.model.User;
import uz.postbaby.desktop.store.LocalStore;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Holds the current auth state in memory and persists tokens to disk.
 * Tokens travel with every BackendClient call via the configured supplier.
 */
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final LocalStore store;
    private final BackendClient backend;
    private final AuthApi authApi;

    private volatile AuthTokens tokens;

    public AuthService(LocalStore store, BackendClient backend) {
        this.store = store;
        this.backend = backend;
        this.authApi = new AuthApi(backend);
        this.tokens = store.loadTokens();
        this.backend.setTokenSupplier(() -> tokens == null ? null : tokens.access_token);
    }

    public boolean isAuthenticated() {
        return tokens != null && tokens.access_token != null;
    }

    public User user() {
        return tokens == null ? null : tokens.user;
    }

    public CompletableFuture<User> signInWithGoogle() {
        OAuthFlow flow = new OAuthFlow(backend);
        return flow.start().thenApply(this::adoptTokensAndFetchUser);
    }

    /**
     * Desktop sign-in via the loopback HTTP redirect flow. The desktop opens a
     * browser at the backend's desktop sign-in URL with a {@code return_to}
     * loopback address; the backend redirects back with tokens once the user
     * is authenticated.
     */
    public CompletableFuture<User> signInWithDesktopFlow() {
        DesktopSignInFlow flow = new DesktopSignInFlow();
        return flow.start().thenApply(this::adoptTokensAndFetchUser);
    }

    /**
     * Fallback path: the user pastes the callback URL or its query string after
     * being redirected to the web app. We extract access_token + refresh_token,
     * adopt them, and load the user. Used when the backend can't redirect to
     * the desktop loopback.
     */
    public CompletableFuture<User> signInFromCallbackInput(String input) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> q = parseCallbackInput(input);
            String access = q.get("access_token");
            String refresh = q.get("refresh_token"); // optional — desktop can run without it
            if (access == null || access.isBlank()) {
                throw new IllegalArgumentException(
                        "Paste your access token (or the full callback URL).");
            }
            long expiresIn = 3600;
            try {
                expiresIn = Long.parseLong(q.getOrDefault("expires_in", "3600"));
            } catch (Exception ignored) {
            }
            return adoptTokensAndFetchUser(new AuthTokens(access, refresh, expiresIn, null));
        });
    }

    private User adoptTokensAndFetchUser(AuthTokens t) {
        this.tokens = t;
        store.saveTokens(t);
        try {
            User u = authApi.getCurrentUser();
            t.user = u;
            store.saveTokens(t);
            return u;
        } catch (BackendException e) {
            LOG.warn("Failed to load user after sign-in: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Accepts a full URL (http(s)://, postbaby://, or any scheme://), just the
     * query string, or `key=value&...` pasted by the user.
     */
    static Map<String, String> parseCallbackInput(String input) {
        Map<String, String> out = new HashMap<>();
        if (input == null) return out;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return out;

        boolean looksLikeUrl = trimmed.contains("://");

        // Bare access token — user pasted just the token value
        if (!trimmed.contains("=") && !trimmed.contains("&")
                && !looksLikeUrl
                && !trimmed.startsWith("?") && !trimmed.startsWith("#")) {
            out.put("access_token", trimmed);
            return out;
        }

        String query = trimmed;
        try {
            if (looksLikeUrl) {
                URI uri = URI.create(trimmed);
                if (uri.getRawQuery() != null) {
                    query = uri.getRawQuery();
                } else if (uri.getRawFragment() != null) {
                    query = uri.getRawFragment();
                } else {
                    // Custom schemes (postbaby://signin?token=...) sometimes leave
                    // getRawQuery() null; fall back to splitting the SSP manually.
                    String ssp = uri.getRawSchemeSpecificPart();
                    if (ssp != null) {
                        int q = ssp.indexOf('?');
                        if (q >= 0) query = ssp.substring(q + 1);
                        else {
                            int hash = ssp.indexOf('#');
                            if (hash >= 0) query = ssp.substring(hash + 1);
                        }
                    }
                }
            } else if (trimmed.startsWith("?") || trimmed.startsWith("#")) {
                query = trimmed.substring(1);
            }
        } catch (IllegalArgumentException ignored) {
            // Treat as raw query string
        }

        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            try {
                String k = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                out.put(k, v);
            } catch (IllegalArgumentException ignored) {
                // skip malformed segments
            }
        }
        return out;
    }

    public void signOut() {
        try {
            if (tokens != null) authApi.logout();
        } catch (Exception e) {
            LOG.debug("Logout call failed (offline?): {}", e.getMessage());
        }
        tokens = null;
        store.clearTokens();
    }

    /**
     * Re-validates the cached token by hitting /auth/me. Returns null if invalid or offline.
     */
    public User refreshUser() {
        if (tokens == null) return null;
        try {
            User u = authApi.getCurrentUser();
            tokens.user = u;
            store.saveTokens(tokens);
            return u;
        } catch (BackendException e) {
            if (e.isUnauthorized()) {
                signOut();
            }
            return tokens.user;
        }
    }
}
