package models

import "time"

type Team struct {
	ID        uint         `json:"id" gorm:"primaryKey"`
	Name      string       `json:"name" gorm:"not null"`
	CreatedAt time.Time    `json:"created_at"`
	UpdatedAt time.Time    `json:"updated_at"`
	Members   []TeamMember `json:"members,omitempty" gorm:"foreignKey:TeamID"`
}

type TeamMember struct {
	ID       uint      `json:"id" gorm:"primaryKey"`
	TeamID   uint      `json:"team_id" gorm:"not null;index"`
	UserID   uint      `json:"user_id" gorm:"not null;index"`
	Role     string    `json:"role" gorm:"default:'member'"` // owner, member
	JoinedAt time.Time `json:"joined_at"`
	Team     *Team     `json:"team,omitempty" gorm:"foreignKey:TeamID"`
	User     *User     `json:"user,omitempty" gorm:"foreignKey:UserID"`
}

type TeamInvite struct {
	ID           uint      `json:"id" gorm:"primaryKey"`
	TeamID       uint      `json:"team_id" gorm:"not null;index"`
	InviterID    uint      `json:"inviter_id" gorm:"not null"`
	InviteeEmail string    `json:"invitee_email" gorm:"not null;index"`
	Status       string    `json:"status" gorm:"default:'pending'"` // pending, accepted, declined
	Token        string    `json:"token,omitempty" gorm:"uniqueIndex;not null"`
	ExpiresAt    time.Time `json:"expires_at"`
	CreatedAt    time.Time `json:"created_at"`
	Team         *Team     `json:"team,omitempty" gorm:"foreignKey:TeamID"`
	Inviter      *User     `json:"inviter,omitempty" gorm:"foreignKey:InviterID"`
}

type CreateTeamRequest struct {
	Name string `json:"name" binding:"required"`
}

type InviteRequest struct {
	Email string `json:"email" binding:"required,email"`
}
