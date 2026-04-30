package uz.postbaby.desktop.auth;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.config.AppConfig;
import uz.postbaby.desktop.model.AuthTokens;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Desktop sign-in flow using a loopback HTTP redirect.
 *
 * <ol>
 *   <li>Spin up a tiny HTTP server on 127.0.0.1:&lt;port&gt;.</li>
 *   <li>Open the browser to {@code ${apiBase}/auth/desktop?return_to=http://127.0.0.1:&lt;port&gt;/}.</li>
 *   <li>Backend authenticates the user (its existing OAuth session) and
 *       redirects the browser to {@code return_to} with
 *       {@code access_token}, {@code refresh_token}, {@code expires_in} as
 *       query params.</li>
 *   <li>The loopback handler captures the tokens and completes the future.</li>
 * </ol>
 *
 * Used instead of a {@code postbaby://} deep link because URL Apple Events
 * aren't reliably delivered to JavaFX apps on macOS.
 */
public class DesktopSignInFlow {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopSignInFlow.class);
    private static final int DEFAULT_PORT = 53683;
    private static final long TIMEOUT_SECONDS = 300;

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
                future.completeExceptionally(new RuntimeException(
                        "Failed to start loopback server: " + e.getMessage(), e));
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
                    body = htmlPage("Sign-in failed", "<p>" + escape(error)
                            + "</p><p>You can close this window.</p>");
                    future.completeExceptionally(new RuntimeException(error));
                } else if (accessToken == null) {
                    body = htmlPage("Sign-in incomplete",
                            "<p>Missing <code>access_token</code> in callback.</p>");
                    future.completeExceptionally(new RuntimeException("Missing access_token in callback"));
                } else {
                    long expiresIn = 3600;
                    try {
                        if (expiresInRaw != null) expiresIn = Long.parseLong(expiresInRaw);
                    } catch (Exception ignored) {
                    }
                    AuthTokens tokens = new AuthTokens(accessToken, refreshToken, expiresIn, null);
                    body = htmlPage("Signed in",
                            "<p>You can close this window and return to PostBaby.</p>");
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
        LOG.info("Desktop sign-in loopback listening on http://127.0.0.1:{}/", boundPort);

        try {
            openBrowser(buildSignInUrl(boundPort));
        } catch (Exception e) {
            running.stop(0);
            future.completeExceptionally(e);
            return future;
        }

        future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((ok, ex) -> {
            try {
                running.stop(0);
            } catch (Exception ignored) {
            }
        });

        return future;
    }

    private String buildSignInUrl(int port) {
        String base = AppConfig.desktopSignInUrl();
        String returnTo = "http://127.0.0.1:" + port + "/";
        String encoded = URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "return_to=" + encoded;
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
