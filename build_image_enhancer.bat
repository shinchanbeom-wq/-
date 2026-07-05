@echo off
setlocal

set "SRC=tools\opencv_image_enhancer.cpp"
set "OUT_DIR=dist"
set "OUT_EXE=%OUT_DIR%\ImageEnhancer.exe"

if not defined OPENCV_DIR (
  echo [ERROR] OPENCV_DIR 환경변수가 설정되어 있지 않습니다.
  echo 예: set OPENCV_DIR=C:\opencv\build\x64\vc16
  echo OPENCV_DIR 아래에 include 폴더와 lib 폴더가 있어야 합니다.
  pause
  exit /b 1
)

if not exist "%OPENCV_DIR%\include\opencv2\opencv.hpp" (
  echo [ERROR] OpenCV 헤더를 찾을 수 없습니다: %OPENCV_DIR%\include
  pause
  exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

where cl >nul 2>nul
if errorlevel 1 (
  echo [ERROR] cl.exe를 찾을 수 없습니다.
  echo "x64 Native Tools Command Prompt for VS"에서 이 배치 파일을 실행하세요.
  pause
  exit /b 1
)

for %%F in ("%OPENCV_DIR%\lib\opencv_world*.lib") do set "OPENCV_WORLD_LIB=%%~nxF"
if not defined OPENCV_WORLD_LIB (
  echo [ERROR] %OPENCV_DIR%\lib 에서 opencv_world*.lib 파일을 찾을 수 없습니다.
  pause
  exit /b 1
)

cl /EHsc /std:c++17 /O2 /utf-8 /I"%OPENCV_DIR%\include" "%SRC%" /Fe:"%OUT_EXE%" /link /LIBPATH:"%OPENCV_DIR%\lib" "%OPENCV_WORLD_LIB%"
if errorlevel 1 (
  echo [ERROR] 빌드 실패
  pause
  exit /b 1
)

echo.
echo 빌드 완료: %OUT_EXE%
echo 실행 전 OpenCV DLL 폴더를 PATH에 추가하세요:
echo set PATH=%%OPENCV_DIR%%\bin;%%PATH%%
echo.
pause
