package models

import (
	"database/sql/driver"
	"encoding/json"
	"time"
)

// Environment represents an environment with variables
type Environment struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Variables Variables `json:"variables" gorm:"type:jsonb"`
	TeamID    *uint     `json:"team_id" gorm:"index"`
	CreatedAt time.Time `json:"created_at"`
}

// Variables is a custom type for JSONB storage
type Variables map[string]string

// Scan implements sql.Scanner interface
func (v *Variables) Scan(value interface{}) error {
	if value == nil {
		*v = make(Variables)
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return nil
	}
	return json.Unmarshal(bytes, v)
}

// Value implements driver.Valuer interface
func (v Variables) Value() (driver.Value, error) {
	if v == nil {
		return json.Marshal(make(Variables))
	}
	return json.Marshal(v)
}
