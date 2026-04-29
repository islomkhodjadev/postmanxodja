package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostmanCollection {
    public Info info = new Info();
    public List<PostmanItem> item = new ArrayList<>();
    public List<PostmanVariable> variable = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Info {
        public String _postman_id;
        public String name;
        public String description;
        public String schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
    }
}
