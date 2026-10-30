@echo off
setlocal
chcp 65001 >nul
title Журнал — сборка APK

set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%tools\jdk17"
set "ANDROID_HOME=%ROOT%tools\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [Ошибка] Не найден JDK: %JAVA_HOME%
  echo Запустите tools\setup-sdk.ps1
  pause
  exit /b 1
)

if not exist "%ANDROID_HOME%\platforms\android-35" (
  echo [Ошибка] Не найден Android SDK 35: %ANDROID_HOME%
  echo Запустите tools\setup-sdk.ps1
  pause
  exit /b 1
)

cd /d "%ROOT%android"

echo.
echo  Сборка debug APK...
echo.

call gradlew.bat assembleDebug --console=plain
if errorlevel 1 (
  echo.
  echo [Ошибка] Сборка не удалась. Смотрите сообщения выше.
  echo.
  pause
  exit /b 1
)

copy /y "%ROOT%android\app\build\outputs\apk\debug\app-debug.apk" "%ROOT%zhurnal-debug.apk" >nul
if errorlevel 1 (
  echo [Ошибка] APK собран, но не удалось скопировать его в корень проекта.
  pause
  exit /b 1
)

where node >nul 2>nul
if errorlevel 1 (
  echo [Предупреждение] Node.js не найден — обновление для сервера не опубликовано.
) else (
  node "%ROOT%tools\publish-update.mjs"
  if errorlevel 1 (
    echo [Предупреждение] APK собран, но опубликовать обновление на сервере не удалось.
  )
)

echo.
echo  Готово.
echo  APK: %ROOT%zhurnal-debug.apk
echo  Также: android\app\build\outputs\apk\debug\app-debug.apk
echo. 
pause
