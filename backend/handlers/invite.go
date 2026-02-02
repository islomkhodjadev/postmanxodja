package handlers

import (
	"fmt"
	"net/http"
	"time"

	"postmanxodja/database"
	"postmanxodja/models"
	"postmanxodja/services"

	"github.com/gin-gonic/gin"
)

func CreateInvite(c *gin.Context) {
	teamID := c.GetUint("team_id")
	userID := c.GetUint("user_id")

	if !services.IsTeamOwner(userID, teamID) {
		c.JSON(http.StatusForbidden, gin.H{"error": "Only team owner can invite members"})
		return
	}

	// Check if this is a Personal team (cannot invite to personal teams)
	var team models.Team
	if err := database.DB.First(&team, teamID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Team not found"})
		return
	}
	if team.Name == "Personal" {
		c.JSON(http.StatusForbidden, gin.H{"error": "Cannot invite members to Personal workspace"})
		return
	}

	var req models.InviteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Check if user is already a member
	var existingMember models.TeamMember
	if result := database.DB.Joins("JOIN users ON users.id = team_members.user_id").
		Where("team_members.team_id = ? AND users.email = ?", teamID, req.Email).
		First(&existingMember); result.Error == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "User is already a team member"})
		return
	}

	// Check if there's already a pending invite
	var existingInvite models.TeamInvite
	if result := database.DB.Where("team_id = ? AND invitee_email = ? AND status = ?", teamID, req.Email, "pending").
		First(&existingInvite); result.Error == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "Invite already sent to this email"})
		return
	}

	// Create invite
	invite := models.TeamInvite{
		TeamID:       teamID,
		InviterID:    userID,
		InviteeEmail: req.Email,
		Status:       "pending",
		Token:        services.GenerateInviteToken(),
		ExpiresAt:    time.Now().AddDate(0, 0, 7), // 7 days expiry
	}

	if err := database.DB.Create(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create invite"})
		return
	}

	// Load relationships for response
	database.DB.Preload("Team").Preload("Inviter").First(&invite, invite.ID)

	// Send invite email
	emailService := services.NewEmailService()
	if emailService.IsConfigured() {
		go func() {
			if err := emailService.SendTeamInviteEmail(
				invite.InviteeEmail,
				invite.Inviter.Name,
				invite.Team.Name,
				invite.Token,
			); err != nil {
				fmt.Println("Failed to send invite email:", err)
			}
		}()
	}

	c.JSON(http.StatusCreated, invite)
}

func GetUserInvites(c *gin.Context) {
	email := c.GetString("email")

	var invites []models.TeamInvite
	result := database.DB.Preload("Team").Preload("Inviter").
		Where("invitee_email = ? AND status = ? AND expires_at > ?", email, "pending", time.Now()).
		Find(&invites)

	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get invites"})
		return
	}

	c.JSON(http.StatusOK, invites)
}

func AcceptInvite(c *gin.Context) {
	token := c.Param("token")
	userID := c.GetUint("user_id")
	email := c.GetString("email")

	var invite models.TeamInvite
	if result := database.DB.Where("token = ? AND status = ?", token, "pending").First(&invite); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Invite not found or already used"})
		return
	}

	// Check if invite is for this user
	if invite.InviteeEmail != email {
		c.JSON(http.StatusForbidden, gin.H{"error": "This invite is not for your email"})
		return
	}

	// Check if invite is expired
	if invite.ExpiresAt.Before(time.Now()) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invite has expired"})
		return
	}

	tx := database.DB.Begin()

	// Add user to team
	member := models.TeamMember{
		TeamID: invite.TeamID,
		UserID: userID,
		Role:   "member",
	}

	if err := tx.Create(&member).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to add to team"})
		return
	}

	// Update invite status
	invite.Status = "accepted"
	if err := tx.Save(&invite).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update invite"})
		return
	}

	tx.Commit()

	// Get team details for response
	var team models.Team
	database.DB.First(&team, invite.TeamID)

	c.JSON(http.StatusOK, gin.H{"message": "Joined team successfully", "team": team})
}

func DeclineInvite(c *gin.Context) {
	token := c.Param("token")
	email := c.GetString("email")

	var invite models.TeamInvite
	if result := database.DB.Where("token = ? AND status = ?", token, "pending").First(&invite); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Invite not found or already used"})
		return
	}

	// Check if invite is for this user
	if invite.InviteeEmail != email {
		c.JSON(http.StatusForbidden, gin.H{"error": "This invite is not for your email"})
		return
	}

	invite.Status = "declined"
	database.DB.Save(&invite)

	c.JSON(http.StatusOK, gin.H{"message": "Invite declined"})
}

func GetTeamInvites(c *gin.Context) {
	teamID := c.GetUint("team_id")

	var invites []models.TeamInvite
	result := database.DB.Preload("Inviter").
		Where("team_id = ? AND status = ?", teamID, "pending").
		Find(&invites)

	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get invites"})
		return
	}

	c.JSON(http.StatusOK, invites)
}

// GetInviteByToken returns invite details for public viewing (no auth required)
func GetInviteByToken(c *gin.Context) {
	token := c.Param("token")

	var invite models.TeamInvite
	if result := database.DB.Preload("Team").Preload("Inviter").
		Where("token = ?", token).First(&invite); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Invite not found"})
		return
	}

	// Check if invite is still valid
	if invite.Status != "pending" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invite has already been " + invite.Status})
		return
	}

	if invite.ExpiresAt.Before(time.Now()) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invite has expired"})
		return
	}

	// Return limited info for public view
	c.JSON(http.StatusOK, gin.H{
		"team_name":     invite.Team.Name,
		"inviter_name":  invite.Inviter.Name,
		"invitee_email": invite.InviteeEmail,
		"expires_at":    invite.ExpiresAt,
	})
}

// AcceptInvitePublic allows accepting invite after login/register from email link
func AcceptInvitePublic(c *gin.Context) {
	token := c.Param("token")
	userID := c.GetUint("user_id")
	email := c.GetString("email")

	var invite models.TeamInvite
	if result := database.DB.Where("token = ? AND status = ?", token, "pending").First(&invite); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Invite not found or already used"})
		return
	}

	// Check if invite is for this user
	if invite.InviteeEmail != email {
		c.JSON(http.StatusForbidden, gin.H{"error": "This invite is not for your email address"})
		return
	}

	// Check if invite is expired
	if invite.ExpiresAt.Before(time.Now()) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invite has expired"})
		return
	}

	// Check if user is already a member
	var existingMember models.TeamMember
	if result := database.DB.Where("team_id = ? AND user_id = ?", invite.TeamID, userID).
		First(&existingMember); result.Error == nil {
		// Already a member, just mark invite as accepted
		invite.Status = "accepted"
		database.DB.Save(&invite)

		var team models.Team
		database.DB.First(&team, invite.TeamID)
		c.JSON(http.StatusOK, gin.H{"message": "You are already a member of this team", "team": team})
		return
	}

	tx := database.DB.Begin()

	// Add user to team
	member := models.TeamMember{
		TeamID: invite.TeamID,
		UserID: userID,
		Role:   "member",
	}

	if err := tx.Create(&member).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to add to team"})
		return
	}

	// Update invite status
	invite.Status = "accepted"
	if err := tx.Save(&invite).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update invite"})
		return
	}

	tx.Commit()

	// Get team details for response
	var team models.Team
	database.DB.First(&team, invite.TeamID)

	c.JSON(http.StatusOK, gin.H{"message": "Joined team successfully", "team": team})
}
