package uz.postbaby.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.config.AppConfig;
import uz.postbaby.desktop.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Thin HTTP wrapper around Java 21's HttpClient. Holds the access token
 * and base URL. Methods throw BackendException on non-2xx or network errors.
 */
public class BackendClient {

    private static final Logger LOG = LoggerFactory.getLogger(BackendClient.class);

    private final HttpClient http;
    private volatile Supplier<String> tokenSupplier = () -> null;

    public BackendClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setTokenSupplier(Supplier<String> supplier) {
        this.tokenSupplier = supplier == null ? () -> null : supplier;
    }

    public String baseUrl() {
        return AppConfig.apiBaseUrl();
    }

    public boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    public <T> T get(String path, Class<T> type) {
        return parse(send("GET", path, null, null), type);
    }

    public <T> T get(String path, TypeReference<T> type) {
        return parse(send("GET", path, null, null), type);
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return parse(send("POST", path, body, null), type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        return parse(send("POST", path, body, null), type);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return parse(send("PUT", path, body, null), type);
    }

    public void delete(String path) {
        send("DELETE", path, null, null);
    }

    public String sendRaw(String method, String path, Object body) {
        return send(method, path, body, null);
    }

    private String send(String method, String path, Object body, String contentTypeOverride) {
        String url = baseUrl() + path;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60));

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            String payload = body instanceof String s ? s : Json.stringify(body);
            publisher = HttpRequest.BodyPublishers.ofString(payload);
            b.header("Content-Type", contentTypeOverride != null ? contentTypeOverride : "application/json");
        }
        b.method(method, publisher);

        String token = tokenSupplier.get();
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new BackendException("Network error: " + e.getMessage(), e);
        }
        int status = resp.statusCode();
        String responseBody = resp.body();
        if (status / 100 != 2) {
            LOG.warn("HTTP {} {} -> {}: {}", method, url, status, responseBody);
            throw new BackendException(status, responseBody);
        }
        return responseBody;
    }

    private <T> T parse(String body, Class<T> type) {
        if (body == null || body.isBlank()) return null;
        if (type == String.class) return type.cast(body);
        return Json.parse(body, type);
    }

    private <T> T parse(String body, TypeReference<T> type) {
        if (body == null || body.isBlank()) return null;
        return Json.parse(body, type);
    }
}
