# 4K Rhythm Game Prototype

브라우저/EXE로 실행 가능한 4키 리듬게임 프로토타입입니다.

## 기능
- 타이틀 화면에서 **곡 선택/설정** 이동
- 설정에서
  - 음악 보정(ms)
  - 4레인 키 설정
- 곡 선택 후 게임 플레이
- 판정: 완벽/좋음/나쁨/최악/놓침
- 판정 기반 점수 및 콤보
- 곡/채보를 JSON 파일로 분리하여 커스텀 가능

## 빠른 실행 (개발)
정적 서버로 실행하세요 (fetch 사용).

```bash
python -m http.server 8000
```

브라우저에서 `http://localhost:8000` 접속.

## 간단 설치용 EXE 만들기 (Windows)
아래 배치 파일만 실행하면 됩니다.

1. `build_exe.bat` 더블클릭
2. 완료 후 `dist/Rhythm4K.exe` 실행

`build_exe.bat`가 자동으로 하는 작업:
- 가상환경 생성
- 의존성 설치
- 단일 실행파일(One-file) 빌드



## 원터치 셋업 방법 (Windows)
아래 파일 하나만 실행하면 **빌드 + 설치파일 생성**까지 자동으로 진행됩니다.

1. `one_touch_setup.bat` 더블클릭
2. 완료 후 `dist/Rhythm4K-Setup.exe` 배포

동작 순서:
- `build_exe.bat` 실행
- Inno Setup 6 없으면 `winget`으로 자동 설치 시도
- `build_installer.bat` 실행

> 참고: `winget`이 막힌 환경에서는 Inno Setup 수동 설치가 필요합니다.

## 설치 프로그램(.exe) 만들기 (권장)
`dist/Rhythm4K.exe`를 만든 뒤, 설치 마법사 EXE까지 생성할 수 있습니다.

1. `build_exe.bat` 실행
2. [Inno Setup 6](https://jrsoftware.org/isdl.php) 설치
3. `build_installer.bat` 실행
4. 결과물: `dist/Rhythm4K-Setup.exe`

설치 프로그램 기능:
- 기본 설치 경로 선택
- 시작 메뉴 바로가기 생성
- 바탕화면 바로가기 선택 생성

## 커스텀 곡 추가
1. `songs/<song-id>/song.json` 생성
2. `songs/<song-id>/chart.json` 생성
3. `songs/index.json` 배열에 `songs/<song-id>/song.json` 경로 추가

### song.json 예시
```json
{
  "id": "sample",
  "title": "Sample Beat",
  "artist": "Demo Composer",
  "audio": "songs/sample/sample.ogg",
  "bpm": 120,
  "offsetMs": 0,
  "chart": "songs/sample/chart.json"
}
```

### chart.json 예시
```json
{
  "difficulty": "Normal",
  "notes": [
    {"timeMs": 1000, "lane": 0},
    {"timeMs": 1500, "lane": 1}
  ]
}
```

- `timeMs`: 곡 시작 기준 노트 타이밍(ms)
- `lane`: 0~3 (4키)

> 샘플 오디오는 포함되어 있지 않습니다. `songs/sample/sample.ogg` 파일을 직접 넣어주세요.

## OpenCV 이미지 보정 EXE 만들기 (Windows)
사용자가 제공한 C++ OpenCV 이미지 보정 코드는 `tools/opencv_image_enhancer.cpp`에 저장되어 있습니다.

### 준비물
- Visual Studio C++ 빌드 도구
- OpenCV Windows 빌드
- `x64 Native Tools Command Prompt for VS`

### 빌드 방법
1. OpenCV가 설치된 경로를 `OPENCV_DIR`로 지정합니다.
   ```bat
   set OPENCV_DIR=C:\opencv\build\x64\vc16
   ```
2. OpenCV DLL을 실행 경로에서 찾을 수 있게 `PATH`에 추가합니다.
   ```bat
   set PATH=%OPENCV_DIR%\bin;%PATH%
   ```
3. 저장소 루트에서 배치 파일을 실행합니다.
   ```bat
   build_image_enhancer.bat
   ```
4. 결과물은 `dist\ImageEnhancer.exe`로 생성됩니다.

### 실행 방법
- 이미지 파일을 `dist\ImageEnhancer.exe`에 드래그 앤 드롭하거나,
- 명령 프롬프트에서 아래처럼 실행합니다.
  ```bat
  dist\ImageEnhancer.exe C:\path\to\photo.jpg
  ```

결과 파일은 원본 이미지와 같은 폴더에 `_result_gray.jpg`, `_result_color.jpg` 이름으로 저장됩니다.
