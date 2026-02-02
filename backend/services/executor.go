package services

import (
	"errors"
	"io"
	"net/http"
	"net/url"
	"postmanxodja/models"
	"strings"
	"time"
)

// ExecuteHTTPRequest executes an HTTP request and returns the response
func ExecuteHTTPRequest(req *models.ExecuteRequest) (*models.ExecuteResponse, error) {
	// Validate URL
	if req.URL == "" {
		return nil, errors.New("URL is required")
	}

	startTime := time.Now()

	// Build URL with query parameters
	fullURL := req.URL
	if len(req.QueryParams) > 0 {
		// Parse existing URL to handle query params properly
		parsedURL, err := url.Parse(fullURL)
		if err == nil {
			existingParams := parsedURL.Query()
			for key, value := range req.QueryParams {
				// Only add if not already in URL
				if existingParams.Get(key) == "" {
					existingParams.Add(key, value)
				}
			}
			parsedURL.RawQuery = existingParams.Encode()
			fullURL = parsedURL.String()
		} else {
			// Fallback to simple concatenation if URL parsing fails
			params := url.Values{}
			for key, value := range req.QueryParams {
				params.Add(key, value)
			}
			if strings.Contains(fullURL, "?") {
				fullURL += "&" + params.Encode()
			} else {
				fullURL += "?" + params.Encode()
			}
		}
	}

	// Create request
	var bodyReader io.Reader
	if req.Body != "" {
		bodyReader = strings.NewReader(req.Body)
	}

	httpReq, err := http.NewRequest(req.Method, fullURL, bodyReader)
	if err != nil {
		return nil, err
	}

	// Add headers
	for key, value := range req.Headers {
		httpReq.Header.Set(key, value)
	}

	// Execute request
	client := &http.Client{
		Timeout: 30 * time.Second,
	}
	resp, err := client.Do(httpReq)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	// Read response body
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	// Calculate elapsed time
	elapsed := time.Since(startTime).Milliseconds()

	// Build response headers map
	respHeaders := make(map[string]string)
	for key, values := range resp.Header {
		if len(values) > 0 {
			respHeaders[key] = values[0]
		}
	}

	return &models.ExecuteResponse{
		Status:     resp.StatusCode,
		StatusText: resp.Status,
		Headers:    respHeaders,
		Body:       string(bodyBytes),
		Time:       elapsed,
	}, nil
}
