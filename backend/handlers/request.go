package handlers

import (
	"log"
	"net/http"
	"postmanxodja/database"
	"postmanxodja/models"
	"postmanxodja/services"

	"github.com/gin-gonic/gin"
)

// ExecuteRequest executes an HTTP request with variable substitution
func ExecuteRequest(c *gin.Context) {
	var req models.ExecuteRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("Failed to bind JSON: %v", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	log.Printf("Executing request: %s %s", req.Method, req.URL)

	// Get environment variables if environment ID is provided
	var variables models.Variables
	if req.EnvironmentID != nil {
		var env models.Environment
		if err := database.GetDB().First(&env, *req.EnvironmentID).Error; err == nil {
			variables = env.Variables
			log.Printf("Loaded %d variables from environment: %s", len(variables), env.Name)
		} else {
			log.Printf("Failed to load environment ID %d: %v", *req.EnvironmentID, err)
		}
	}

	// Replace variables in request
	if len(variables) > 0 {
		log.Printf("Replacing variables in request. URL before: %s", req.URL)
		services.ReplaceInRequest(&req, variables)
		log.Printf("URL after variable replacement: %s", req.URL)
	}

	// Execute the request
	response, err := services.ExecuteHTTPRequest(&req)
	log.Default().Print(response, "heeeeeereee reponse")
	if err != nil {
		log.Printf("Request execution failed: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, response)
}
