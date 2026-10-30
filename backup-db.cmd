@echo off
setlocal
chcp 65001 >nul
title Журнал — резервная копия базы
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo [Ошибка] Node.js не найден.
  pause
  exit /b 1
)

node tools\backup-db.mjs
if errorlevel 1 (
  echo.
  echo [Ошибка] Не удалось создать резервную копию.
  pause
  exit /b 1
)

echo.
pause
