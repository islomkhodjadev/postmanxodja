package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-request auth config. Mirrors the subset of the web app's Authorization
 * type that the desktop currently supports: noauth, bearer, basic, apikey.
 * <p>
 * Stored locally per tab. Not round-tripped through /tabs (the backend's
 * tab schema doesn't include auth — it's silently dropped on sync).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Authorization {

    /**
     * "noauth" | "bearer" | "basic" | "apikey"
     */
    public String type = "noauth";

    public Bearer bearer;
    public Basic basic;
    public ApiKey apikey;

    public static Authorization noauth() {
        Authorization a = new Authorization();
        a.type = "noauth";
        return a;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Bearer {
        public String token;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Basic {
        public String username;
        public String password;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiKey {
        public String key;
        public String value;
        /**
         * "header" | "query"
         */
        public String addTo = "header";
    }
}
