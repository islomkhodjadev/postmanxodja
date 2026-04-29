package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Environment {
    public Long id;
    public String name;
    public Map<String, String> variables = new LinkedHashMap<>();
    public Long team_id;
    public String created_at;

    public boolean dirty;
    public boolean localOnly;
}
