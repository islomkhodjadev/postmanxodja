package handlers

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"postmanxodja/config"
	"postmanxodja/database"
	"postmanxodja/models"
	"postmanxodja/services"

	"github.com/gin-gonic/gin"
	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

var googleOAuthConfig *oauth2.Config

func InitOAuth() {
	googleOAuthConfig = &oauth2.Config{
		ClientID:     config.AppConfig.GoogleClientID,
		ClientSecret: config.AppConfig.GoogleClientSecret,
		RedirectURL:  config.AppConfig.GoogleRedirectURL,
		Scopes: []string{
			"https://www.googleapis.com/auth/userinfo.email",
			"https://www.googleapis.com/auth/userinfo.profile",
		},
		Endpoint: google.Endpoint,
	}
}

type GoogleUserInfo struct {
	ID            string `json:"id"`
	Email         string `json:"email"`
	VerifiedEmail bool   `json:"verified_email"`
	Name          string `json:"name"`
	Picture       string `json:"picture"`
}

// generateSignedState creates a signed state token for CSRF protection
// The state contains a timestamp and random data, signed with HMAC
func generateSignedState() string {
	timestamp := strconv.FormatInt(time.Now().Unix(), 10)
	random := services.GenerateInviteToken()[:16]
	data := timestamp + ":" + random

	h := hmac.New(sha256.New, []byte(config.AppConfig.JWTSecret))
	h.Write([]byte(data))
	signature := base64.URLEncoding.EncodeToString(h.Sum(nil))

	return base64.URLEncoding.EncodeToString([]byte(data)) + "." + signature
}

// verifySignedState verifies the signed state token
func verifySignedState(state string) bool {
	parts := strings.Split(state, ".")
	if len(parts) != 2 {
		return false
	}

	data, err := base64.URLEncoding.DecodeString(parts[0])
	if err != nil {
		return false
	}

	// Verify signature
	h := hmac.New(sha256.New, []byte(config.AppConfig.JWTSecret))
	h.Write(data)
	expectedSig := base64.URLEncoding.EncodeToString(h.Sum(nil))
	if !hmac.Equal([]byte(parts[1]), []byte(expectedSig)) {
		return false
	}

	// Check timestamp (allow 10 minutes)
	dataParts := strings.Split(string(data), ":")
	if len(dataParts) != 2 {
		return false
	}

	timestamp, err := strconv.ParseInt(dataParts[0], 10, 64)
	if err != nil {
		return false
	}

	if time.Now().Unix()-timestamp > 600 {
		return false
	}

	return true
}

// GoogleLogin initiates Google OAuth flow
func GoogleLogin(c *gin.Context) {
	if config.AppConfig.GoogleClientID == "" {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "Google OAuth not configured"})
		return
	}

	// Generate signed state for CSRF protection (no cookies needed)
	state := generateSignedState()

	authURL := googleOAuthConfig.AuthCodeURL(state, oauth2.AccessTypeOffline)
	c.JSON(http.StatusOK, gin.H{"url": authURL})
}

// GoogleCallback handles the OAuth callback from Google
func GoogleCallback(c *gin.Context) {
	// Verify state using signature (no cookies needed)
	state := c.Query("state")
	if !verifySignedState(state) {
		redirectWithError(c, "Invalid OAuth state")
		return
	}

	// Get authorization code
	code := c.Query("code")
	if code == "" {
		redirectWithError(c, "No authorization code received")
		return
	}

	// Exchange code for token
	token, err := googleOAuthConfig.Exchange(context.Background(), code)
	if err != nil {
		redirectWithError(c, "Failed to exchange token")
		return
	}

	// Get user info from Google
	userInfo, err := getGoogleUserInfo(token.AccessToken)
	if err != nil {
		redirectWithError(c, "Failed to get user info")
		return
	}

	if !userInfo.VerifiedEmail {
		redirectWithError(c, "Email not verified with Google")
		return
	}

	// Find or create user
	var user models.User
	result := database.DB.Where("email = ?", userInfo.Email).First(&user)

	if result.Error != nil {
		// Create new user (no password for OAuth users)
		user = models.User{
			Email:          userInfo.Email,
			Name:           userInfo.Name,
			PasswordHash:   "",
			GoogleID:       &userInfo.ID,
			ProfilePicture: &userInfo.Picture,
		}

		if err := database.DB.Create(&user).Error; err != nil {
			redirectWithError(c, "Failed to create user")
			return
		}

		// Create personal team for new user
		if _, err := services.CreateTeamWithOwner("Personal", user.ID); err != nil {
			// Log but don't fail - user can create team later
			fmt.Println("Failed to create personal team:", err.Error())
		}
	} else {
		// Update Google info if not set
		if user.GoogleID == nil {
			user.GoogleID = &userInfo.ID
			user.ProfilePicture = &userInfo.Picture
			database.DB.Save(&user)
		}
	}

	// Generate JWT tokens
	authResponse, err := services.GenerateTokenPair(&user)
	if err != nil {
		redirectWithError(c, "Failed to generate tokens")
		return
	}

	// Redirect to frontend with tokens
	redirectURL := fmt.Sprintf("%s/auth/callback?access_token=%s&refresh_token=%s&expires_in=%d",
		config.AppConfig.FrontendURL,
		url.QueryEscape(authResponse.AccessToken),
		url.QueryEscape(authResponse.RefreshToken),
		authResponse.ExpiresIn,
	)

	c.Redirect(http.StatusTemporaryRedirect, redirectURL)
}

func getGoogleUserInfo(accessToken string) (*GoogleUserInfo, error) {
	resp, err := http.Get("https://www.googleapis.com/oauth2/v2/userinfo?access_token=" + accessToken)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var userInfo GoogleUserInfo
	if err := json.Unmarshal(body, &userInfo); err != nil {
		return nil, err
	}

	return &userInfo, nil
}

func redirectWithError(c *gin.Context, errorMsg string) {
	redirectURL := config.AppConfig.FrontendURL + "/auth/callback?error=" + url.QueryEscape(errorMsg)
	c.Redirect(http.StatusTemporaryRedirect, redirectURL)
}
