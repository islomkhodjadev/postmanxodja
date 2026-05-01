package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostmanRequest {
    public String method = "GET";
    public List<PostmanKeyValue> header = new ArrayList<>();
    public PostmanBody body;

    public Object url;
}
