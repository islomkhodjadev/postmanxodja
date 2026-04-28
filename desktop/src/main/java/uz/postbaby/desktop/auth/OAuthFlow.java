package uz.postbaby.desktop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.api.BackendClient;
import uz.postbaby.desktop.model.AuthTokens;
import uz.postbaby.desktop.util.Json;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Performs the desktop Google OAuth flow:
 *  1. Asks the backend for the Google auth URL with desktop_port baked into state.
 *  2. Starts a tiny loopback HTTP server on 127.0.0.1:<port> to receive the redirect.
 *  3. Opens the system browser to the auth URL.
 *  4. Returns the tokens once the loopback server receives them (or an error / timeout).
 */
public class OAuthFlow {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthFlow.class);
    private static final int DEFAULT_PORT = 53682;
    private static final long TIMEOUT_SECONDS = 300;

    private final BackendClient backend;

    public OAuthFlow(BackendClient backend) {
        this.backend = backend;
    }

    public CompletableFuture<AuthTokens> start() {
        CompletableFuture<AuthTokens> future = new CompletableFuture<>();

        HttpServer server;
        int port;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", DEFAULT_PORT), 0);
            port = DEFAULT_PORT;
        } catch (IOException tryFixed) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                port = server.getAddress().getPort();
            } catch (IOException e) {
                future.completeExceptionally(new RuntimeException("Failed to start loopback server: " + e.getMessage(), e));
                return future;
            }
        }

        final HttpServer running = server;
        final int boundPort = port;

        running.createContext("/", exchange -> {
            try {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
                String error = q.get("error");
                String accessToken = q.get("access_token");
                String refreshToken = q.get("refresh_token");
                String expiresInRaw = q.get("expires_in");

                String body;
                if (error != null) {
                    body = htmlPage("Sign-in failed", "<p>" + escape(error) + "</p><p>You can close this window.</p>");
                    future.completeExceptionally(new RuntimeException(error));
                } else if (accessToken == null || refreshToken == null) {
                    body = htmlPage("Sign-in incomplete", "<p>Missing tokens in callback.</p>");
                    future.completeExceptionally(new RuntimeException("Missing tokens in callback"));
                } else {
                    long expiresIn = 3600;
                    try { expiresIn = Long.parseLong(expiresInRaw); } catch (Exception ignored) {}
                    AuthTokens tokens = new AuthTokens(accessToken, refreshToken, expiresIn, null);
                    body = htmlPage("Signed in", "<p>You can close this window and return to PostBaby.</p>");
                    future.complete(tokens);
                }

                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } finally {
                exchange.close();
                new Thread(() -> running.stop(0)).start();
            }
        });
        running.start();
        LOG.info("OAuth loopback listening on http://127.0.0.1:{}/", boundPort);

        try {
            String authUrl = fetchAuthUrl(boundPort);
            openBrowser(authUrl);
        } catch (Exception e) {
            running.stop(0);
            future.completeExceptionally(e);
            return future;
        }

        // Watchdog timeout
        future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((ok, ex) -> {
            try { running.stop(0); } catch (Exception ignored) {}
        });

        return future;
    }

    private String fetchAuthUrl(int port) {
        String body = backend.sendRaw("GET", "/auth/google?desktop_port=" + port, null);
        Map<String, Object> json = Json.parse(body, new TypeReference<Map<String, Object>>() {});
        Object url = json == null ? null : json.get("url");
        if (url == null) {
            throw new RuntimeException("Backend did not return an auth URL");
        }
        return url.toString();
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            LOG.warn("Desktop.browse failed: {}", e.getMessage());
        }
        // Fallback: shell out
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("windows")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not open browser: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String k = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String htmlPage(String title, String body) {
        return """
                <!doctype html>
                <html><head><meta charset='utf-8'><title>PostBaby — %s</title>
                <style>body{font-family:-apple-system,Segoe UI,sans-serif;background:#0b1020;color:#e7ecf3;
                display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
                .card{padding:32px;border-radius:14px;background:#121933;max-width:420px;text-align:center}
                h1{font-size:20px;margin:0 0 12px}p{margin:6px 0;color:#b9c2d4}</style>
                </head><body><div class='card'><h1>%s</h1>%s</div></body></html>
                """.formatted(escape(title), escape(title), body);
    }
}
