package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostmanUrl {
    public String raw;
    public String protocol;
    public List<String> host = new ArrayList<>();
    public List<String> path = new ArrayList<>();
    public List<PostmanKeyValue> query = new ArrayList<>();
}
