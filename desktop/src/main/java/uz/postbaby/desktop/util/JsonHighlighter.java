package uz.postbaby.desktop.util;

import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Renders a JSON string as syntax-highlighted HTML for display inside a WebView.
 * Falls back to escaped plain text when the input isn't valid JSON.
 */
public final class JsonHighlighter {

    public enum Theme {DARK, LIGHT}

    private JsonHighlighter() {
    }

    public static String toHtml(String body, Theme theme) {
        String pretty = tryPretty(body);
        Palette p = theme == Theme.LIGHT ? Palette.light() : Palette.dark();
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!doctype html><html><head><meta charset='utf-8'><style>");
        // No height:100% — let content drive the body height so the WebView's
        // own scrollbar appears when the response is taller than the viewport.
        sb.append("html,body{margin:0;padding:0;background:").append(p.bg).append(";}");
        sb.append("body{color:").append(p.text).append(";")
                .append("font-family:Menlo,Consolas,'SF Mono','Courier New',monospace;font-size:12px;}");
        sb.append("pre{margin:0;padding:12px;white-space:pre-wrap;word-break:break-word;tab-size:2;}");
        sb.append("::-webkit-scrollbar{width:10px;height:10px;}");
        sb.append("::-webkit-scrollbar-track{background:transparent;}");
        sb.append("::-webkit-scrollbar-thumb{background:").append(p.punct).append(";border-radius:6px;}");
        sb.append("::-webkit-scrollbar-thumb:hover{background:").append(p.bool).append(";}");
        sb.append("::selection{background:").append(p.selection).append(";color:#fff;}");
        sb.append(".key{color:").append(p.key).append(";}");
        sb.append(".str{color:").append(p.string).append(";}");
        sb.append(".num{color:").append(p.number).append(";}");
        sb.append(".bool{color:").append(p.bool).append(";font-weight:bold;}");
        sb.append(".nul{color:").append(p.nul).append(";font-weight:bold;}");
        sb.append(".punct{color:").append(p.punct).append(";}");
        sb.append("</style></head><body><pre>");
        sb.append(highlight(pretty));
        sb.append("</pre></body></html>");
        return sb.toString();
    }

    private static String tryPretty(String body) {
        if (body == null) return "";
        String t = body.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                Object node = Json.MAPPER.readValue(t, Object.class);
                return Json.MAPPER.copy()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(node);
            } catch (Exception ignored) {
            }
        }
        return body;
    }

    private static String highlight(String json) {
        if (json == null || json.isEmpty()) return "";
        StringBuilder out = new StringBuilder(json.length() + 256);
        int len = json.length();
        int i = 0;
        while (i < len) {
            char c = json.charAt(i);
            if (c == '"') {
                int start = i;
                i++;
                while (i < len) {
                    char cc = json.charAt(i);
                    if (cc == '\\' && i + 1 < len) {
                        i += 2;
                        continue;
                    }
                    if (cc == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                String literal = json.substring(start, i);
                int j = i;
                while (j < len && Character.isWhitespace(json.charAt(j))) j++;
                boolean isKey = j < len && json.charAt(j) == ':';
                out.append("<span class='").append(isKey ? "key" : "str").append("'>");
                out.append(escape(literal));
                out.append("</span>");
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                int start = i;
                if (c == '-') i++;
                while (i < len) {
                    char cc = json.charAt(i);
                    if (Character.isDigit(cc) || cc == '.' || cc == 'e' || cc == 'E'
                            || cc == '+' || cc == '-') i++;
                    else break;
                }
                out.append("<span class='num'>")
                        .append(escape(json.substring(start, i)))
                        .append("</span>");
            } else if (matches(json, i, "true")) {
                out.append("<span class='bool'>true</span>");
                i += 4;
            } else if (matches(json, i, "false")) {
                out.append("<span class='bool'>false</span>");
                i += 5;
            } else if (matches(json, i, "null")) {
                out.append("<span class='nul'>null</span>");
                i += 4;
            } else if ("{}[],:".indexOf(c) >= 0) {
                out.append("<span class='punct'>").append(c).append("</span>");
                i++;
            } else {
                out.append(escape(String.valueOf(c)));
                i++;
            }
        }
        return out.toString();
    }

    private static boolean matches(String s, int from, String literal) {
        if (from + literal.length() > s.length()) return false;
        for (int k = 0; k < literal.length(); k++) {
            if (s.charAt(from + k) != literal.charAt(k)) return false;
        }
        // not part of a longer identifier — JSON has no identifiers, so this is sufficient
        return true;
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char ch = s.charAt(k);
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }

    private record Palette(String bg, String text, String key, String string,
                           String number, String bool, String nul, String punct,
                           String selection) {
        static Palette dark() {
            return new Palette(
                    "#1c1c1f", "#d8d8da",
                    "#82aaff",   // keys — soft blue
                    "#a5e075",   // strings — green
                    "#f78c6c",   // numbers — orange
                    "#c792ea",   // booleans — purple
                    "#ff5370",   // null — red/pink
                    "#888a90",   // punctuation
                    "#ff6c37"    // selection — Postman orange
            );
        }

        static Palette light() {
            return new Palette(
                    "#ffffff", "#1f2328",
                    "#0550ae",   // keys — deep blue
                    "#0a8754",   // strings — green
                    "#cf222e",   // numbers — red
                    "#8250df",   // booleans — purple
                    "#a8071a",   // null — dark red
                    "#6b7280",   // punctuation
                    "#ff6c37"    // selection
            );
        }
    }
}
