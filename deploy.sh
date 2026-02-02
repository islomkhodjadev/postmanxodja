#!/bin/bash

#################################################
# PostmanXodja Automated Deployment Script
# For Ubuntu/Debian servers
# Domain: postbaby.uz
#################################################

set -e  # Exit on any error

DOMAIN="postbaby.uz"
APP_DIR="/opt/postmanxodja"
BACKEND_PORT=8080
FRONTEND_PORT=3000
DB_NAME="postmanxodja"
DB_USER="postmanxodja_user"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

#################################################
# Check if running as root
#################################################
if [ "$EUID" -ne 0 ]; then
    log_error "Please run as root (use sudo)"
    exit 1
fi

log_info "Starting deployment for $DOMAIN..."

#################################################
# Update system
#################################################
log_info "Updating system packages..."
apt-get update
apt-get upgrade -y

#################################################
# Install PostgreSQL
#################################################
if ! command -v psql &> /dev/null; then
    log_info "Installing PostgreSQL..."
    apt-get install -y postgresql postgresql-contrib
    systemctl start postgresql
    systemctl enable postgresql
else
    log_info "PostgreSQL already installed"
fi

#################################################
# Install Go
#################################################
if ! command -v go &> /dev/null; then
    log_info "Installing Go..."
    GO_VERSION="1.21.5"
    wget "https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz"
    rm -rf /usr/local/go
    tar -C /usr/local -xzf "go${GO_VERSION}.linux-amd64.tar.gz"
    rm "go${GO_VERSION}.linux-amd64.tar.gz"

    # Add Go to PATH
    if ! grep -q "/usr/local/go/bin" /etc/profile; then
        echo "export PATH=\$PATH:/usr/local/go/bin" >> /etc/profile
    fi
    export PATH=$PATH:/usr/local/go/bin
else
    log_info "Go already installed"
fi

#################################################
# Install Node.js
#################################################
if ! command -v node &> /dev/null; then
    log_info "Installing Node.js..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
else
    log_info "Node.js already installed"
fi

#################################################
# Install Nginx
#################################################
if ! command -v nginx &> /dev/null; then
    log_info "Installing Nginx..."
    apt-get install -y nginx
    systemctl start nginx
    systemctl enable nginx
else
    log_info "Nginx already installed"
fi

#################################################
# Install Certbot
#################################################
if ! command -v certbot &> /dev/null; then
    log_info "Installing Certbot..."
    apt-get install -y certbot python3-certbot-nginx
else
    log_info "Certbot already installed"
fi

#################################################
# Setup PostgreSQL Database
#################################################
log_info "Setting up PostgreSQL database..."

# Generate random password if not exists in .env
if [ ! -f "$APP_DIR/.env" ]; then
    DB_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-25)
else
    # Read password from existing .env
    DB_PASSWORD=$(grep "POSTGRES_PASSWORD=" "$APP_DIR/.env" | cut -d '=' -f2)
    if [ -z "$DB_PASSWORD" ]; then
        DB_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-25)
    fi
fi

# Create database and user
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
    sudo -u postgres psql -c "CREATE DATABASE $DB_NAME;"

sudo -u postgres psql -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || \
    sudo -u postgres psql -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"

sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
sudo -u postgres psql -d $DB_NAME -c "GRANT ALL ON SCHEMA public TO $DB_USER;"

log_info "Database setup complete"

#################################################
# Create application directory
#################################################
log_info "Setting up application directory..."
mkdir -p $APP_DIR
cd $APP_DIR

