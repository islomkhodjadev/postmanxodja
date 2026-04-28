package uz.postbaby.desktop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamInvite {
    public long id;
    public long team_id;
    public String invitee_email;
    /** "pending" | "accepted" | "declined" */
    public String status;
    public String expires_at;
    public String created_at;
    public Team team;
    public User inviter;
    public String token;
}
