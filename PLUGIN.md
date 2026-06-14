# EndermanBoss Paper Plugin

이 저장소는 `.exe`, `.dll`, `.jar`, `.bin`, 이미지 파일, 압축 파일, AI 모델 파일, Git LFS 파일 없이 **소스코드와 빌드 명령어만** 제공합니다.

## CMD에서 바로 JAR 만들기

Windows CMD에서 저장소 루트 폴더로 이동한 뒤 아래 명령어를 실행하세요.

```cmd
build_plugin.cmd
```

또는 배치 파일 없이 직접 실행하려면 아래 명령어를 사용하세요.

```cmd
gradle clean build
```

빌드가 성공하면 아래 경로에 Paper 플러그인 JAR가 생성됩니다.

```text
build\libs\enderman-boss-paper-1.0.0.jar
```

생성된 JAR 파일을 Paper 서버의 `plugins` 폴더에 복사한 뒤 서버를 재시작하세요.

## 서버에서 사용하기

OP 또는 `endermanboss.spawn` 권한이 있는 플레이어가 아래 명령어로 보스를 소환합니다.

```text
/endermanboss spawn
```

## 보스 정보

- 체력: 500
- 1페이즈: 500~250 HP, 뒤 텔레포트 공격 또는 도주 후 원거리 발사체 공격
- 2페이즈: 250~100 HP, 매우 빠른 돌격 공격
- 3페이즈: 100~0 HP, 30초 정지 및 받는 피해 0.8배 적용
- 3페이즈 제한 시간 내 처치 실패: 큰 보라색 폭발 후 보상 없음
- 처치 성공 보상: 우클릭 시 가장 가까운 발사체 제외 대상 뒤로 이동하는 `공허의 단검`
