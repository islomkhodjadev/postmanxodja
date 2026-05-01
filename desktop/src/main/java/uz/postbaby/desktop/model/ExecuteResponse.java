package uz.postbaby.desktop.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecuteResponse {
    public int status;
    public String status_text = "";
    public Map<String, String> headers = new LinkedHashMap<>();
    public String body = "";
    public long time;
    public long size;
}
