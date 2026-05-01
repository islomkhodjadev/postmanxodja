package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKey {
    public long id;
    public long team_id;
    public String name;

    public String key;
    public String key_prefix;

    public String permissions;
    public String last_used_at;
    public String expires_at;
    public String created_at;
}
