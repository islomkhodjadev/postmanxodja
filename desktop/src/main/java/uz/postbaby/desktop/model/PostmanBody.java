package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostmanBody {

    public String mode;
    public String raw;
    public List<PostmanKeyValue> formdata = new ArrayList<>();
    public List<PostmanKeyValue> urlencoded = new ArrayList<>();
    public Options options;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Options {
        public Raw raw;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public static class Raw {
            public String language;
        }
    }
}
