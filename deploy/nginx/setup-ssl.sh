#!/usr/bin/env bash
# deploy/nginx/setup-ssl.sh
# Script to install Certbot, obtain Let's Encrypt SSL certificates, and configure auto-renewal on VPS

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: sudo ./setup-ssl.sh <your-domain.com> <your-email@example.com>"
    exit 1
fi

DOMAIN=$1
EMAIL=$2

echo ">>> [1/4] Installing Nginx & Certbot..."
apt update
apt install -y nginx certbot python3-certbot-nginx

echo ">>> [2/4] Obtaining SSL certificate from Let's Encrypt for $DOMAIN..."
certbot certonly --nginx -d "$DOMAIN" -d "www.$DOMAIN" --non-interactive --agree-tos -m "$EMAIL"

echo ">>> [3/4] Linking Nginx configuration..."
sed -i "s/your-domain.com/$DOMAIN/g" /etc/nginx/sites-available/nexus-ecommerce.conf || true
ln -sf /etc/nginx/sites-available/nexus-ecommerce.conf /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

nginx -t
systemctl reload nginx

echo ">>> [4/4] Testing Certbot automatic renewal..."
certbot renew --dry-run

echo ">>> [SUCCESS] SSL Certificate successfully installed for $DOMAIN! HTTPS is active."
