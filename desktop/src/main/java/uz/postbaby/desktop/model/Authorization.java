package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Authorization {

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
        public String addTo = "header";
    }
}
