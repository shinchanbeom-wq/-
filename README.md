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



## 처음부터 원터치 셋업 (아주 자세히)
아래는 **"파일이 하나도 없는 상태" 기준**으로, 직접 파일 만들기부터 설치 파일 생성까지 순서대로 설명합니다.

### 0) 준비물
- Windows 10/11
- 인터넷 연결
- Python 3.10+ 설치 (설치 시 "Add Python to PATH" 체크)
- (선택) Git 설치

### 1) 새 폴더 만들기
1. 바탕화면에서 새 폴더 생성
2. 폴더 이름을 `Rhythm4K`로 지정

### 2) 프로젝트 파일 넣기
다음 파일/폴더가 `Rhythm4K` 안에 있어야 합니다.
- `index.html`
- `style.css`
- `app.js`
- `launcher.py`
- `requirements.txt`
- `build_exe.bat`
- `installer.iss`
- `build_installer.bat`
- `one_touch_setup.bat`
- `songs/index.json`
- `songs/sample/song.json`
- `songs/sample/chart.json`

> 이미 이 저장소를 받았다면(다운로드/클론) 이 단계는 자동으로 충족됩니다.

### 3) 샘플 오디오 파일 직접 넣기
1. `Rhythm4K\songs\sample` 폴더 열기
2. 오디오 파일 하나 준비 (`.ogg` 권장)
3. 파일명을 **정확히** `sample.ogg` 로 변경
4. 최종 경로가 `Rhythm4K\songs\sample\sample.ogg` 인지 확인

### 4) 원터치 셋업 실행
1. `Rhythm4K` 폴더에서 `one_touch_setup.bat` 더블클릭
2. 콘솔 창이 뜨면 자동으로 다음이 진행됨
   - 가상환경 생성
   - 라이브러리 설치
   - `dist/Rhythm4K.exe` 생성
   - Inno Setup 확인/설치
   - `dist/Rhythm4K-Setup.exe` 생성

### 5) 완료 확인
- 아래 파일이 생성되면 성공입니다.
  - `Rhythm4K\dist\Rhythm4K.exe`
  - `Rhythm4K\dist\Rhythm4K-Setup.exe`

### 6) 다른 사람에게 배포
- 배포는 `Rhythm4K-Setup.exe` 하나만 전달하면 됩니다.
- 상대방은 설치 마법사만 따라가면 실행 가능합니다.

### 7) 자주 생기는 문제
- `winget` 실패: Inno Setup 6을 수동 설치 후 `one_touch_setup.bat` 재실행
- Python 명령 인식 안 됨: Python 재설치 + PATH 체크
- 오디오 안 나옴: `songs/sample/sample.ogg` 경로/파일명 확인

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
