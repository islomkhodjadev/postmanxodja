package models

// ExecuteRequest represents a request to execute
type ExecuteRequest struct {
	Method        string            `json:"method"`
	URL           string            `json:"url"`
	Headers       map[string]string `json:"headers"`
	Body          string            `json:"body"`
	QueryParams   map[string]string `json:"query_params"`
	EnvironmentID *uint             `json:"environment_id"`
}

// ExecuteResponse represents the response from executing a request
type ExecuteResponse struct {
	Status     int               `json:"status"`
	StatusText string            `json:"status_text"`
	Headers    map[string]string `json:"headers"`
	Body       string            `json:"body"`
	Time       int64             `json:"time"` // milliseconds
}
