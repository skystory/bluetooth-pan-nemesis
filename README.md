# 블루투스 PAN (Bluetooth PAN for NEMESIS)

현대·기아 헤드유닛을 **페어링된 휴대폰의 블루투스 테더링(PAN)**으로 인터넷에 연결하는
Android 4.4 앱과 NEMESIS 1.16 패키지입니다.

순정 펌웨어에 이미 들어 있는 블루투스 PANU 기능을 그대로 씁니다. `Bluetooth.apk`를 바꾸거나
블루투스 스택을 고치지 않으며, HMG-API도 필요 없습니다.

이 저장소는 Isaac Wisdom이 만든 Bluetooth PAN 3.0.0을 포크해
**한글화**, **버튼 눌림 효과**, **버그 수정**을 더한 버전(3.1.x)입니다. 자세한 내용은
[CHANGELOG.md](CHANGELOG.md)를 참고하세요.

## 시험된 환경

- 원본 시험 환경: 헤드유닛 NEEV23(미국), 펌웨어 260507, Android 4.4.2, NEMESIS 1.16.2
- 이 포크의 대상: 쏘나타 하이브리드(DN8 HEV) 국내형, 펌웨어 260902 (`DN823HEV.KOR`)
  : NEMESIS 호환 목록(`os`)에 추가했지만 **실차 동작은 아직 검증 전**입니다.

그 밖의 Gen5W 헤드유닛은 같은 표준 블루투스 PAN API를 쓰므로 NEMESIS에 "부분 호환"으로 표시됩니다.

## 기능

- NEMESIS Manager의 **My Packages** 화면에서 실행
- 페어링된 휴대폰 선택 (연결 중에는 다른 휴대폰으로 바꾸지 않음)
- 수동 연결과 연결 해제
- 실제 PAN·네트워크 상태를 계속 확인 (밖에서 맺은 연결, 갑작스런 끊김 포함)
- 블루투스, DHCP, 할당된 주소 표시
- 선택형 자동 재연결 (기본 꺼짐). 꺼져 있으면 연결이 끊기거나 재부팅된 뒤에도 스스로 연결하지 않고,
  켜져 있으면 재부팅 후 마지막으로 연결했던 휴대폰에 다시 연결합니다.
- 순정 PAN 프로필 자동 활성화·초기화
- NEMESIS 로그 수집기와 연동되는 연결 진단 로그
- **버튼 눌림 효과:** 누르는 동안 버튼이 살짝 줄어들고 밝아지며, 손을 떼면 되돌아옵니다.
  아주 짧게 탭해도 눌렸다 돌아오는 모습이 보입니다. 누른 채로 버튼 밖으로 손가락을 빼면 취소됩니다. 클릭음(시스템 터치음 설정을 따름)이 나고,
  연결·해제를 누르면 상태 카드에 "요청됨"이 바로 표시됩니다. 자동 재연결 스위치는 미끄러지듯 움직입니다.

## 설치

### USB 메모리 (권장)

`bluetooth.pan-<버전>-usb.zip`을 USB 메모리 최상위에 압축 해제합니다. 결과는 아래와 같아야 합니다.

```text
NEMESIS.data
README.txt
packages/
└── bluetooth.pan-<버전>.zip
```

Android가 다 켜진 뒤 USB를 꽂고 NEMESIS 설치 안내를 확인합니다.

### 패키지 스토어 또는 ADB

압축하지 않은 `bluetooth.pan-<버전>.zip`이 패키지 스토어 등록과 직접 설치에 쓰는 원본 패키지입니다.

```sh
adb push bluetooth.pan-3.1.1.zip /data/local/tmp/bluetooth.pan.zip
adb shell '/system/xbin/nemesis install /data/local/tmp/bluetooth.pan.zip'
adb shell '/system/xbin/nemesis pkg-launch bluetooth.pan'
```

휴대폰은 미리 차량과 페어링돼 있어야 합니다. [연결]을 누르기 전에 휴대폰에서 **블루투스 테더링**
(또는 블루투스 핫스팟)을 켜 주세요. 휴대폰에 따라 Wi-Fi 핫스팟과 별도 설정입니다.

### 2.0.1 이하 버전에서 옮겨 오기

3.0.0부터 NEMESIS ID가 `bluetooth.pan`, Android 패키지가 `org.egmp.bluetoothpan`으로 바뀌었습니다.
3.x를 설치하기 전에 예전 패키지를 지우세요.

```sh
adb shell '/system/xbin/nemesis uninstall iphone.bluetooth.pan'
```

NEMESIS는 두 패키지를 함께 설치하지 않으므로, 두 감시 프로세스가 같은 PAN 서비스를 동시에 다루는 일은 없습니다.

## 진단

```sh
adb shell '/system/xbin/nemesis service status bluetooth.pan/pan-supervisor'
adb shell '/system/xbin/nemesis log service bluetooth.pan/pan-supervisor'
```

감시 로그에는 앱의 연결 요청, PAN 프로필 상태, 블루투스 연결 변화, 네트워크 상태, Android 런타임
오류가 남습니다. NEMESIS Manager의 **Logs → Copy to USB**에도 포함됩니다. 연결에 성공하면
휴대폰이 할당한 주소로 `bt-pan` 인터페이스가 생깁니다.

## 빌드

필요한 것:

- JDK 17
- Android SDK platform과 build-tools (`aapt2`, `d8`, `apksigner`)
- `zip`, Python 3

`ANDROID_HOME`을 설정한 뒤 실행합니다.

```sh
./build.sh
```

`dist/`에는 **GitHub 릴리즈에 올리는 파일만** 만들어집니다(빌드 중간 파일은 `.build/`에 남고 올리지 않습니다).

- `bluetooth.pan-<버전>-usb.zip`: USB 설치용 (권장)
- `bluetooth.pan-<버전>.zip`: 패키지 스토어·ADB 직접 설치용
- `SHA256SUMS`: 위 두 파일의 체크섬

서명 키를 따로 지정할 수 있습니다.

```sh
APP_KEY=/secure/path/bluetooth-pan.p12 APP_KEY_PASS='…' ./build.sh
```

`APP_KEY`가 없으면 임시 개발용 키를 만듭니다. NEMESIS는 업데이트할 때 이전 APK를 지우고 새로
설치하므로 서명 키가 같을 필요는 없습니다. 대신 업데이트하면 앱에 저장된 휴대폰 선택과 자동 재연결
설정이 초기화됩니다. 키와 빌드 결과물은 Git에서 제외되며 절대 커밋하지 마세요.

> **Windows 참고:** `build.sh`는 macOS·Linux 기준입니다. Windows의 build-tools는 `d8.bat`,
> `apksigner.bat`, `aapt2.exe`라서 Git Bash에서 그대로는 찾지 못하고, Git Bash에는 `zip`도 없습니다.
> WSL(Linux용 Android SDK)에서 빌드하는 것을 권합니다.

## 테스트

```sh
sh tests/test-supervisor.sh
```

가짜 `am`, `pm`, `dumpsys`, `logcat`, `sleep`으로 감시 스크립트의 시작 절차와 로그 수집을 확인합니다.

## 안전과 한계

차량 인포테인먼트용 실험적 소프트웨어입니다. 운전 중에는 조작하지 마세요. 자동 재연결은
직접 켜야 동작합니다. 현재 패키지는 위의 원본 시험 환경에서만 검증됐습니다.

## 라이선스

GPL-3.0-or-later.
