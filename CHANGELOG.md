# Changelog

## 3.1.0 (포크: 한글화·눌림 효과·버그 수정)

- **한글화:** 앱 화면, 상태 문구, 감시 스크립트 로그 메시지, NEMESIS 패키지 이름·설명,
  README와 USB 안내문을 한국어로 바꿈. 상태 카드 폭을 넘는 긴 문구는 두 줄로 나눔.
- **버튼 눌림 효과:** 누르는 동안 버튼·카드가 5% 줄어들고 밝아지며 손을 떼면 되돌아옴.
  누른 채로 버튼 밖으로 나가면 취소(이전에는 손을 뗀 위치의 버튼이 눌림). 클릭음과 햅틱 신호를 내고,
  연결·해제를 누르면 서비스가 처리하기 전까지 상태 카드에 "요청됨"을 바로 표시. 자동 재연결 스위치는 미끄러지듯 움직임.
- **수정: 연결 해제 요청이 매초 반복되던 문제.** 네트워크가 살아 있는 동안 매 틱마다
  `disconnectIssued`를 지워 해제 명령을 1초마다 다시 보냈음. 이제 한 번만 보냄.
- **수정: 네트워크가 내려가지 않으면 해제 중 상태에 영원히 머물던 문제.** 1.9.1에서 넣은
  해제 시간 초과(15초) 오류가 3.0.0의 네트워크 경로에서 빠져 있었음. 다시 "연결 해제 실패 또는 시간 초과"로 알림.
- **수정: 해제 명령이 선택된 휴대폰에만 가던 문제.** 실제로 PAN에 연결된 기기 모두에 해제를 보냄.
  연결 중에 휴대폰 선택을 바꾸면 다른 기기에 해제가 가서 연결이 끊기지 않던 경우를 막음.
- **수정: 연결 중 휴대폰 변경 차단.** 연결됨·연결 중·해제 중에는 휴대폰을 바꾸지 않고 안내를 표시.
- **수정: 연결 뒤 남은 기한이 다음 해제의 기한으로 쓰이던 문제.** 연결되면 기한을 초기화.
- 연결 문구에 실제로 연결된 기기 이름을 표시. 앱 제목의 버전을 APK 버전에서 읽음(하드코딩 제거).
- 그리기 때마다 글꼴을 새로 만들지 않도록 캐시.
- NEMESIS 호환 목록에 쏘나타 하이브리드(DN8 HEV) 국내형 260902 추가(실차 미검증).
- `build.sh`: `shasum`이 없는 Linux에서는 `sha256sum` 사용.

## 3.0.0

- Rename the product from iPhone Bluetooth PAN to Bluetooth PAN.
- Support any paired phone that provides standard Bluetooth tethering or a PAN
  Network Access Point service.
- Rename the NEMESIS package to `bluetooth.pan`, the Android application ID to
  `org.egmp.bluetoothpan`, the APK to `BluetoothPan.apk`, and release archives
  to `bluetooth.pan-<version>.zip`.
- Replace phone-vendor-specific UI, status, documentation, logging, and build
  identifiers with generic Bluetooth PAN terminology.
- Conflict with the legacy `iphone.bluetooth.pan` package so both supervisors
  cannot be installed simultaneously.

## 2.0.1

- Apply the automatic-reconnect cooldown immediately after a live connection
  drops instead of making one premature retry first.
- Skip the transient connected UI state when a disconnect request arrives at
  the same moment that PAN network setup completes.
- Leave reconnect cooldown immediately if automatic reconnect is switched off.

## 2.0.0

- Replace blocking connect/disconnect routines with one serialized state
  machine driven by persistent user intent and live adapter, phone, PAN-link,
  and network observations.
- Wait for Bluetooth and its bonded-device database to stabilize before PAN
  initialization, avoiding the boot race that left PanService with no
  `bt-pan` interface.
