#!/usr/bin/env bash
# Запуск сервера «Журнал» на Linux и macOS.
# Проверяет Node.js, при необходимости ставит зависимости и запускает сервер.
set -u

cd "$(dirname "$0")/server" 2>/dev/null || {
  echo
  echo "[Ошибка] Не найдена папка server рядом со скриптом."
  echo
  exit 1
}

if ! command -v node >/dev/null 2>&1; then
  echo
  echo "[Ошибка] Node.js не найден."
  echo "Установите Node.js 22.5 или новее: https://nodejs.org/"
  echo
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo
  echo "[Ошибка] npm не найден."
  echo "Установите Node.js 22.5 или новее: https://nodejs.org/"
  echo
  exit 1
fi

NODE_VERSION="$(node -p 'process.versions.node' 2>/dev/null || echo '0.0.0')"
NODE_MAJOR="${NODE_VERSION%%.*}"
NODE_REST="${NODE_VERSION#*.}"
NODE_MINOR="${NODE_REST%%.*}"
NODE_MAJOR="${NODE_MAJOR:-0}"
NODE_MINOR="${NODE_MINOR:-0}"

if [ "$NODE_MAJOR" -lt 22 ] || { [ "$NODE_MAJOR" -eq 22 ] && [ "$NODE_MINOR" -lt 5 ]; }; then
  echo
  echo "[Ошибка] Слишком старая версия Node.js."
  echo "Нужна версия 22.5 или новее. Сейчас: $NODE_VERSION"
  echo
  exit 1
fi

if [ ! -d node_modules/fastify ]; then
  echo "Устанавливаю зависимости сервера..."
  if ! npm install; then
    echo
    echo "[Ошибка] Не удалось установить зависимости."
    echo
    exit 1
  fi
fi

if [ ! -f .env ] && [ -f .env.example ]; then
  cp .env.example .env
fi

echo
echo " ЖУРНАЛ · запуск сервера"
echo " Локальная проверка: http://localhost:8080/api/health"
echo " Остановить: Ctrl+C"
echo

npm start
STATUS=$?

echo
echo " Сервер остановлен."
exit "$STATUS"
