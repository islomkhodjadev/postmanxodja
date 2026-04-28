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

// generateSignedState creates a signed state token for CSRF protection.
// The state encodes a timestamp, random data, and an optional desktop loopback
// port (0 = web flow). Format: base64(timestamp:random:port).signature
func generateSignedState(desktopPort int) string {
	timestamp := strconv.FormatInt(time.Now().Unix(), 10)
	random := services.GenerateInviteToken()[:16]
	data := timestamp + ":" + random + ":" + strconv.Itoa(desktopPort)

	h := hmac.New(sha256.New, []byte(config.AppConfig.JWTSecret))
	h.Write([]byte(data))
	signature := base64.URLEncoding.EncodeToString(h.Sum(nil))

	return base64.URLEncoding.EncodeToString([]byte(data)) + "." + signature
}

// verifySignedState validates the signed state and returns the desktop port
// embedded in it (0 if absent / web flow). Returns ok=false if invalid.
func verifySignedState(state string) (int, bool) {
	parts := strings.Split(state, ".")
	if len(parts) != 2 {
		return 0, false
	}

	data, err := base64.URLEncoding.DecodeString(parts[0])
	if err != nil {
		return 0, false
	}

	// Verify signature
	h := hmac.New(sha256.New, []byte(config.AppConfig.JWTSecret))
	h.Write(data)
	expectedSig := base64.URLEncoding.EncodeToString(h.Sum(nil))
	if !hmac.Equal([]byte(parts[1]), []byte(expectedSig)) {
		return 0, false
	}

	// Check timestamp (allow 10 minutes)
	dataParts := strings.Split(string(data), ":")
	if len(dataParts) < 2 {
		return 0, false
	}

	timestamp, err := strconv.ParseInt(dataParts[0], 10, 64)
	if err != nil {
		return 0, false
	}

	if time.Now().Unix()-timestamp > 600 {
		return 0, false
	}

	port := 0
	if len(dataParts) >= 3 {
		if p, err := strconv.Atoi(dataParts[2]); err == nil {
			port = p
		}
	}
	return port, true
}

// GoogleLogin initiates Google OAuth flow.
// Optional ?desktop_port=NNNN routes the final redirect to http://127.0.0.1:NNNN/
// instead of FRONTEND_URL — used by the desktop app's loopback callback server.
func GoogleLogin(c *gin.Context) {
	if config.AppConfig.GoogleClientID == "" {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "Google OAuth not configured"})
		return
	}

	desktopPort := 0
	if raw := c.Query("desktop_port"); raw != "" {
		if p, err := strconv.Atoi(raw); err == nil && p > 1024 && p < 65536 {
			desktopPort = p
		}
	}

	// Generate signed state for CSRF protection (no cookies needed)
	state := generateSignedState(desktopPort)

	authURL := googleOAuthConfig.AuthCodeURL(state, oauth2.AccessTypeOffline)
	c.JSON(http.StatusOK, gin.H{"url": authURL})
}

// GoogleCallback handles the OAuth callback from Google
func GoogleCallback(c *gin.Context) {
	// Verify state using signature (no cookies needed)
	state := c.Query("state")
	desktopPort, ok := verifySignedState(state)
	if !ok {
		redirectWithError(c, "Invalid OAuth state", 0)
		return
	}

	// Get authorization code
	code := c.Query("code")
	if code == "" {
		redirectWithError(c, "No authorization code received", desktopPort)
		return
	}

	// Exchange code for token
	token, err := googleOAuthConfig.Exchange(context.Background(), code)
	if err != nil {
		redirectWithError(c, "Failed to exchange token", desktopPort)
		return
	}

	// Get user info from Google
	userInfo, err := getGoogleUserInfo(token.AccessToken)
	if err != nil {
		redirectWithError(c, "Failed to get user info", desktopPort)
		return
	}

	if !userInfo.VerifiedEmail {
		redirectWithError(c, "Email not verified with Google", desktopPort)
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
			redirectWithError(c, "Failed to create user", desktopPort)
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
		redirectWithError(c, "Failed to generate tokens", desktopPort)
		return
	}

	// Pick redirect target: desktop loopback or web frontend
	target := config.AppConfig.FrontendURL + "/auth/callback"
	if desktopPort > 0 {
		target = fmt.Sprintf("http://127.0.0.1:%d/", desktopPort)
	}
	redirectURL := fmt.Sprintf("%s?access_token=%s&refresh_token=%s&expires_in=%d",
		target,
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

func redirectWithError(c *gin.Context, errorMsg string, desktopPort int) {
	target := config.AppConfig.FrontendURL + "/auth/callback"
	if desktopPort > 0 {
		target = fmt.Sprintf("http://127.0.0.1:%d/", desktopPort)
	}
	redirectURL := target + "?error=" + url.QueryEscape(errorMsg)
	c.Redirect(http.StatusTemporaryRedirect, redirectURL)
}
