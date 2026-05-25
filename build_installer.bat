@echo off
setlocal

if not exist dist\Rhythm4K.exe (
  echo Missing file: dist\Rhythm4K.exe
  echo Run build_exe.bat first.
  pause
  exit /b 1
)

set "ISCC_PATH=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist "%ISCC_PATH%" (
  echo Inno Setup 6 is not installed.
  echo Install from: https://jrsoftware.org/isdl.php
  pause
  exit /b 1
)

"%ISCC_PATH%" installer.iss
if errorlevel 1 (
  echo Installer build failed.
  pause
  exit /b 1
)

echo.
echo Done: dist\Rhythm4K-Setup.exe
pause
