# PostmanXodja

A Postman alternative built with Golang backend and React TypeScript frontend. Import and work with Postman collections, manage environments, and execute HTTP requests.

## Features

- 🔐 Multi-user support with Google OAuth authentication
- 👥 Team collaboration with workspace sharing
- 📥 Import Postman collection v2.1 JSON files
- 📤 Export collections in Postman format
- 🚀 Execute HTTP requests (GET, POST, PUT, DELETE, PATCH)
- 🌍 Environment variable management with {{variable}} syntax
- 💾 Save and manage collections in PostgreSQL
- 📊 View formatted responses with headers and body
- 📑 Tab management with auto-save
- ✉️ Email invitations for team members
- 🎨 Clean, intuitive UI

## Tech Stack

**Backend:**
- Golang
- Gin (HTTP framework)
- GORM (ORM)
- PostgreSQL

**Frontend:**
- React
- TypeScript
- Vite
- Axios

## Quick Start with Docker (Recommended)

### Prerequisites
- Docker
- Docker Compose

### Initial Setup

1. Clone the repository and navigate to the project directory

2. **Create your `.env` file** (required):

```bash
cp .env.example .env
```

Edit the `.env` file and configure your settings:
- Database credentials
- Google OAuth credentials (get from [Google Console](https://console.cloud.google.com))
- SMTP settings (for team invitations)
- Frontend URL

> ⚠️ **Security Note**: Never commit `.env` to Git. It contains secrets and is already in `.gitignore`.

### Run the Application

3. Start all services with Docker Compose:

```bash
docker-compose up -d
```

This will start:
- PostgreSQL database on port 5432
- Backend API on port 8080
- Frontend on port 3000

3. Open your browser and visit [http://localhost:3000](http://localhost:3000)

4. To stop all services:

```bash
docker-compose down
```

5. To stop and remove all data (including database):

```bash
docker-compose down -v
```

### Development Mode with Hot Reload

For development with hot reload enabled:

```bash
docker-compose -f docker-compose.dev.yml up
```

This runs:
- Backend with Go hot reload on port 8080
- Frontend with Vite dev server on port 5173
- PostgreSQL on port 5432

Access the development frontend at [http://localhost:5173](http://localhost:5173)

## Manual Setup (Without Docker)

### Prerequisites
- Go 1.21+
- Node.js 18+
- PostgreSQL

### 1. Database Setup

Create a PostgreSQL database:

```bash
createdb postmanxodja
```

Or set the `DATABASE_URL` environment variable:

```bash
export DATABASE_URL="host=localhost user=postgres password=postgres dbname=postmanxodja port=5432 sslmode=disable"
```

### 2. Backend Setup

```bash
cd backend
go mod tidy
go run main.go
```

The backend will start on `http://localhost:8080`

### 3. Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

The frontend will start on `http://localhost:5173`

## Usage

1. **Import Collection**: Click "Import Collection" and upload a Postman collection JSON file
2. **Create Environment**: In the Environments panel, create a new environment with variables (e.g., `baseUrl`, `apiKey`)
3. **Select Request**: Click on a request from the imported collection
4. **Execute Request**: Modify parameters if needed, select an environment, and click "Send"
5. **View Response**: See the response status, time, headers, and body

## API Endpoints

### Collections
- `POST /api/collections/import` - Import Postman collection
- `GET /api/collections` - List all collections
- `GET /api/collections/:id` - Get collection details
- `DELETE /api/collections/:id` - Delete collection

### Requests
- `POST /api/requests/execute` - Execute HTTP request

### Environments
- `GET /api/environments` - List environments
- `POST /api/environments` - Create environment
- `PUT /api/environments/:id` - Update environment
- `DELETE /api/environments/:id` - Delete environment

## Variable Substitution

Use `{{variableName}}` syntax in:
- URLs
- Headers
- Query parameters
- Request body

Variables are replaced with values from the selected environment.

## Project Structure

```
postmanxodja/
├── backend/
│   ├── main.go
│   ├── Dockerfile
│   ├── handlers/       # HTTP request handlers
│   ├── models/         # Data models
│   ├── services/       # Business logic
│   └── database/       # Database connection
├── frontend/
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── components/ # React components
│       ├── services/   # API client
│       └── types/      # TypeScript types
├── docker-compose.yml      # Production setup
├── docker-compose.dev.yml  # Development setup
└── README.md
```

## Docker Commands

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Rebuild services after code changes
docker-compose up -d --build

# Development mode with hot reload
docker-compose -f docker-compose.dev.yml up

# Remove all data including database
docker-compose down -v
```

## Production Deployment

### 🚀 Automated Deployment (Recommended)

**One-push deployment with GitHub Actions:**
- [QUICK_START.md](QUICK_START.md) - Get started in 3 steps
- [SETUP_SERVER.md](SETUP_SERVER.md) - Complete setup guide with troubleshooting

### 🐳 Docker Deployment

For Docker-based deployment, see [DEPLOYMENT.md](DEPLOYMENT.md).

### 📝 What's Included

- ✅ **deploy.sh** - Automated deployment script
- ✅ **GitHub Actions** - Auto-deploy on push to main
- ✅ **HTTPS/SSL** - Automatic certificate with Let's Encrypt
- ✅ **Systemd services** - Auto-restart on failure
- ✅ **Nginx** - Reverse proxy with caching

## Security

- All sensitive configuration is stored in `.env` file (not committed to Git)
- Passwords and API keys should never be hardcoded
- Use strong passwords for database in production
- Enable HTTPS in production
- Configure firewall properly (only ports 80, 443, 22 open)
- Set up proper DMARC records for email deliverability

## License

MIT
