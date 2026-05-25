@echo off
setlocal

if not exist .venv (
  py -m venv .venv
)

call .venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt

pyinstaller --noconfirm --onefile --windowed --name Rhythm4K ^
  --add-data "index.html;." ^
  --add-data "style.css;." ^
  --add-data "app.js;." ^
  --add-data "songs;songs" ^
  launcher.py

echo.
echo 빌드 완료: dist\Rhythm4K.exe
pause
