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
# REMOVE: DB_NAME="postmanxodja"
# REMOVE: DB_USER="postmanxodja_user"

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
# Install Go (if needed for backend)
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
# Install and Configure PostgreSQL
#################################################
if ! command -v psql &> /dev/null; then
    log_info "Installing PostgreSQL..."
    apt-get install -y postgresql postgresql-contrib
    systemctl start postgresql
    systemctl enable postgresql
else
    log_info "PostgreSQL already installed"
fi

# Configure PostgreSQL database and user
log_info "Configuring PostgreSQL database..."

# Set default values if not provided
POSTGRES_USER=${POSTGRES_USER:-postgres}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-changeme}
POSTGRES_DB=${POSTGRES_DB:-postmanxodja}

# Check if database exists, create if not
sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -qw "$POSTGRES_DB" || {
    log_info "Creating database: $POSTGRES_DB"
    sudo -u postgres psql -c "CREATE DATABASE $POSTGRES_DB;"
}

# Create user if it doesn't exist and set password
if [ "$POSTGRES_USER" != "postgres" ]; then
    sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$POSTGRES_USER'" | grep -q 1 || {
        log_info "Creating PostgreSQL user: $POSTGRES_USER"
        sudo -u postgres psql -c "CREATE USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD';"
    }
    # Always update password in case it changed
    log_info "Updating PostgreSQL user password..."
    sudo -u postgres psql -c "ALTER USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD';"

    # Grant privileges
    log_info "Granting privileges..."
    sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE $POSTGRES_DB TO $POSTGRES_USER;"
else
    # If using default postgres user, set the password
    log_info "Setting password for postgres user..."
    sudo -u postgres psql -c "ALTER USER postgres WITH PASSWORD '$POSTGRES_PASSWORD';"
fi

log_info "PostgreSQL configured successfully"

#################################################
# Create application directory
#################################################
log_info "Setting up application directory..."
mkdir -p $APP_DIR
cd $APP_DIR

#################################################
# Copy files from /tmp
#################################################
log_info "Copying application files..."
if [ -d "/tmp/postmanxodja" ]; then
    cp -r /tmp/postmanxodja/* $APP_DIR/ 2>/dev/null || true
    cp -r /tmp/postmanxodja/.* $APP_DIR/ 2>/dev/null || true
fi

#################################################
# Create/Update .env file
#################################################
log_info "Configuring environment variables..."

cat > $APP_DIR/.env << EOF
# Application Configuration
NODE_ENV=production

# Database Configuration
POSTGRES_USER=${POSTGRES_USER:-postgres}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-}
POSTGRES_DB=${POSTGRES_DB:-postmanxodja}
POSTGRES_HOST=${POSTGRES_HOST:-localhost}
POSTGRES_PORT=5432

# Constructed DATABASE_URL (used by backend)
DATABASE_URL=host=${POSTGRES_HOST:-localhost} user=${POSTGRES_USER:-postgres} password=${POSTGRES_PASSWORD:-} dbname=${POSTGRES_DB:-postmanxodja} port=5432 sslmode=disable

# Frontend Configuration
VITE_API_URL=https://$DOMAIN/api

# Google OAuth
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
log_info "Verifying environment variables (sanitized)..."
log_info "  POSTGRES_USER: ${POSTGRES_USER:-NOT_SET}"
log_info "  POSTGRES_DB: ${POSTGRES_DB:-NOT_SET}"
log_info "  POSTGRES_HOST: ${POSTGRES_HOST:-NOT_SET}"
log_info "  POSTGRES_PASSWORD: $([ -n "$POSTGRES_PASSWORD" ] && echo "***SET***" || echo "NOT_SET")"
log_info "  GOOGLE_CLIENT_ID: $([ -n "$GOOGLE_CLIENT_ID" ] && echo "***SET***" || echo "NOT_SET")"
log_info "  SMTP_HOST: ${SMTP_HOST:-NOT_SET}"

#################################################
# Build Frontend
#################################################
log_info "Building frontend..."
cd $APP_DIR/frontend
npm install --legacy-peer-deps
npm run build

#################################################
# Build Backend (if Go backend exists)
#################################################
if [ -f "$APP_DIR/backend/main.go" ]; then
    log_info "Building backend..."
    cd $APP_DIR/backend
    export PATH=$PATH:/usr/local/go/bin
    go mod download
    go build -o postmanxodja main.go
    chmod +x postmanxodja
fi

#################################################
# Create systemd service for backend
#################################################
if [ -f "$APP_DIR/backend/postmanxodja" ]; then
    log_info "Creating systemd service..."

    cat > /etc/systemd/system/postmanxodja-backend.service << EOF
[Unit]
Description=PostmanXodja Backend Service
After=network.target

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
    chmod 600 $APP_DIR/.env 2>/dev/null || true

    # Reload and restart backend service
    systemctl daemon-reload
    systemctl enable postmanxodja-backend
    systemctl restart postmanxodja-backend
fi

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
# Final checks
#################################################
log_info "Running final checks..."

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

# Check backend status if exists
if [ -f "/etc/systemd/system/postmanxodja-backend.service" ]; then
    if systemctl is-active --quiet postmanxodja-backend; then
        log_info "✓ Backend service is running"
    else
        log_error "✗ Backend service is not running"
    fi
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
echo "  Nginx:   systemctl status nginx"
if [ -f "/etc/systemd/system/postmanxodja-backend.service" ]; then
    echo "  Backend: systemctl status postmanxodja-backend"
fi
echo ""
echo "=========================================="