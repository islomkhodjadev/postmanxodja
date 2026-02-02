package handlers

import (
	"net/http"
	"strconv"

	"postmanxodja/database"
	"postmanxodja/models"
	"postmanxodja/services"

	"github.com/gin-gonic/gin"
)

func GetUserTeams(c *gin.Context) {
	userID := c.GetUint("user_id")

	teams, err := services.GetUserTeams(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get teams"})
		return
	}

	c.JSON(http.StatusOK, teams)
}

func CreateTeam(c *gin.Context) {
	userID := c.GetUint("user_id")

	var req models.CreateTeamRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	team, err := services.CreateTeamWithOwner(req.Name, userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create team"})
		return
	}

	c.JSON(http.StatusCreated, team)
}

func GetTeam(c *gin.Context) {
	teamID := c.GetUint("team_id")

	var team models.Team
	if result := database.DB.Preload("Members.User").First(&team, teamID); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Team not found"})
		return
	}

	c.JSON(http.StatusOK, team)
}

func UpdateTeam(c *gin.Context) {
	teamID := c.GetUint("team_id")
	userID := c.GetUint("user_id")

	if !services.IsTeamOwner(userID, teamID) {
		c.JSON(http.StatusForbidden, gin.H{"error": "Only team owner can update the team"})
		return
	}

	var req models.CreateTeamRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	var team models.Team
	if result := database.DB.First(&team, teamID); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Team not found"})
		return
	}

	team.Name = req.Name
	database.DB.Save(&team)

	c.JSON(http.StatusOK, team)
}

func DeleteTeam(c *gin.Context) {
	teamID := c.GetUint("team_id")
	userID := c.GetUint("user_id")

	if !services.IsTeamOwner(userID, teamID) {
		c.JSON(http.StatusForbidden, gin.H{"error": "Only team owner can delete the team"})
		return
	}

	tx := database.DB.Begin()

	// Delete team members
	if err := tx.Where("team_id = ?", teamID).Delete(&models.TeamMember{}).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete team members"})
		return
	}

	// Delete team invites
	if err := tx.Where("team_id = ?", teamID).Delete(&models.TeamInvite{}).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete team invites"})
		return
	}

	// Delete team
	if err := tx.Delete(&models.Team{}, teamID).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete team"})
		return
	}

	tx.Commit()
	c.JSON(http.StatusOK, gin.H{"message": "Team deleted successfully"})
}

func GetTeamMembers(c *gin.Context) {
	teamID := c.GetUint("team_id")

	var members []models.TeamMember
	if result := database.DB.Preload("User").Where("team_id = ?", teamID).Find(&members); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get team members"})
		return
	}

	c.JSON(http.StatusOK, members)
}

func RemoveTeamMember(c *gin.Context) {
	teamID := c.GetUint("team_id")
	userID := c.GetUint("user_id")

	if !services.IsTeamOwner(userID, teamID) {
		c.JSON(http.StatusForbidden, gin.H{"error": "Only team owner can remove members"})
		return
	}

	memberUserIDStr := c.Param("user_id")
	memberUserID, err := strconv.ParseUint(memberUserIDStr, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid user ID"})
		return
	}

	// Can't remove the owner
	if services.IsTeamOwner(uint(memberUserID), teamID) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Cannot remove team owner"})
		return
	}

	result := database.DB.Where("team_id = ? AND user_id = ?", teamID, memberUserID).Delete(&models.TeamMember{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Member not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Member removed successfully"})
}

func LeaveTeam(c *gin.Context) {
	teamID := c.GetUint("team_id")
	userID := c.GetUint("user_id")

	// Owners can't leave their team, they must transfer ownership or delete the team
	if services.IsTeamOwner(userID, teamID) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Team owner cannot leave. Transfer ownership or delete the team."})
		return
	}

	result := database.DB.Where("team_id = ? AND user_id = ?", teamID, userID).Delete(&models.TeamMember{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Membership not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Left team successfully"})
}
