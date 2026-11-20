#!/system/bin/sh

# NEMESIS가 관리하는 상주 감시 스크립트입니다. 꺼져 있는 순정 PAN 프로필을 준비하고,
# 앱의 제어 서비스를 띄운 뒤, Android가 둘 중 하나를 종료하면 다시 시작합니다.
# 이 스크립트의 출력이 NEMESIS 서비스 로그가 되며, 걸러낸 logcat 자식 프로세스가
# 앱과 블루투스 진단 로그를 같은 로그에 남깁니다.

VERSION=3.1.2

PAN_COMPONENT=com.android.bluetooth/.pan.PanService
APP_COMPONENT=org.egmp.bluetoothpan/org.egmp.bluetoothpan.PanControllerService
LOGCAT_PID=

cleanup() {
    if [ -n "$LOGCAT_PID" ]; then
        kill "$LOGCAT_PID" >/dev/null 2>&1
        wait "$LOGCAT_PID" >/dev/null 2>&1
    fi
}

trap 'exit 0' 1 2 15
trap cleanup 0

start_pan() {
    pm enable --user 0 "$PAN_COMPONENT"
    am startservice -n "$PAN_COMPONENT" \
        --es action com.android.bluetooth.btservice.action.STATE_CHANGED \
        --ei android.bluetooth.adapter.extra.STATE 12
}

start_controller() {
    am startservice -n "$APP_COMPONENT"
}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

echo "===== 블루투스 PAN $VERSION 진단 세션 $(date) ====="
echo "Bluetooth PAN: 감시 시작"
logcat -v time -s BluetoothPanApp:I BluetoothPan:D PanService:D AndroidRuntime:E '*:S' &
LOGCAT_PID=$!
echo "Bluetooth PAN: 진단 logcat pid=$LOGCAT_PID"
# Android는 블루투스가 페어링 기기 목록을 복원하기 전에 부팅 완료를 알릴 수 있습니다.
# 그때 PanService를 시작하면 살아 있어도 bt-pan을 만들지 못하므로 잠시 기다립니다.
echo "Bluetooth PAN: 블루투스 시작을 기다리는 중"
sleep 10
start_pan
start_controller

while true; do
    sleep 15
    # PanService가 정상일 때는 초기화 요청을 다시 보내지 않습니다. 연결 중에 이 요청을 받으면
    # 순정 구현이 기기별 상태를 잃을 수 있습니다.
    if ! dumpsys activity services com.android.bluetooth/.pan.PanService 2>/dev/null \
        | grep -q 'ServiceRecord'; then
        echo "Bluetooth PAN: 순정 PAN 서비스가 멈춰 다시 시작"
        start_pan
    fi
    if ! dumpsys activity services "$APP_COMPONENT" 2>/dev/null \
        | grep -q 'ServiceRecord'; then
        echo "Bluetooth PAN: 제어 서비스가 멈춰 다시 시작"
        start_controller
    fi
done
