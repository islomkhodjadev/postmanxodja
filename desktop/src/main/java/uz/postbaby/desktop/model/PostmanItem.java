package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostmanItem {
    public String name;
    public PostmanRequest request;
    public List<PostmanItem> item = new ArrayList<>();

    public boolean isFolder() {
        return request == null;
    }
}
