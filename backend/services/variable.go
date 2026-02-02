package services

import (
	"log"
	"postmanxodja/models"
	"regexp"
	"strings"
)

// ReplaceVariables replaces {{variableName}} with actual values
func ReplaceVariables(text string, variables models.Variables) string {
	// Updated regex to support hyphens, underscores, dots, and other characters in variable names
	re := regexp.MustCompile(`\{\{([^}]+)\}\}`)

	log.Printf("ReplaceVariables called with text: %s", text)
	log.Printf("Available variables: %+v", variables)

	result := re.ReplaceAllStringFunc(text, func(match string) string {
		// Extract variable name without {{ }}
		varName := strings.TrimSuffix(strings.TrimPrefix(match, "{{"), "}}")

		log.Printf("Found variable placeholder: %s, extracted name: %s", match, varName)

		if value, ok := variables[varName]; ok {
			log.Printf("Replacing %s with: %s", varName, value)
			return value
		}
		log.Printf("Variable %s not found in environment, keeping original", varName)
		return match // Return original if not found
	})

	log.Printf("Result after replacement: %s", result)
	return result
}

// ReplaceInRequest replaces variables in all parts of a request
func ReplaceInRequest(req *models.ExecuteRequest, variables models.Variables) {
	// Replace in URL
	req.URL = ReplaceVariables(req.URL, variables)

	// Replace in headers
	for key, value := range req.Headers {
		req.Headers[key] = ReplaceVariables(value, variables)
	}

	// Replace in body
	req.Body = ReplaceVariables(req.Body, variables)

	// Replace in query params
	for key, value := range req.QueryParams {
		req.QueryParams[key] = ReplaceVariables(value, variables)
	}
}
