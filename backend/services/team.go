package services

import (
	"postmanxodja/database"
	"postmanxodja/models"
)

func UserBelongsToTeam(userID, teamID uint) bool {
	var count int64
	database.DB.Model(&models.TeamMember{}).
		Where("user_id = ? AND team_id = ?", userID, teamID).
		Count(&count)
	return count > 0
}

func GetUserRole(userID, teamID uint) string {
	var member models.TeamMember
	result := database.DB.Where("user_id = ? AND team_id = ?", userID, teamID).First(&member)
	if result.Error != nil {
		return ""
	}
	return member.Role
}

func IsTeamOwner(userID, teamID uint) bool {
	return GetUserRole(userID, teamID) == "owner"
}

func GetUserTeams(userID uint) ([]models.Team, error) {
	var teams []models.Team
	result := database.DB.
		Joins("JOIN team_members ON team_members.team_id = teams.id").
		Where("team_members.user_id = ?", userID).
		Find(&teams)
	return teams, result.Error
}

func CreateTeamWithOwner(name string, userID uint) (*models.Team, error) {
	tx := database.DB.Begin()

	team := &models.Team{Name: name}
	if err := tx.Create(team).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	member := &models.TeamMember{
		TeamID: team.ID,
		UserID: userID,
		Role:   "owner",
	}
	if err := tx.Create(member).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	tx.Commit()
	return team, nil
}

func CreatePersonalTeam(userID uint) (*models.Team, error) {
	return CreateTeamWithOwner("Personal", userID)
}
