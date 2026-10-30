@echo off
setlocal
chcp 65001 >nul
title Журнал — сервер
cd /d "%~dp0server"

where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo [Ошибка] Node.js не найден.
  echo Установите Node.js 22.5 или новее: https://nodejs.org/
  echo.
  pause
  exit /b 1
)

set "NODE_MAJOR=0"
for /f "delims=." %%v in ('node -p "process.versions.node"') do set "NODE_MAJOR=%%v"
if %NODE_MAJOR% LSS 22 (
  echo.
  echo [Ошибка] Слишком старая версия Node.js.
  echo Нужна версия 22.5 или новее. Сейчас:
  node -v
  echo.
  pause
  exit /b 1
)

if not exist "node_modules\fastify" (
  echo Устанавливаю зависимости сервера...
  call npm install
  if errorlevel 1 (
    echo.
    echo [Ошибка] Не удалось установить зависимости.
    echo.
    pause
    exit /b 1
  )
)

if not exist ".env" if exist ".env.example" copy /y ".env.example" ".env" >nul

echo.
echo  ЖУРНАЛ · запуск сервера
echo  Локальная проверка: http://localhost:8080/api/health
echo  Остановить: Ctrl+C
echo.

call npm start

echo.
echo  Сервер остановлен.
pause
