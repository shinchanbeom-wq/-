@echo off
setlocal

echo [EndermanBoss] Paper plugin JAR build start...
echo.

gradle clean build
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
