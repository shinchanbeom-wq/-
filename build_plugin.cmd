@echo off
setlocal

echo [EndermanBoss] Building Paper plugin jar...
gradle clean build
if errorlevel 1 (
    echo [EndermanBoss] Build failed.
    exit /b 1
)

echo.
echo [EndermanBoss] Done!
echo JAR path: %CD%\build\libs\enderman-boss-paper-1.0.0.jar
echo Copy that file into your Paper server plugins folder.
