# 🚀 Quick Start Deployment Guide

Fast track to get PostmanXodja running on postbaby.uz

## ⚡ Super Quick Start (3 Steps)

### 1️⃣ Setup GitHub Secrets

Go to GitHub → Settings → Secrets and variables → Actions

Add these secrets:

```
SSH_PRIVATE_KEY         = (Your SSH private key)
SSH_HOST                = your-server-ip
SSH_USER                = deploy
GOOGLE_CLIENT_ID        = (From Google Console)
GOOGLE_CLIENT_SECRET    = (From Google Console)
SMTP_HOST               = mail.privateemail.com
SMTP_PORT               = 465
SMTP_USERNAME           = info@postbaby.uz
SMTP_PASSWORD           = (Your email password)
SMTP_FROM               = PostmanXodja <info@postbaby.uz>
CERT_EMAIL              = admin@postbaby.uz
```

### 2️⃣ Prepare Server

SSH into your server and create deploy user:

```bash
# On server
sudo adduser deploy
sudo usermod -aG sudo deploy

# Copy your SSH key to server
ssh-copy-id deploy@your-server-ip
```

### 3️⃣ Deploy!

```bash
# On your local machine
git add .
git commit -m "Setup deployment"
git push origin main
```

That's it! GitHub Actions will automatically deploy everything. 🎉

---

## 🔧 Manual Deployment (Alternative)

If you prefer manual deployment:

```bash
# On server
cd /tmp
git clone https://github.com/your-username/postmanxodja.git
cd postmanxodja

# Run deployment script
sudo bash deploy.sh
```

---

## ✅ Verify It's Working

```bash
# Check services
sudo systemctl status postmanxodja-backend
sudo systemctl status nginx

# View logs
sudo journalctl -u postmanxodja-backend -f

# Test application
curl https://postbaby.uz
```

Open browser: **https://postbaby.uz**

---

## 🛠 Common Commands

```bash
# Restart backend
sudo systemctl restart postmanxodja-backend

# View backend logs
sudo journalctl -u postmanxodja-backend -f

# Edit environment variables
sudo nano /opt/postmanxodja/.env

# Renew SSL certificate
sudo certbot renew
```

---

## 📚 Full Documentation

- **[SETUP_SERVER.md](SETUP_SERVER.md)** - Complete setup guide with troubleshooting
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Detailed deployment documentation
- **[README.md](README.md)** - Application documentation

---

## 🆘 Quick Troubleshooting

### Backend not starting?
```bash
sudo journalctl -u postmanxodja-backend -n 50
```

### Frontend not loading?
```bash
sudo nginx -t
sudo tail -f /var/log/nginx/error.log
```

### Need to rollback?
```bash
cd /opt/postmanxodja
git log --oneline
sudo git reset --hard <previous-commit>
sudo bash deploy.sh
```

---

**Need help?** Check [SETUP_SERVER.md](SETUP_SERVER.md) for detailed troubleshooting.
