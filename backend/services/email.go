package services

import (
	"bytes"
	"crypto/tls"
	"fmt"
	"html/template"
	"net/smtp"
	"strings"

	"postmanxodja/config"
)

type EmailService struct {
	host     string
	port     int
	username string
	password string
	from     string
}

func NewEmailService() *EmailService {
	return &EmailService{
		host:     config.AppConfig.SMTPHost,
		port:     config.AppConfig.SMTPPort,
		username: config.AppConfig.SMTPUsername,
		password: config.AppConfig.SMTPPassword,
		from:     config.AppConfig.SMTPFrom,
	}
}

func (e *EmailService) IsConfigured() bool {
	return e.host != "" && e.username != "" && e.password != "" && e.from != ""
}

// extractEmail extracts the email address from "Display Name <email@example.com>" format
// Returns just "email@example.com" for use in SMTP commands
func extractEmail(address string) string {
	// Check if address contains < and >
	if strings.Contains(address, "<") && strings.Contains(address, ">") {
		start := strings.Index(address, "<")
		end := strings.Index(address, ">")
		if start < end {
			return strings.TrimSpace(address[start+1 : end])
		}
	}
	// If no brackets, return the address as-is
	return strings.TrimSpace(address)
}

func (e *EmailService) SendEmail(to, subject, htmlBody string) error {
	if !e.IsConfigured() {
		return fmt.Errorf("email service not configured")
	}

	auth := smtp.PlainAuth("", e.username, e.password, e.host)

	mime := "MIME-version: 1.0;\nContent-Type: text/html; charset=\"UTF-8\";\n\n"
	// Use full format (with display name) in headers
	msg := []byte(fmt.Sprintf("To: %s\r\nFrom: %s\r\nSubject: %s\r\n%s\r\n%s",
		to, e.from, subject, mime, htmlBody))

	addr := fmt.Sprintf("%s:%d", e.host, e.port)

	// Extract just the email address for SMTP commands
	fromEmail := extractEmail(e.from)

	// Port 465 requires SSL/TLS, port 587 uses STARTTLS
	if e.port == 465 {
		return e.sendMailSSL(addr, auth, fromEmail, []string{to}, msg)
	}

	// For port 587 or other ports, use standard STARTTLS
	return smtp.SendMail(addr, auth, fromEmail, []string{to}, msg)
}

// sendMailSSL sends email using SSL/TLS (for port 465)
func (e *EmailService) sendMailSSL(addr string, auth smtp.Auth, from string, to []string, msg []byte) error {
	// Create TLS connection
	tlsConfig := &tls.Config{
		ServerName: e.host,
	}

	conn, err := tls.Dial("tcp", addr, tlsConfig)
	if err != nil {
		return fmt.Errorf("failed to connect via TLS: %w", err)
	}
	defer conn.Close()

	// Create SMTP client
	client, err := smtp.NewClient(conn, e.host)
	if err != nil {
		return fmt.Errorf("failed to create SMTP client: %w", err)
	}
	defer client.Close()

	// Authenticate
	if err := client.Auth(auth); err != nil {
		return fmt.Errorf("authentication failed: %w", err)
	}

	// Set sender
	if err := client.Mail(from); err != nil {
		return fmt.Errorf("failed to set sender: %w", err)
	}

	// Set recipients
	for _, recipient := range to {
		if err := client.Rcpt(recipient); err != nil {
			return fmt.Errorf("failed to set recipient: %w", err)
		}
	}

	// Send message body
	writer, err := client.Data()
	if err != nil {
		return fmt.Errorf("failed to open data writer: %w", err)
	}

	_, err = writer.Write(msg)
	if err != nil {
		return fmt.Errorf("failed to write message: %w", err)
	}

	if err := writer.Close(); err != nil {
		return fmt.Errorf("failed to close writer: %w", err)
	}

	return client.Quit()
}

type InviteEmailData struct {
	InviterName  string
	TeamName     string
	InviteLink   string
	FrontendURL  string
}

func (e *EmailService) SendTeamInviteEmail(to, inviterName, teamName, inviteToken string) error {
	inviteLink := fmt.Sprintf("%s/invite/%s", config.AppConfig.FrontendURL, inviteToken)

	data := InviteEmailData{
		InviterName:  inviterName,
		TeamName:     teamName,
		InviteLink:   inviteLink,
		FrontendURL:  config.AppConfig.FrontendURL,
	}

	tmpl := template.Must(template.New("invite").Parse(inviteEmailTemplate))
	var body bytes.Buffer
	if err := tmpl.Execute(&body, data); err != nil {
		return err
	}

	subject := fmt.Sprintf("%s invited you to join %s on PostmanXodja", inviterName, teamName)
	return e.SendEmail(to, subject, body.String())
}

const inviteEmailTemplate = `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f3f4f6;">
    <table role="presentation" style="width: 100%; border-collapse: collapse;">
        <tr>
            <td style="padding: 40px 20px;">
                <table role="presentation" style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);">
                    <tr>
                        <td style="padding: 40px; text-align: center;">
                            <h1 style="color: #2563eb; margin: 0 0 10px 0; font-size: 28px;">PostmanXodja</h1>
                            <p style="color: #6b7280; margin: 0; font-size: 14px;">Team Collaboration Platform</p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 0 40px;">
                            <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 0;">
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 40px;">
                            <h2 style="color: #111827; margin: 0 0 20px 0; font-size: 20px;">You're invited to join a team!</h2>
                            <p style="color: #4b5563; font-size: 16px; line-height: 1.6; margin: 0 0 20px 0;">
                                <strong>{{.InviterName}}</strong> has invited you to join <strong>{{.TeamName}}</strong> on PostmanXodja.
                            </p>
                            <p style="color: #4b5563; font-size: 16px; line-height: 1.6; margin: 0 0 30px 0;">
                                Click the button below to accept the invitation and start collaborating with your team.
                            </p>
                            <table role="presentation" style="width: 100%;">
                                <tr>
                                    <td style="text-align: center;">
                                        <a href="{{.InviteLink}}" style="display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 16px;">
                                            Accept Invitation
                                        </a>
                                    </td>
                                </tr>
                            </table>
                            <p style="color: #9ca3af; font-size: 14px; margin: 30px 0 0 0; text-align: center;">
                                Or copy and paste this link into your browser:
                            </p>
                            <p style="color: #2563eb; font-size: 14px; margin: 10px 0 0 0; text-align: center; word-break: break-all;">
                                {{.InviteLink}}
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 0 40px;">
                            <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 0;">
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 30px 40px; text-align: center;">
                            <p style="color: #9ca3af; font-size: 12px; margin: 0;">
                                This invitation will expire in 7 days.<br>
                                If you didn't expect this invitation, you can safely ignore this email.
                            </p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
`
