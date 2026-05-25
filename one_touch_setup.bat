@echo off
setlocal

REM 1) Build app EXE
call build_exe.bat
if errorlevel 1 (
  echo [ERROR] EXE 빌드 실패
  exit /b 1
)

REM 2) Ensure Inno Setup exists (install via winget if missing)
set ISCC_EXE=C:\Program Files (x86)\Inno Setup 6\ISCC.exe
if not exist "%ISCC_EXE%" (
  echo Inno Setup 6 미설치. winget으로 자동 설치를 시도합니다...
  winget install --id JRSoftware.InnoSetup -e --accept-package-agreements --accept-source-agreements
  if errorlevel 1 (
    echo [ERROR] Inno Setup 자동 설치 실패. 수동 설치 후 다시 실행하세요.
    exit /b 1
  )
)

REM 3) Build installer EXE
call build_installer.bat
if errorlevel 1 (
  echo [ERROR] 설치파일 빌드 실패
  exit /b 1
)

echo.
echo [OK] 원터치 셋업 완료
for %%f in ("dist\Rhythm4K-Setup.exe") do echo 생성 파일: %%~ff
pause
