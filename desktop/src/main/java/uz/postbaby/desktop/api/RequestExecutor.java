package uz.postbaby.desktop.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.model.ExecuteRequest;
import uz.postbaby.desktop.model.ExecuteResponse;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class RequestExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestExecutor.class);

    private final HttpClient http;

    public RequestExecutor() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ExecuteResponse execute(ExecuteRequest req) {
        long start = System.currentTimeMillis();
        ExecuteResponse out = new ExecuteResponse();
        try {
            String url = appendQueryParams(req.url, req.query_params);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120));

            HttpRequest.BodyPublisher publisher;
            String method = req.method == null ? "GET" : req.method.toUpperCase();
            if (req.body != null && !req.body.isEmpty() && supportsBody(method)) {
                publisher = HttpRequest.BodyPublishers.ofString(req.body, StandardCharsets.UTF_8);
            } else {
                publisher = HttpRequest.BodyPublishers.noBody();
            }
            b.method(method, publisher);

            boolean hasContentType = false;
            if (req.headers != null) {
                for (Map.Entry<String, String> h : req.headers.entrySet()) {
                    if (h.getKey() == null || h.getKey().isBlank() || h.getValue() == null) continue;
                    if (isRestricted(h.getKey())) continue;
                    if ("content-type".equalsIgnoreCase(h.getKey())) hasContentType = true;
                    try {
                        b.header(h.getKey(), h.getValue());
                    } catch (IllegalArgumentException ex) {
                        LOG.debug("Skipping invalid header {}: {}", h.getKey(), ex.getMessage());
                    }
                }
            }
            if (!hasContentType && req.body != null && !req.body.isEmpty() && supportsBody(method)) {
                b.header("Content-Type", "application/json");
            }

            HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            out.status = resp.statusCode();
            out.status_text = reasonPhrase(resp.statusCode());
            out.headers = new LinkedHashMap<>();
            resp.headers().map().forEach((k, v) -> {
                if (!v.isEmpty()) out.headers.put(k, v.get(0));
            });
            byte[] bodyBytes = resp.body() == null ? new byte[0] : resp.body();
            out.size = bodyBytes.length;
            out.body = new String(bodyBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            out.status = 0;
            out.status_text = "Network Error";
            out.body = "Request failed: " + e.getMessage();
            out.size = 0;
        }
        out.time = System.currentTimeMillis() - start;
        return out;
    }

    private static boolean supportsBody(String method) {
        return !"GET".equals(method) && !"HEAD".equals(method);
    }

    private static String reasonPhrase(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 206 -> "Partial Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    private static boolean isRestricted(String name) {
        String n = name.toLowerCase();
        return n.equals("connection") || n.equals("content-length") || n.equals("date")
                || n.equals("expect") || n.equals("from") || n.equals("host")
                || n.equals("upgrade") || n.equals("via") || n.equals("warning");
    }

    private static String appendQueryParams(String url, Map<String, String> params) {
        if (url == null || url.isBlank()) return url;
        if (params == null || params.isEmpty()) return url;
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
