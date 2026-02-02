# Server Setup Guide for postbaby.uz

Complete guide to set up automated deployment with GitHub Actions.

## 🎯 Overview

This setup will:
- ✅ Install all dependencies (Go, Node.js, PostgreSQL, Nginx, Certbot)
- ✅ Build and run your application
- ✅ Configure HTTPS with Let's Encrypt
- ✅ Set up systemd services for auto-restart
- ✅ Deploy automatically on every push to main branch

## 📋 Prerequisites

1. **A server** (Ubuntu 20.04+ or Debian 11+ recommended)
   - Minimum 2GB RAM, 2 CPU cores
   - 20GB disk space

2. **Domain configured**
   - `postbaby.uz` pointing to your server IP
   - `www.postbaby.uz` pointing to your server IP (optional)

3. **SSH access** to the server with sudo privileges

## 🚀 One-Time Server Setup

### Step 1: Prepare Your Server

SSH into your server:

```bash
ssh root@your-server-ip
```

Create a deploy user (recommended):

```bash
# Create user
adduser deploy
usermod -aG sudo deploy

# Setup SSH key for deploy user
su - deploy
mkdir -p ~/.ssh
chmod 700 ~/.ssh
```

### Step 2: Generate SSH Key for GitHub Actions

On your **local machine**:

```bash
# Generate SSH key pair
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/postbaby_deploy

# Copy public key to server
ssh-copy-id -i ~/.ssh/postbaby_deploy.pub deploy@your-server-ip

# Test connection
ssh -i ~/.ssh/postbaby_deploy deploy@your-server-ip
```

### Step 3: Configure GitHub Secrets

Go to your GitHub repository → Settings → Secrets and variables → Actions

Add the following secrets:

| Secret Name | Value | Description |
|------------|-------|-------------|
| `SSH_PRIVATE_KEY` | Content of `~/.ssh/postbaby_deploy` | Private SSH key |
| `SSH_HOST` | `your-server-ip` or `postbaby.uz` | Server hostname/IP |
| `SSH_USER` | `deploy` | SSH username |
| `GOOGLE_CLIENT_ID` | Your Google OAuth Client ID | From Google Console |
| `GOOGLE_CLIENT_SECRET` | Your Google OAuth Client Secret | From Google Console |
| `SMTP_HOST` | `mail.privateemail.com` | SMTP server |
| `SMTP_PORT` | `465` | SMTP port |
| `SMTP_USERNAME` | `info@postbaby.uz` | SMTP username |
| `SMTP_PASSWORD` | Your SMTP password | SMTP password |
| `SMTP_FROM` | `PostmanXodja <info@postbaby.uz>` | From address |
| `CERT_EMAIL` | `admin@postbaby.uz` | Email for SSL certificate |

**To get the private key content:**

```bash
cat ~/.ssh/postbaby_deploy
```

Copy the **entire** output including `-----BEGIN` and `-----END` lines.

### Step 4: Configure DNS

Make sure your domain points to your server:

```bash
# Check DNS
dig postbaby.uz +short
dig www.postbaby.uz +short
```

Both should return your server IP.

### Step 5: Open Firewall Ports

On your server:

```bash
# If using UFW
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable

# If using iptables
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables-save
```

## 🎬 First Deployment

### Option 1: Automatic (via GitHub Actions)

Simply push to the main branch:

```bash
git add .
git commit -m "Initial deployment setup"
git push origin main
```

GitHub Actions will automatically:
1. Connect to your server
2. Run the deployment script
3. Install all dependencies
4. Build and deploy the application
5. Configure SSL

Monitor the deployment:
- Go to GitHub → Actions tab
- Click on the running workflow

### Option 2: Manual (on the server)

SSH into your server and run:

```bash
# Clone repository
cd /tmp
git clone https://github.com/your-username/postmanxodja.git

# Run deployment script
cd postmanxodja
sudo bash deploy.sh
```

## ✅ Verify Deployment

After deployment completes:

1. **Check services are running:**

```bash
sudo systemctl status postmanxodja-backend
sudo systemctl status nginx
```

2. **Check logs:**

```bash
# Backend logs
sudo journalctl -u postmanxodja-backend -f

# Nginx logs
sudo tail -f /var/log/nginx/error.log
```

3. **Test the application:**

```bash
# Test HTTPS redirect
curl -I http://postbaby.uz

# Test frontend
curl -I https://postbaby.uz

# Test backend
curl -I https://postbaby.uz/api
```

