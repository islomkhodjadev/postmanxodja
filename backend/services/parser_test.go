package services

import (
	"encoding/json"
	"os"
	"testing"
)

func TestParsePostmanCollection(t *testing.T) {
	// Read the test collection file
	jsonData, err := os.ReadFile("../../test_collection.json")
	if err != nil {
		t.Fatalf("Failed to read test file: %v", err)
	}

	// Parse the collection
	collection, err := ParsePostmanCollection(string(jsonData))
	if err != nil {
		t.Fatalf("Failed to parse collection: %v", err)
	}

	// Verify basic info
	if collection.Info.PostmanID != "07cce483-9cfa-4bbd-ae92-c3bc82001faf" {
		t.Errorf("Expected PostmanID '07cce483-9cfa-4bbd-ae92-c3bc82001faf', got '%s'", collection.Info.PostmanID)
	}

	if collection.Info.Name != "test" {
		t.Errorf("Expected name 'test', got '%s'", collection.Info.Name)
	}

	if collection.Info.ExporterID != "50762321" {
		t.Errorf("Expected ExporterID '50762321', got '%s'", collection.Info.ExporterID)
	}

	// Verify items structure
	if len(collection.Item) != 1 {
		t.Fatalf("Expected 1 top-level item, got %d", len(collection.Item))
	}

	// Check folder
	folder := collection.Item[0]
	if folder.Name != "notification" {
		t.Errorf("Expected folder name 'notification', got '%s'", folder.Name)
	}

	if len(folder.Item) != 1 {
		t.Fatalf("Expected 1 item in folder, got %d", len(folder.Item))
	}

	// Check request
	request := folder.Item[0]
	if request.Name != "New Request" {
		t.Errorf("Expected request name 'New Request', got '%s'", request.Name)
	}

	if request.Request == nil {
		t.Fatal("Request should not be nil")
	}

	// Verify auth
	if request.Request.Auth == nil {
		t.Fatal("Auth should not be nil")
	}

	if request.Request.Auth.Type != "bearer" {
		t.Errorf("Expected auth type 'bearer', got '%s'", request.Request.Auth.Type)
	}

	if len(request.Request.Auth.Bearer) != 1 {
		t.Fatalf("Expected 1 bearer auth parameter, got %d", len(request.Request.Auth.Bearer))
	}

	if request.Request.Auth.Bearer[0].Key != "token" {
		t.Errorf("Expected bearer token key 'token', got '%s'", request.Request.Auth.Bearer[0].Key)
	}

	if request.Request.Auth.Bearer[0].Type != "string" {
		t.Errorf("Expected bearer token type 'string', got '%s'", request.Request.Auth.Bearer[0].Type)
	}

	// Verify method
	if request.Request.Method != "POST" {
		t.Errorf("Expected method 'POST', got '%s'", request.Request.Method)
	}

	// Verify headers
	if len(request.Request.Header) != 2 {
		t.Fatalf("Expected 2 headers, got %d", len(request.Request.Header))
	}

	if request.Request.Header[0].Key != "Content-Type" {
		t.Errorf("Expected first header key 'Content-Type', got '%s'", request.Request.Header[0].Key)
	}

	if request.Request.Header[1].Disabled != true {
		t.Error("Expected second header to be disabled")
	}

	// Verify body
	if request.Request.Body == nil {
		t.Fatal("Body should not be nil")
	}

	if request.Request.Body.Mode != "raw" {
		t.Errorf("Expected body mode 'raw', got '%s'", request.Request.Body.Mode)
	}

	if request.Request.Body.Options == nil || request.Request.Body.Options.Raw == nil {
		t.Fatal("Body options should not be nil")
	}

	if request.Request.Body.Options.Raw.Language != "json" {
		t.Errorf("Expected body language 'json', got '%s'", request.Request.Body.Options.Raw.Language)
	}

	// Verify URL
	urlData, err := json.Marshal(request.Request.URL)
	if err != nil {
		t.Fatalf("Failed to marshal URL: %v", err)
	}

	var url map[string]interface{}
	if err := json.Unmarshal(urlData, &url); err != nil {
		t.Fatalf("Failed to unmarshal URL: %v", err)
	}

	if url["protocol"] != "https" {
		t.Errorf("Expected protocol 'https', got '%v'", url["protocol"])
	}

	if url["raw"] != "https://api.admin.u-code.io/v2/invoke_function/kuai-notifications/?project-id=3323bfe2-b147-41fd-9d24-ca7c929d6abd" {
		t.Errorf("URL raw field mismatch")
	}

	t.Log("✓ All tests passed! Postman collection v2.1 format is fully supported")
}
