package middleware

import (
	"net/http"
	"strconv"
	"strings"

	"postmanxodja/services"

	"github.com/gin-gonic/gin"
)

func AuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Authorization header required"})
			return
		}

		parts := strings.Split(authHeader, " ")
		if len(parts) != 2 || parts[0] != "Bearer" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Invalid authorization format"})
			return
		}

		claims, err := services.ValidateJWT(parts[1])
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Invalid or expired token"})
			return
		}

		c.Set("user_id", claims.UserID)
		c.Set("email", claims.Email)
		c.Next()
	}
}

func TeamAccessMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.GetUint("user_id")
		teamIDStr := c.Param("team_id")

		if teamIDStr == "" {
			c.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"error": "Team ID required"})
			return
		}

		teamID, err := strconv.ParseUint(teamIDStr, 10, 32)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"error": "Invalid team ID"})
			return
		}

		if !services.UserBelongsToTeam(userID, uint(teamID)) {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "Access denied to this team"})
			return
		}

		c.Set("team_id", uint(teamID))
		c.Next()
	}
}

func TeamOwnerMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.GetUint("user_id")
		teamID := c.GetUint("team_id")

		if !services.IsTeamOwner(userID, teamID) {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "Only team owner can perform this action"})
			return
		}

		c.Next()
	}
}
