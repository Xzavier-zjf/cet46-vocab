#!/usr/bin/env bash
# deploy.sh
set -euo pipefail

PROJECT_DIR="/opt/cet46-vocab"  #root 替换为你的真实服务器路径后再执行
BACKEND_DIR="$PROJECT_DIR/cet46-vocab-backend"
FRONTEND_DIR="$PROJECT_DIR/cet46-vocab-frontend"
LOG_DIR="$BACKEND_DIR/logs"

echo "[1/6] 拉取最新代码"
cd "$PROJECT_DIR"
git fetch origin main
git reset --hard origin/main

echo "[2/6] 前端构建"
cd "$FRONTEND_DIR"
npm install
npm run build

echo "[3/6] 后端打包"
cd "$BACKEND_DIR"
mvn clean package -DskipTests

echo "[4/6] 停止旧Java进程"
OLD_PID=$(pgrep -f "cet46-vocab-backend.*\\.jar" || true)
if [ -n "${OLD_PID:-}" ]; then
  kill -15 $OLD_PID || true
  sleep 3
  STILL_PID=$(pgrep -f "cet46-vocab-backend.*\\.jar" || true)
  if [ -n "${STILL_PID:-}" ]; then
    kill -9 $STILL_PID || true
  fi
fi

echo "[5/6] 启动新Jar"
mkdir -p "$LOG_DIR"
JAR_FILE=$(ls -t "$BACKEND_DIR"/target/*.jar | grep -v '\.original$' | head -n 1)
nohup java -jar "$JAR_FILE" > "$LOG_DIR/app.out.log" 2> "$LOG_DIR/app.err.log" &

echo "[6/6] 重载Nginx"
nginx -t
systemctl reload nginx

echo "部署完成"