#################################################
# Clone/Update repository
#################################################
if [ ! -d "$APP_DIR/.git" ]; then
    log_info "Cloning repository..."
    # If running from CI/CD, files are already here
    if [ -d "/tmp/postmanxodja" ]; then
        cp -r /tmp/postmanxodja/* $APP_DIR/
        cp -r /tmp/postmanxodja/.git $APP_DIR/ 2>/dev/null || true
    fi
else
    log_info "Updating repository..."
    git pull origin main
fi

#################################################
# Create/Update .env file
#################################################
log_info "Configuring environment variables..."

if [ ! -f "$APP_DIR/.env" ]; then
    log_warn ".env file not found, creating from .env.example..."
    cp .env.example .env
fi

# Update .env with production values
cat > $APP_DIR/.env << EOF
# Database Configuration
POSTGRES_USER=$DB_USER
POSTGRES_PASSWORD=$DB_PASSWORD
POSTGRES_DB=$DB_NAME
POSTGRES_HOST=localhost
POSTGRES_PORT=5432

# Backend Configuration
DATABASE_URL=host=localhost user=$DB_USER password=$DB_PASSWORD dbname=$DB_NAME port=5432 sslmode=disable

# Frontend Configuration
VITE_API_URL=https://$DOMAIN/api

# Google OAuth (you need to fill these manually or via secrets)
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID:-}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET:-}
GOOGLE_REDIRECT_URL=https://$DOMAIN/api/auth/google/callback

# Application URLs
FRONTEND_URL=https://$DOMAIN

# Email Configuration
SMTP_HOST=${SMTP_HOST:-}
SMTP_PORT=${SMTP_PORT:-587}
SMTP_FROM=${SMTP_FROM:-PostmanXodja <noreply@$DOMAIN>}
SMTP_USERNAME=${SMTP_USERNAME:-}
SMTP_PASSWORD=${SMTP_PASSWORD:-}
EOF

log_info ".env file configured"

#################################################
# Build Backend
#################################################
log_info "Building backend..."
cd $APP_DIR/backend
export PATH=$PATH:/usr/local/go/bin
go mod download
go build -o postmanxodja main.go
chmod +x postmanxodja

#################################################
# Build Frontend
#################################################
log_info "Building frontend..."
cd $APP_DIR/frontend
npm install
npm run build

#################################################
# Create systemd service for backend
#################################################
log_info "Creating systemd service..."

cat > /etc/systemd/system/postmanxodja-backend.service << EOF
[Unit]
Description=PostmanXodja Backend Service
After=network.target postgresql.service

[Service]
Type=simple
User=www-data
WorkingDirectory=$APP_DIR/backend
EnvironmentFile=$APP_DIR/.env
ExecStart=$APP_DIR/backend/postmanxodja
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Set proper permissions
chown -R www-data:www-data $APP_DIR
chmod 600 $APP_DIR/.env

# Reload and restart backend service
systemctl daemon-reload
systemctl enable postmanxodja-backend
systemctl restart postmanxodja-backend

#################################################
# Configure Nginx
#################################################
log_info "Configuring Nginx..."

cat > /etc/nginx/sites-available/postmanxodja << 'EOF'
server {
    listen 80;
    server_name postbaby.uz www.postbaby.uz;

    # Increase client body size for file uploads
    client_max_body_size 50M;

    # Frontend - serve static files
    location / {
        root /opt/postmanxodja/frontend/dist;
        try_files $uri $uri/ /index.html;

        # Cache static assets
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }

    # Backend API
    location /api {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF

# Enable site
ln -sf /etc/nginx/sites-available/postmanxodja /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

# Test nginx configuration
nginx -t

# Reload nginx
systemctl reload nginx

#################################################
# Setup SSL with Certbot
#################################################
log_info "Setting up SSL certificate..."

# Check if certificate already exists
if [ ! -d "/etc/letsencrypt/live/$DOMAIN" ]; then
    log_info "Obtaining SSL certificate..."

    # Get email from .env or use default
    CERT_EMAIL=${CERT_EMAIL:-admin@$DOMAIN}

    certbot --nginx -d $DOMAIN -d www.$DOMAIN \
        --non-interactive \
        --agree-tos \
        --email $CERT_EMAIL \
        --redirect

    log_info "SSL certificate obtained successfully"
else
    log_info "SSL certificate already exists"
    # Renew if needed
    certbot renew --quiet
fi

# Setup auto-renewal
if ! crontab -l | grep -q "certbot renew"; then
    (crontab -l 2>/dev/null; echo "0 12 * * * /usr/bin/certbot renew --quiet") | crontab -
    log_info "Certbot auto-renewal configured"
fi

#################################################
# Setup Firewall
#################################################
log_info "Configuring firewall..."
if command -v ufw &> /dev/null; then
    ufw --force enable
    ufw allow 22/tcp      # SSH
    ufw allow 80/tcp      # HTTP
    ufw allow 443/tcp     # HTTPS
    ufw status
fi

#################################################
# Final checks
#################################################
log_info "Running final checks..."

# Check backend status
if systemctl is-active --quiet postmanxodja-backend; then
    log_info "✓ Backend service is running"
else
    log_error "✗ Backend service is not running"
    systemctl status postmanxodja-backend
fi

# Check nginx status
if systemctl is-active --quiet nginx; then
    log_info "✓ Nginx is running"
else
    log_error "✗ Nginx is not running"
fi

# Check if frontend is built
if [ -d "$APP_DIR/frontend/dist" ]; then
    log_info "✓ Frontend is built"
else
    log_error "✗ Frontend build not found"
fi

#################################################
# Display summary
#################################################
echo ""
echo "=========================================="
log_info "Deployment completed successfully!"
echo "=========================================="
echo ""
echo "Application URLs:"
echo "  Frontend: https://$DOMAIN"
echo "  Backend:  https://$DOMAIN/api"
echo ""
echo "Service status:"
echo "  Backend: systemctl status postmanxodja-backend"
echo "  Nginx:   systemctl status nginx"
echo ""
echo "Logs:"
echo "  Backend: journalctl -u postmanxodja-backend -f"
echo "  Nginx:   tail -f /var/log/nginx/error.log"
echo ""
echo "Database:"
echo "  Name:     $DB_NAME"
echo "  User:     $DB_USER"
echo "  Password: (stored in $APP_DIR/.env)"
echo ""
log_warn "IMPORTANT: Update the following in .env:"
echo "  - GOOGLE_CLIENT_ID"
echo "  - GOOGLE_CLIENT_SECRET"
echo "  - SMTP_* variables for email"
echo ""
echo "After updating .env, restart backend:"
echo "  systemctl restart postmanxodja-backend"
echo "=========================================="
