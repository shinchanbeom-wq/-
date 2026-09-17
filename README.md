# FreeVR-SBS

Android 공개 API만 사용하여 화면 캡처를 기기 안에서 OpenGL ES 기반 좌우 분할(SBS) 화면으로 표시하는 무료 Kotlin 프로젝트입니다. 기본 UI 언어는 한국어입니다.

## 빌드 방법
Android Studio에서 이 폴더를 열고 Gradle 동기화를 완료한 뒤 `app`의 `assembleDebug` 작업을 실행합니다. Android SDK Platform 35와 JDK 17이 필요합니다.

### Windows CMD
```cmd
cd /d D:\FreeVR-SBS
gradlew.bat assembleDebug
dir app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
SDK/ADB가 PATH에 없으면 다음과 같이 설정합니다.
```cmd
set ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
set PATH=%ANDROID_SDK_ROOT%\platform-tools;%PATH%
adb devices
```

## 설치 방법 및 최초 실행
1. Android Studio에서 프로젝트를 열고 Gradle을 동기화합니다.
2. 디버그 APK를 빌드하고 USB 디버깅을 켠 기기에 설치합니다.
3. 앱을 열어 안내를 읽은 뒤 **시작하기**를 누릅니다.
4. **VR 시작**을 누르고 시스템의 **화면 캡처 권한**을 허용합니다.
5. 필요할 때 앱이 요청하는 블루투스 권한을 허용합니다.
6. VR 화면이 나타나면 휴대폰을 Cardboard 형태의 VR 고글에 장착합니다.

## 블루투스 입력 장치 연결 방법
1. Android 설정의 블루투스에서 키보드, 마우스, 리모컨 또는 HID 장치를 페어링합니다.
2. FreeVR-SBS에서 **블루투스 입력**을 열고 연결된 장치가 표시되는지 확인합니다.
3. **입력 테스트**에서 장치의 키나 버튼을 눌러 Android가 이벤트를 제공하는지 확인합니다.
4. 이후 키 매핑 화면에서 지원되는 VR 동작에 매핑합니다. Android가 노출하는 HID 입력만 감지합니다.

## 접근성 및 보안 제한
접근성 서비스는 시스템 설정에서 사용자가 직접 활성화해야 합니다. 서비스는 접근성 노드가 제공하는 클릭, 스크롤, 포커스와 공식 전역 동작만 수행할 수 있습니다. Unity, Unreal, OpenGL/Vulkan 기반 게임처럼 접근성 노드를 제공하지 않는 앱은 조작할 수 없습니다.

MediaProjection은 화면을 캡처할 뿐 다른 앱으로 임의의 터치를 주입하지 않습니다. 루트, 숨겨진 API, 우회 기법은 사용하지 않습니다. 캡처 프레임은 외부 서버로 전송되지 않습니다.

## Android 14+
캡처는 `mediaProjection` 유형의 포그라운드 서비스로 실행되며, 매번 시스템 화면 캡처 동의를 받아 사용합니다. Android 14 이상에서 요구하는 포그라운드 서비스 및 MediaProjection 수명 주기를 따릅니다.

## 현재 기능과 향후 깊이 처리
현재 모드는 동일한 2D 캡처 프레임을 두 눈에 그리는 **2D SBS**이며 실제 입체 3D가 아닙니다. `depth/DepthProcessor.kt`는 장치 내 TensorFlow Lite, ONNX Runtime 등의 미래 깊이 추정기를 연결할 인터페이스입니다. 깊이 맵을 생성한 뒤 눈별 재투영 렌더러를 구현하면 AI 2D→3D 모드를 추가할 수 있습니다. 클라우드 AI는 사용하지 않습니다.
