package config

import (
	"os"
	"strconv"
)

type Config struct {
	JWTSecret             string
	JWTExpirationHours    int
	RefreshExpirationDays int
	GoogleClientID        string
	GoogleClientSecret    string
	GoogleRedirectURL     string
	FrontendURL           string
	// Email configuration
	SMTPHost     string
	SMTPPort     int
	SMTPUsername string
	SMTPPassword string
	SMTPFrom     string
}

var AppConfig *Config

func LoadConfig() {
	AppConfig = &Config{
		JWTSecret:             getEnv("JWT_SECRET", "postmanxodja-secret-key-change-in-production"),
		JWTExpirationHours:    getEnvInt("JWT_EXPIRATION_HOURS", 24),
		RefreshExpirationDays: getEnvInt("REFRESH_EXPIRATION_DAYS", 7),
		GoogleClientID:        getEnv("GOOGLE_CLIENT_ID", ""),
		GoogleClientSecret:    getEnv("GOOGLE_CLIENT_SECRET", ""),
		GoogleRedirectURL:     getEnv("GOOGLE_REDIRECT_URL", "http://localhost:8080/api/auth/google/callback"),
		FrontendURL:           getEnv("FRONTEND_URL", "http://localhost:5173"),
		// Email configuration
		SMTPHost:     getEnv("SMTP_HOST", ""),
		SMTPPort:     getEnvInt("SMTP_PORT", 587),
		SMTPUsername: getEnv("SMTP_USERNAME", ""),
		SMTPPassword: getEnv("SMTP_PASSWORD", ""),
		SMTPFrom:     getEnv("SMTP_FROM", ""),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}
