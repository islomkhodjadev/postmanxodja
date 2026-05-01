package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Collection {
    public Long id;
    public String name;
    public String description;
    public String raw_json;
    public Long environment_id;
    public Long team_id;
    public String created_at;

    public boolean dirty;

    public boolean localOnly;
}
