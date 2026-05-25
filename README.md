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
