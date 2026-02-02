package models

import "time"

type User struct {
	ID             uint      `json:"id" gorm:"primaryKey"`
	Email          string    `json:"email" gorm:"uniqueIndex;not null"`
	PasswordHash   string    `json:"-"`
	Name           string    `json:"name"`
	GoogleID       *string   `json:"-" gorm:"index"`
	ProfilePicture *string   `json:"profile_picture,omitempty"`
	CreatedAt      time.Time `json:"created_at"`
	UpdatedAt      time.Time `json:"updated_at"`
}

// Password is a virtual field for setting password during registration
func (u *User) SetPassword(password string) {
	u.PasswordHash = password
}

func (u *User) GetPassword() string {
	return u.PasswordHash
}

type LoginRequest struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=6"`
}

type RegisterRequest struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=6"`
	Name     string `json:"name" binding:"required"`
}

type AuthResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int64  `json:"expires_in"`
	User         User   `json:"user"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" binding:"required"`
}
