@echo off
setlocal enabledelayedexpansion

echo [1/3] Building app EXE...
call build_exe.bat
if errorlevel 1 (
  echo [ERROR] EXE build failed.
  exit /b 1
)

echo [2/3] Checking Inno Setup...
set "ISCC_EXE=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist "%ISCC_EXE%" (
  echo Inno Setup 6 not found. Trying winget install...
  winget install --id JRSoftware.InnoSetup -e --accept-package-agreements --accept-source-agreements
  if errorlevel 1 (
    echo [ERROR] Automatic Inno Setup install failed.
    echo Please install Inno Setup 6 manually: https://jrsoftware.org/isdl.php
    exit /b 1
  )
)

echo [3/3] Building setup installer...
call build_installer.bat
if errorlevel 1 (
  echo [ERROR] Installer build failed.
  exit /b 1
)

echo.
echo [OK] One-touch setup completed.
for %%f in ("dist\Rhythm4K-Setup.exe") do echo Output: %%~ff
pause
