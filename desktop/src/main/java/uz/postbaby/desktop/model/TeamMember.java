package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamMember {
    public long id;
    public long team_id;
    public long user_id;
    /**
     * "owner" or "member"
     */
    public String role;
    public String joined_at;
    public User user;
}
