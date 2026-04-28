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
    /** url is either String or PostmanUrl in Postman v2.1; we keep it as Object and read it carefully. */
    public Object url;
}
