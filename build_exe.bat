@echo off
setlocal

if not exist .venv (
  py -m venv .venv
  if errorlevel 1 (
    echo [ERROR] Failed to create virtual environment.
    pause
    exit /b 1
  )
)

call .venv\Scripts\activate
if errorlevel 1 (
  echo [ERROR] Failed to activate virtual environment.
  pause
  exit /b 1
)

python -m pip install --upgrade pip
if errorlevel 1 (
  echo [ERROR] pip upgrade failed.
  pause
  exit /b 1
)

pip install -r requirements.txt
if errorlevel 1 (
  echo [ERROR] dependency install failed.
  pause
  exit /b 1
)

python -m PyInstaller --noconfirm --onefile --windowed --name Rhythm4K ^
  --add-data "index.html;." ^
  --add-data "style.css;." ^
  --add-data "app.js;." ^
  --add-data "songs;songs" ^
  launcher.py
if errorlevel 1 (
  echo [ERROR] EXE build failed.
  pause
  exit /b 1
)

echo.
echo Done: dist\Rhythm4K.exe
pause