- Keep desired connection state separate from observed state so opposite
  requests, external connections, service restarts, and reconnect cooldowns
  reconcile predictably.
- Add bounded PAN proxy recovery after repeated immediate connection drops,
  while refusing recovery whenever a PAN link or network is active.
- Derive dashboard color and status from explicit controller states and emit
  structured transition, observation, command, and result logs.

## 1.9.2

- Detect a PAN request that the remote endpoint immediately drops instead of
  leaving the dashboard in a misleading 30-second connecting state.
- Report the fast rejection within three seconds with a Bluetooth tethering hint,
  while preserving the full timeout for connections that remain in progress.

## 1.9.1

- Reconcile the selected phone's real PAN and network state at startup and
  continuously, including connections initiated outside the app and drops.
- Discover an already-connected PAN device at startup, or automatically select
  the sole bonded phone when no saved selection exists.
- Make Connect idempotent for already-connected and already-connecting phones.
- Confirm both PAN disconnection and network teardown before reporting a
  successful Disconnect, with explicit timeout states otherwise.
- Retry automatic reconnect only after confirmed disconnection, with a
  30-second cooldown between attempts.
- Log only observed state transitions during idle monitoring.
- Avoid reinitializing a healthy PanService, which can erase its active-device
  state while the PAN network is still connected.
- Let an opposite button request cancel an in-progress connect/disconnect wait
  instead of sitting behind its timeout.
- Treat Android's live Bluetooth network as connected even when a restarted
  PanService has forgotten its device-state entry; do not require Java's
  unreliable `bt-pan` address enumeration.
- Use green for online, amber for transitional states, and red for offline or
  failed states in the dashboard.
- Replace the indefinite "waiting for network cleanup" message with an explicit
  disconnect-incomplete error when the PAN network remains active.

## 1.9.0

- Capture filtered application, Bluetooth PAN, and Android runtime messages in
  the NEMESIS `pan-supervisor` service log.
- Add timestamped diagnostic-session markers and stop the logcat child cleanly
  with the supervisor.
- Keep waiting after transient disconnected readings during an accepted PAN
  request, rather than reporting failure before the stock service finishes.
- Request the KitKat `GET_TASKS` permission required by this head unit when the
  activity closes, preventing the back-button crash seen in captured logs.

## 1.8.1

- Reacquire the Bluetooth PAN profile proxy when the stock service disconnects,
  allowing subsequent connections without restarting the app.
- Periodically reassert stock PAN initialization to repair a stale registered
  service whose profile binder is no longer available.
- Document the canonical NEMESIS update behavior: the previous APK is
  uninstalled before its replacement is installed, so signer continuity is not
  required and app preferences reset on update.

## 1.8.0

- Use the NEMESIS firmware identifier for compatibility and advertise untested
  head-unit models as partial matches.
- Replace the duplicate post-install startup and logcat keepalive with one
  genuine NEMESIS-supervised PAN/controller service.
- Present the canonical USB bundle as the primary user download and keep build
  output limited to the USB bundle and bare Package Store archive.

## 1.7.0

- Crop transparent icon padding so the artwork fills the NEMESIS launcher slot.

## 1.6.0

- Use a calmer teal-blue tile and enlarge/thicken the globe and Bluetooth glyph for launcher-size legibility.

## 1.5.0

- Refine the icon to a full wireframe globe with a smaller centered Bluetooth rune.

## 1.4.0

- Simplify the launcher artwork to a dark globe/Bluetooth glyph on a cyan tile matching NEMESIS's Precondition icon.

## 1.3.0

- Add a purpose-built launcher icon matching the dark dashboard.

## 1.2.0

- Scale the dashboard to the head unit's actual display resolution.
- Increase secondary-label and footer text sizes.

## 1.1.0

- Replace stock Holo controls with a dark, touch-friendly dashboard.

## 1.0.0

- Initial standalone NEMESIS 1.16 release.
- Manual connect and disconnect, connection status, IP display, and optional startup reconnect.
