package uz.postbaby.desktop.util;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;

public final class JsonStyle {

    private JsonStyle() {
    }

    public static StyleSpans<Collection<String>> compute(String json) {
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        if (json == null || json.isEmpty()) {
            spans.add(Collections.emptyList(), 0);
            return spans.create();
        }
        int len = json.length();
        int i = 0;
        int neutralRunStart = 0;

        while (i < len) {
            char c = json.charAt(i);
            int tokenStart = -1;
            int tokenLen = 0;
            String cls = null;

            if (c == '"') {
                tokenStart = i;
                int j = i + 1;
                while (j < len) {
                    char cc = json.charAt(j);
                    if (cc == '\\' && j + 1 < len) {
                        j += 2;
                        continue;
                    }
                    if (cc == '"') {
                        j++;
                        break;
                    }
                    j++;
                }
                tokenLen = j - i;
                int after = j;
                while (after < len && Character.isWhitespace(json.charAt(after))) after++;
                cls = (after < len && json.charAt(after) == ':') ? "json-key" : "json-string";
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                tokenStart = i;
                int j = i;
                if (c == '-') j++;
                while (j < len) {
                    char cc = json.charAt(j);
                    if (Character.isDigit(cc) || cc == '.' || cc == 'e' || cc == 'E'
                            || cc == '+' || cc == '-') j++;
                    else break;
                }
                tokenLen = j - i;
                cls = "json-number";
            } else if (matches(json, i, "true")) {
                tokenStart = i;
                tokenLen = 4;
                cls = "json-bool";
            } else if (matches(json, i, "false")) {
                tokenStart = i;
                tokenLen = 5;
                cls = "json-bool";
            } else if (matches(json, i, "null")) {
                tokenStart = i;
                tokenLen = 4;
                cls = "json-nul";
            } else if ("{}[],:".indexOf(c) >= 0) {
                tokenStart = i;
                tokenLen = 1;
                cls = "json-punct";
            }

            if (tokenStart >= 0) {
                if (tokenStart > neutralRunStart) {
                    spans.add(Collections.emptyList(), tokenStart - neutralRunStart);
                }
                spans.add(Collections.singletonList(cls), tokenLen);
                i = tokenStart + tokenLen;
                neutralRunStart = i;
            } else {
                i++;
            }
        }
        if (len > neutralRunStart) {
            spans.add(Collections.emptyList(), len - neutralRunStart);
        }
        return spans.create();
    }

    private static boolean matches(String s, int from, String literal) {
        if (from + literal.length() > s.length()) return false;
        for (int k = 0; k < literal.length(); k++) {
            if (s.charAt(from + k) != literal.charAt(k)) return false;
        }
        return true;
    }
}
