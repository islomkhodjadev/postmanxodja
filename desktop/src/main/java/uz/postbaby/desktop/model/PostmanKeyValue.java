package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostmanKeyValue {
    public String key;

    public Object value;
    public String description;
    public Boolean disabled;

    public PostmanKeyValue() {
    }

    public PostmanKeyValue(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String stringValue() {
        return value == null ? "" : String.valueOf(value);
    }
}
