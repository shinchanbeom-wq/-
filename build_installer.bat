@echo off
setlocal

if not exist dist\Rhythm4K.exe (
  echo dist\Rhythm4K.exe 파일이 없습니다.
  echo 먼저 build_exe.bat 를 실행해 EXE를 생성하세요.
  pause
  exit /b 1
)

set ISCC_PATH="C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist %ISCC_PATH% (
  echo Inno Setup 6가 설치되어 있지 않습니다.
  echo https://jrsoftware.org/isdl.php 에서 설치 후 다시 실행하세요.
  pause
  exit /b 1
)

%ISCC_PATH% installer.iss
if errorlevel 1 (
  echo 설치파일 생성 실패
  pause
  exit /b 1
)

echo.
echo 설치파일 생성 완료: dist\Rhythm4K-Setup.exe
pause
