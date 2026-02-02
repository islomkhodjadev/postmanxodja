package services

import (
	"encoding/json"
	"postmanxodja/models"
)

// ParsePostmanCollection parses a Postman collection JSON string
func ParsePostmanCollection(jsonData string) (*models.PostmanCollection, error) {
	var collection models.PostmanCollection
	if err := json.Unmarshal([]byte(jsonData), &collection); err != nil {
		return nil, err
	}
	return &collection, nil
}

// ExtractCollectionInfo extracts name and description from collection
func ExtractCollectionInfo(collection *models.PostmanCollection) (string, string) {
	return collection.Info.Name, collection.Info.Description
}

// CreateEmptyCollection creates an empty Postman collection JSON
func CreateEmptyCollection(name, description string) string {
	collection := models.PostmanCollection{
		Info: models.PostmanInfo{
			Name:        name,
			Description: description,
			Schema:      "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
		},
		Item: []models.PostmanItem{},
	}

	jsonData, _ := json.Marshal(collection)
	return string(jsonData)
}
