package uz.postbaby.desktop.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecuteRequest {
    public String method = "GET";
    public String url = "";
    public Map<String, String> headers = new LinkedHashMap<>();
    public String body = "";
    public Map<String, String> query_params = new LinkedHashMap<>();
}
