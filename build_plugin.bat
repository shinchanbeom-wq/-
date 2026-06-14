@echo off
setlocal EnableExtensions

set "GRADLE_VERSION=8.14.4"
set "LOCAL_GRADLE_DIR=%CD%\.gradle-local"
set "LOCAL_GRADLE=%LOCAL_GRADLE_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
set "GRADLE_ZIP=%LOCAL_GRADLE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

echo [EndermanBoss] Paper plugin JAR build start...
echo.

where java >nul 2>nul
if not %errorlevel%==0 (
    echo [EndermanBoss] Java was not found. Install Java 21 or newer first.
    pause
    exit /b 1
)

where gradle >nul 2>nul
if %errorlevel%==0 (
    set "GRADLE_CMD=gradle"
) else (
    echo [EndermanBoss] Gradle was not found on PATH.
    echo [EndermanBoss] Downloading local Gradle %GRADLE_VERSION%...
    echo.

    if not exist "%LOCAL_GRADLE_DIR%" mkdir "%LOCAL_GRADLE_DIR%"

    if not exist "%LOCAL_GRADLE%" (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ZIP%'"
        if errorlevel 1 (
            echo.
            echo [EndermanBoss] Failed to download Gradle. Check your internet connection.
            pause
            exit /b 1
        )

        powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%LOCAL_GRADLE_DIR%' -Force"
        if errorlevel 1 (
            echo.
            echo [EndermanBoss] Failed to extract Gradle.
            pause
            exit /b 1
        )
    )

    set "GRADLE_CMD=%LOCAL_GRADLE%"
)

call "%GRADLE_CMD%" clean build
if errorlevel 1 (
    echo.
    echo [EndermanBoss] Build failed. Check the error message above.
    pause
    exit /b 1
)

echo.
echo [EndermanBoss] Build complete!
echo JAR file:
echo %CD%\build\libs\enderman-boss-paper-1.0.0.jar
echo.
echo Copy this JAR file into your Paper server's plugins folder.
pause
