package uz.postbaby.desktop.util;

import uz.postbaby.desktop.model.Authorization;
import uz.postbaby.desktop.ui.KeyValueRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CurlParser {

    public static class Parsed {
        public String method = "GET";
        public String url = "";
        public final List<KeyValueRow> headers = new ArrayList<>();
        public String body = "";
        public Authorization auth;
    }

    private CurlParser() {
    }

    public static boolean looksLikeCurl(String input) {
        if (input == null) return false;
        String t = input.trim();
        if (t.length() < 6) return false;
        return t.regionMatches(true, 0, "curl ", 0, 5)
                || t.regionMatches(true, 0, "curl\n", 0, 5)
                || t.regionMatches(true, 0, "curl\t", 0, 5);
    }

    public static Parsed parse(String raw) {
        if (raw == null) return null;
        List<String> tokens = tokenize(raw);
        if (tokens.isEmpty() || !"curl".equalsIgnoreCase(tokens.get(0))) return null;

        Parsed p = new Parsed();
        StringBuilder data = new StringBuilder();
        boolean methodSet = false;
        boolean getMode = false;

        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            switch (t) {
                case "-X", "--request" -> {
                    if (i + 1 < tokens.size()) {
                        p.method = tokens.get(++i).toUpperCase();
                        methodSet = true;
                    }
                }
                case "-H", "--header" -> {
                    if (i + 1 < tokens.size()) {
                        String h = tokens.get(++i);
                        int colon = h.indexOf(':');
                        if (colon > 0) {
                            String k = h.substring(0, colon).trim();
                            String v = h.substring(colon + 1).trim();
                            p.headers.add(new KeyValueRow(k, v));
                        }
                    }
                }
                case "-d", "--data", "--data-raw", "--data-binary",
                     "--data-ascii", "--data-urlencode" -> {
                    if (i + 1 < tokens.size()) {
                        String body = tokens.get(++i);
                        if (data.length() > 0) data.append('&');
                        data.append(body);
                        if (!methodSet) p.method = "POST";
                    }
                }
                case "-G", "--get" -> {
                    getMode = true;
                    if (!methodSet) p.method = "GET";
                }
                case "-u", "--user" -> {
                    if (i + 1 < tokens.size()) {
                        String userPass = tokens.get(++i);
                        Authorization a = new Authorization();
                        a.type = "basic";
                        a.basic = new Authorization.Basic();
                        int colon = userPass.indexOf(':');
                        if (colon >= 0) {
                            a.basic.username = userPass.substring(0, colon);
                            a.basic.password = userPass.substring(colon + 1);
                        } else {
                            a.basic.username = userPass;
                        }
                        p.auth = a;
                    }
                }
                case "--url" -> {
                    if (i + 1 < tokens.size()) p.url = tokens.get(++i);
                }
                default -> {
                    if (BOOLEAN_FLAGS.contains(t)) {
                    } else if (FLAGS_WITH_VALUE.contains(t)) {
                        if (i + 1 < tokens.size()) i++;
                    } else if (t.startsWith("--") && t.contains("=")) {
                    } else if (t.startsWith("-")) {
                    } else if (p.url.isEmpty()) {
                        p.url = t;
                    }
                }
            }
        }

        if (!data.isEmpty()) {
            if (getMode) {
                p.url = appendQuery(p.url, data.toString());
                p.body = "";
            } else {
                p.body = data.toString();
            }
        }
        return p;
    }

    private static String appendQuery(String url, String query) {
        if (url == null || url.isEmpty()) return query;
        int hash = url.indexOf('#');
        String fragment = hash >= 0 ? url.substring(hash) : "";
        String head = hash >= 0 ? url.substring(0, hash) : url;
        char sep = head.contains("?") ? '&' : '?';
        return head + sep + query + fragment;
    }

    private static final Set<String> BOOLEAN_FLAGS = Set.of(
            "-L", "--location",
            "-k", "--insecure",
            "-s", "--silent",
            "-v", "--verbose",
            "-i", "--include",
            "-I", "--head",
            "-f", "--fail",
            "-O", "--remote-name",
            "-J", "--remote-header-name",
            "--compressed",
            "--http1.0", "--http1.1", "--http2", "--http2-prior-knowledge",
            "--no-buffer", "-N",
            "--tlsv1", "--tlsv1.1", "--tlsv1.2", "--tlsv1.3",
            "--ipv4", "-4", "--ipv6", "-6"
    );

    private static final Set<String> FLAGS_WITH_VALUE = Set.of(
            "-A", "--user-agent",
            "-b", "--cookie",
            "-c", "--cookie-jar",
            "-e", "--referer",
            "-o", "--output",
            "-w", "--write-out",
            "-x", "--proxy",
            "--cacert", "--cert", "--key", "--cert-type", "--key-type",
            "--max-time", "--connect-timeout",
            "--retry", "--retry-delay", "--retry-max-time",
            "--resolve", "--limit-rate",
            "--form", "-F",
            "--form-string"
    );


    static List<String> tokenize(String input) {
        // Collapse line continuations: backslash followed by newline (and optional CR)
        String s = input.replace("\\\r\n", " ").replace("\\\n", " ");
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean any = false;

        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);

            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                    i++;
                    continue;
                }
                cur.append(c);
                i++;
                continue;
            }
            if (inDouble) {
                if (c == '\\' && i + 1 < len) {
                    char n = s.charAt(i + 1);
                    switch (n) {
                        case 'n' -> cur.append('\n');
                        case 't' -> cur.append('\t');
                        case 'r' -> cur.append('\r');
                        case '\\', '"', '\'' -> cur.append(n);
                        case '$', '`' -> cur.append(n);
                        default -> {
                            cur.append('\\');
                            cur.append(n);
                        }
                    }
                    i += 2;
                } else if (c == '"') {
                    inDouble = false;
                    i++;
                } else {
                    cur.append(c);
                    i++;
                }
                continue;
            }

            if (c == '\'') {
                inSingle = true;
                any = true;
                i++;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                any = true;
                i++;
                continue;
            }
            if (c == '\\' && i + 1 < len) {
                cur.append(s.charAt(i + 1));
                i += 2;
                any = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (any) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    any = false;
                }
                i++;
                continue;
            }
            cur.append(c);
            i++;
            any = true;
        }
        if (any) out.add(cur.toString());
        return out;
    }
}
