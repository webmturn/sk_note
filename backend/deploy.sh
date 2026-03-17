#!/bin/bash
set -e

# SK-Note Backend 服务器部署脚本
# 适用于 Ubuntu 24.04 LTS

echo "========== SK-Note Backend 部署 =========="

# 1. 系统更新 + 基础工具
echo ">>> 安装基础依赖..."
apt-get update -y
apt-get install -y curl git build-essential python3 nginx

# 2. 安装 Node.js 20.x
if ! command -v node &> /dev/null; then
  echo ">>> 安装 Node.js 20..."
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi
echo "Node.js: $(node -v)"
echo "npm: $(npm -v)"

# 3. 安装 PM2
if ! command -v pm2 &> /dev/null; then
  echo ">>> 安装 PM2..."
  npm install -g pm2
fi

# 4. 创建项目目录
APP_DIR="/opt/sk-note-backend"
mkdir -p $APP_DIR/data
mkdir -p $APP_DIR/logs

# 5. 复制项目文件（假设已通过 scp/rsync 上传到 /tmp/backend）
if [ -d "/tmp/backend" ]; then
  echo ">>> 复制项目文件..."
  cp -r /tmp/backend/* $APP_DIR/
  cp /tmp/backend/.env $APP_DIR/.env 2>/dev/null || true
fi

cd $APP_DIR

# 6. 安装依赖
echo ">>> 安装 npm 依赖..."
npm install --production=false

# 7. 初始化数据库
if [ ! -f "$APP_DIR/data/sk-note.db" ]; then
  echo ">>> 初始化 SQLite 数据库..."
  node -e "
    const Database = require('better-sqlite3');
    const fs = require('fs');
    const db = new Database('$APP_DIR/data/sk-note.db');
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    const schema = fs.readFileSync('$APP_DIR/schema.sql', 'utf-8');
    db.exec(schema);
    db.close();
    console.log('数据库初始化完成');
  "
fi

# 8. 检查 .env
if [ ! -f "$APP_DIR/.env" ]; then
  echo ">>> 创建 .env 文件..."
  JWT_SECRET=$(openssl rand -base64 32)
  cat > $APP_DIR/.env <<EOF
PORT=3000
JWT_SECRET=$JWT_SECRET
DB_PATH=./data/sk-note.db
EOF
  echo "已生成 JWT_SECRET"
fi

# 9. PM2 启动
echo ">>> 启动服务..."
pm2 delete sk-note-api 2>/dev/null || true
pm2 start ecosystem.config.cjs
pm2 save
pm2 startup systemd -u root --hp /root 2>/dev/null || true

# 10. Nginx 配置
echo ">>> 配置 Nginx..."
cat > /etc/nginx/sites-available/sk-note-api <<'NGINX'
server {
    listen 80;
    server_name api.wsqh.cn;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/sk-note-api /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo ""
echo "========== 部署完成 =========="
echo "API 地址: http://$(hostname -I | awk '{print $1}'):3000"
echo "Nginx 代理: http://api.wsqh.cn"
echo ""
echo "后续步骤:"
echo "  1. 配置 DNS 将 api.wsqh.cn 指向此服务器 IP"
echo "  2. 安装 SSL: certbot --nginx -d api.wsqh.cn"
echo "  3. 查看日志: pm2 logs sk-note-api"
