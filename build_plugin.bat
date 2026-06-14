@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set "GRADLE_VERSION=8.14.4"
set "GRADLE_DIR=%CD%\.gradle\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat"
set "GRADLE_ZIP=%CD%\.gradle\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_BIN%" (
    echo [EndermanBoss] Local Gradle not found. Downloading Gradle %GRADLE_VERSION%...
    if not exist "%CD%\.gradle" mkdir "%CD%\.gradle"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
    if errorlevel 1 goto fail

    echo [EndermanBoss] Extracting Gradle...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%GRADLE_ZIP%' '%CD%\.gradle'"
    if errorlevel 1 goto fail
)

echo [EndermanBoss] Building plugin jar...
call "%GRADLE_BIN%" clean build --no-daemon
if errorlevel 1 goto fail

echo.
echo [EndermanBoss] Build complete: build\libs\enderman-boss-1.1.0.jar
exit /b 0

:fail
echo.
echo [EndermanBoss] Build failed.
exit /b 1
