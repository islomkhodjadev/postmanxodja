package handlers

import (
	"net/http"
	"postmanxodja/database"
	"postmanxodja/models"
	"postmanxodja/services"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
)

// CreateCollection creates a new empty collection
func CreateCollection(c *gin.Context) {
	teamID := c.GetUint("team_id")

	var req struct {
		Name        string `json:"name" binding:"required"`
		Description string `json:"description"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Create empty Postman collection format
	rawJSON := services.CreateEmptyCollection(req.Name, req.Description)

	dbCollection := models.Collection{
		Name:        req.Name,
		Description: req.Description,
		RawJSON:     rawJSON,
		TeamID:      &teamID,
	}

	if err := database.GetDB().Create(&dbCollection).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create collection"})
		return
	}

	c.JSON(http.StatusCreated, dbCollection)
}

// ImportCollection imports a Postman collection
func ImportCollection(c *gin.Context) {
	teamID := c.GetUint("team_id")

	var req struct {
		CollectionJSON string `json:"collection_json" binding:"required"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Parse collection
	collection, err := services.ParsePostmanCollection(req.CollectionJSON)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid Postman collection format"})
		return
	}

	// Extract info
	name, description := services.ExtractCollectionInfo(collection)

	// Save to database
	dbCollection := models.Collection{
		Name:        name,
		Description: description,
		RawJSON:     req.CollectionJSON,
		TeamID:      &teamID,
	}

	if err := database.GetDB().Create(&dbCollection).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save collection"})
		return
	}

	c.JSON(http.StatusOK, dbCollection)
}

// GetCollections returns all collections for a team
func GetCollections(c *gin.Context) {
	teamID := c.GetUint("team_id")

	var collections []models.Collection

	if err := database.GetDB().Where("team_id = ?", teamID).Find(&collections).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch collections"})
		return
	}

	c.JSON(http.StatusOK, collections)
}

// GetCollection returns a specific collection with full details
func GetCollection(c *gin.Context) {
	teamID := c.GetUint("team_id")
	id := c.Param("id")
	collectionID, err := strconv.ParseUint(id, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid collection ID"})
		return
	}

	var collection models.Collection
	if err := database.GetDB().Where("id = ? AND team_id = ?", collectionID, teamID).First(&collection).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Collection not found"})
		return
	}

	// Parse the raw JSON to return structured data
	parsed, err := services.ParsePostmanCollection(collection.RawJSON)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to parse collection"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"id":          collection.ID,
		"name":        collection.Name,
		"description": collection.Description,
		"team_id":     collection.TeamID,
		"created_at":  collection.CreatedAt,
		"collection":  parsed,
	})
}

// UpdateCollection updates a collection's raw JSON or name
func UpdateCollection(c *gin.Context) {
	teamID := c.GetUint("team_id")
	id := c.Param("id")
	collectionID, err := strconv.ParseUint(id, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid collection ID"})
		return
	}

	var req struct {
		RawJSON string `json:"raw_json"`
		Name    string `json:"name"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// At least one field must be provided
	if req.RawJSON == "" && req.Name == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Either raw_json or name must be provided"})
		return
	}

	// Get existing collection
	var collection models.Collection
	if err := database.GetDB().Where("id = ? AND team_id = ?", collectionID, teamID).First(&collection).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Collection not found"})
		return
	}

	// If raw_json is provided, validate and update it
	if req.RawJSON != "" {
		parsed, err := services.ParsePostmanCollection(req.RawJSON)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid collection format"})
			return
		}
		name, description := services.ExtractCollectionInfo(parsed)
		collection.RawJSON = req.RawJSON
		collection.Name = name
		collection.Description = description
	} else if req.Name != "" {
		// Update just the name - need to update both Name field and info.name in raw_json
		collection.Name = req.Name
		// Update the name in raw_json as well
		updatedRawJSON, err := services.UpdateCollectionName(collection.RawJSON, req.Name)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update collection name"})
			return
		}
		collection.RawJSON = updatedRawJSON
	}

	if err := database.GetDB().Save(&collection).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update collection"})
		return
	}

	c.JSON(http.StatusOK, collection)
}

// DeleteCollection deletes a collection
func DeleteCollection(c *gin.Context) {
	teamID := c.GetUint("team_id")
	id := c.Param("id")
	collectionID, err := strconv.ParseUint(id, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid collection ID"})
		return
	}

	result := database.GetDB().Where("id = ? AND team_id = ?", collectionID, teamID).Delete(&models.Collection{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Collection not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Collection deleted successfully"})
}

// ExportCollection exports a collection in Postman-compatible JSON format
func ExportCollection(c *gin.Context) {
	teamID := c.GetUint("team_id")
	id := c.Param("id")
	collectionID, err := strconv.ParseUint(id, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid collection ID"})
		return
	}

	var collection models.Collection
	if err := database.GetDB().Where("id = ? AND team_id = ?", collectionID, teamID).First(&collection).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Collection not found"})
		return
	}

	// Sanitize filename - remove special characters
	filename := collection.Name
	for _, char := range []string{"/", "\\", ":", "*", "?", "\"", "<", ">", "|"} {
		filename = strings.ReplaceAll(filename, char, "_")
	}

	// Set headers for file download
	c.Header("Content-Disposition", "attachment; filename=\""+filename+".postman_collection.json\"")
	c.Header("Content-Type", "application/json")
	c.String(http.StatusOK, collection.RawJSON)
}