4. **Open in browser:**
   - https://postbaby.uz

## 🔄 Continuous Deployment

Now every time you push to the `main` branch, GitHub Actions will automatically:

1. Deploy the latest code
2. Build backend and frontend
3. Restart services
4. Verify deployment

**Workflow:**

```bash
# Make changes
git add .
git commit -m "Add new feature"
git push origin main

# GitHub Actions automatically deploys!
```

## 🛠 Manual Operations

### Update Environment Variables

```bash
# Edit .env file
sudo nano /opt/postmanxodja/.env

# Restart backend
sudo systemctl restart postmanxodja-backend
```

### View Logs

```bash
# Backend logs (live)
sudo journalctl -u postmanxodja-backend -f

# Backend logs (last 100 lines)
sudo journalctl -u postmanxodja-backend -n 100

# Nginx error logs
sudo tail -f /var/log/nginx/error.log

# Nginx access logs
sudo tail -f /var/log/nginx/access.log
```

### Restart Services

```bash
# Restart backend
sudo systemctl restart postmanxodja-backend

# Restart Nginx
sudo systemctl restart nginx

# Check status
sudo systemctl status postmanxodja-backend
sudo systemctl status nginx
```

### Renew SSL Certificate

Certificates auto-renew, but to manually renew:

```bash
sudo certbot renew
sudo systemctl reload nginx
```

### Database Operations

```bash
# Connect to database
sudo -u postgres psql -d postmanxodja

# Backup database
sudo -u postgres pg_dump postmanxodja > backup_$(date +%Y%m%d).sql

# Restore database
sudo -u postgres psql postmanxodja < backup.sql
```

### Rollback Deployment

```bash
cd /opt/postmanxodja
git log --oneline  # Find previous commit
sudo git reset --hard <commit-hash>
sudo bash deploy.sh
```

## 🔍 Troubleshooting

### Backend won't start

```bash
# Check logs
sudo journalctl -u postmanxodja-backend -n 50

# Common issues:
# 1. Database connection - check DATABASE_URL in .env
# 2. Port already in use - check: sudo lsof -i :8080
# 3. Binary not executable - check: ls -la /opt/postmanxodja/backend/postmanxodja
```

### Frontend not loading

```bash
# Check if files exist
ls -la /opt/postmanxodja/frontend/dist

# Check Nginx config
sudo nginx -t

# Check Nginx logs
sudo tail -f /var/log/nginx/error.log
```

### SSL certificate issues

```bash
# Check certificate status
sudo certbot certificates

# Test renewal
sudo certbot renew --dry-run

# Force renewal
sudo certbot renew --force-renewal
sudo systemctl reload nginx
```

### GitHub Actions deployment fails

1. Check GitHub Actions logs in your repository
2. Verify all secrets are set correctly
3. Test SSH connection manually:

```bash
ssh -i ~/.ssh/postbaby_deploy deploy@your-server-ip
```

## 📊 Monitoring

### Setup monitoring (optional)

Install monitoring tools:

```bash
# Install htop for system monitoring
sudo apt install htop

# Monitor system resources
htop

# Monitor disk usage
df -h

# Monitor active connections
sudo netstat -tulpn
```

## 🔐 Security Best Practices

1. **Keep system updated:**
```bash
sudo apt update && sudo apt upgrade -y
```

2. **Configure fail2ban:**
```bash
sudo apt install fail2ban
sudo systemctl enable fail2ban
```

3. **Disable root SSH login:**
```bash
sudo nano /etc/ssh/sshd_config
# Set: PermitRootLogin no
sudo systemctl restart sshd
```

4. **Regular backups:**
```bash
# Add to crontab
0 2 * * * /usr/bin/pg_dump postmanxodja > /backup/db_$(date +\%Y\%m\%d).sql
```

## 📚 Additional Resources

- [DEPLOYMENT.md](DEPLOYMENT.md) - Detailed deployment documentation
- [README.md](README.md) - Application documentation
- [Certbot Documentation](https://certbot.eff.org/)
- [Nginx Documentation](https://nginx.org/en/docs/)

## 🆘 Support

If you encounter issues:

1. Check logs (see "View Logs" section)
2. Verify all secrets in GitHub are correct
3. Test manual deployment on the server
4. Check firewall and DNS configuration

---

**Happy Deploying! 🚀**
