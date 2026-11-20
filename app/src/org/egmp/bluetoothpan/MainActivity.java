package org.egmp.bluetoothpan;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    static final String PREFS = "pan";

    /** 버튼을 누른 뒤 서비스가 상태를 바꾸기 전까지 "요청 보냄" 문구를 보여줄 최대 시간 */
    private static final long REQUEST_FEEDBACK_MS = 4000L;
    /** 안내 문구(예: 연결 중에는 휴대폰을 바꿀 수 없음)를 보여줄 시간 */
    private static final long NOTICE_FEEDBACK_MS = 2500L;

    private final Handler handler = new Handler();
    private final List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    private SharedPreferences prefs;
    private Dashboard dashboard;
    private int selected;
    private String versionName = "";

    /** 화면에 바로 보여줄 피드백 문구와, 그 문구를 거둘 조건 */
    private String feedbackMessage;
    private String feedbackStateAtRequest;
    private long feedbackUntil;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (devices.isEmpty()) loadPhones();
            dashboard.invalidate();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            versionName = "";
        }
        loadPhones();
        dashboard = new Dashboard();
        setContentView(dashboard);
        startService(new Intent(this, PanControllerService.class));
    }

    private void loadPhones() {
        devices.clear();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> bonded = adapter == null ? null : adapter.getBondedDevices();
        if (bonded != null) for (BluetoothDevice device : bonded) {
            BluetoothClass type = device.getBluetoothClass();
            if (type != null && type.getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) devices.add(device);
        }
        String saved = prefs.getString("device", "");
        selected = 0;
        for (int i = 0; i < devices.size(); i++) if (devices.get(i).getAddress().equals(saved)) selected = i;
    }

    /** 연결돼 있거나 연결·해제가 진행 중일 때는 휴대폰을 바꾸지 않음 (다른 기기에 해제 명령이 가는 것을 막음) */
    private boolean phoneLocked() {
        String controllerState = prefs.getString("controller_state", "");
        return "CONNECTED".equals(controllerState)
            || "CONNECTING".equals(controllerState)
            || "CONFIGURING_NETWORK".equals(controllerState)
            || "DISCONNECTING".equals(controllerState);
    }

    private void selectNextPhone() {
        if (devices.size() < 2) return;
        if (phoneLocked()) {
            showFeedback("연결 중에는 휴대폰을 바꿀 수 없음\n연결을 해제한 뒤 바꿔 주세요", NOTICE_FEEDBACK_MS);
            return;
        }
        selected = (selected + 1) % devices.size();
        prefs.edit().putString("device", devices.get(selected).getAddress()).commit();
        dashboard.invalidate();
    }

    private void request(boolean connect) {
        int sequence = prefs.getInt("request_sequence", 0) + 1;
        SharedPreferences.Editor edit = prefs.edit().putBoolean("requested_connect", connect)
            .putInt("request_sequence", sequence);
        if (!devices.isEmpty() && !phoneLocked()) edit.putString("device", devices.get(selected).getAddress());
        edit.commit();
        startService(new Intent(this, PanControllerService.class));
        showFeedback(connect ? "연결 요청을 보냈습니다" : "연결 해제 요청을 보냈습니다", REQUEST_FEEDBACK_MS);
    }

    private void showFeedback(String message, long durationMs) {
        feedbackMessage = message;
        feedbackStateAtRequest = currentStateKey();
        feedbackUntil = SystemClock.uptimeMillis() + durationMs;
        dashboard.invalidate();
    }

    /** 서비스가 상태나 문구를 바꾸면(요청을 받아 처리하기 시작하면) 피드백 문구를 거둠 */
    private String activeFeedback() {
        if (feedbackMessage == null) return null;
        boolean expired = SystemClock.uptimeMillis() >= feedbackUntil;
        boolean serviceMoved = !currentStateKey().equals(feedbackStateAtRequest);
        if (expired || serviceMoved) {
            feedbackMessage = null;
            return null;
        }
        return feedbackMessage;
    }

    private String currentStateKey() {
        return prefs.getString("controller_state", "") + "|" + prefs.getString("status", "");
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private final class Dashboard extends View {
        private static final int TARGET_NONE = 0;
        private static final int TARGET_PHONE = 1;
        private static final int TARGET_CONNECT = 2;
        private static final int TARGET_DISCONNECT = 3;
        private static final int TARGET_AUTO = 4;

        /** 눌렸을 때 버튼이 줄어드는 비율 (0.05 = 5%) */
        private static final float PRESSED_SHRINK = 0.05f;
        private static final long PRESS_DOWN_MS = 70L;
        private static final long PRESS_UP_MS = 180L;
        private static final long SWITCH_SLIDE_MS = 180L;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface regularFace = Typeface.create("sans", Typeface.NORMAL);
        private final Typeface boldFace = Typeface.create("sans", Typeface.BOLD);
        private final RectF phoneCard = new RectF();
        private final RectF statusCard = new RectF();
        private final RectF connectButton = new RectF();
        private final RectF disconnectButton = new RectF();
        private final RectF autoButton = new RectF();
        private final RectF switchTrack = new RectF();
        private float drawScale = 1;

        /** 손가락이 지금 누르고 있는 대상 (손을 떼면 TARGET_NONE) */
        private int touchTarget = TARGET_NONE;
        /** 눌림 효과를 그릴 대상 (손을 뗀 뒤 되돌아오는 애니메이션 동안에도 유지) */
        private int pressedTarget = TARGET_NONE;
        /** 0 = 평소, 1 = 완전히 눌림 */
        private float pressAmount;
        private ValueAnimator pressAnimator;

        /** 자동 재연결 스위치 손잡이 위치 (0 = 꺼짐, 1 = 켜짐) */
        private float switchPosition;
        private ValueAnimator switchAnimator;

        Dashboard() {
            super(MainActivity.this);
            setSoundEffectsEnabled(true);
            setHapticFeedbackEnabled(true);
            switchPosition = prefs.getBoolean("automatic", false) ? 1f : 0f;
        }

        @Override protected void onDraw(Canvas canvas) {
            drawScale = Math.min(getWidth() / 800f, getHeight() / 400f);
            if (drawScale <= 0) drawScale = 1;
            canvas.save();
            canvas.scale(drawScale, drawScale);
            int width = (int) (getWidth() / drawScale);
            int height = (int) (getHeight() / drawScale);
            canvas.drawColor(Color.rgb(13, 19, 26));

            text(canvas, versionName.length() == 0 ? "블루투스 PAN" : "블루투스 PAN v" + versionName,
                36, 46, 28, Color.WHITE, true);

            float top = 68;
            phoneCard.set(32, top, width * .52f, top + 86);
            int phoneSave = beginPressed(canvas, phoneCard, TARGET_PHONE);
            card(canvas, phoneCard, Color.rgb(25, 34, 44));
            text(canvas, "페어링된 휴대폰", phoneCard.left + 20, phoneCard.top + 27, 14, Color.rgb(139, 156, 173), true);
            String phoneName = devices.isEmpty() ? "페어링된 휴대폰 없음" : name(devices.get(selected));
            text(canvas, phoneName, phoneCard.left + 20, phoneCard.top + 59, 23, Color.WHITE, true);
            text(canvas, devices.size() > 1 ? "눌러서 변경  ›" : "페어링됨", phoneCard.right - 18,
                phoneCard.top + 59, 14, Color.rgb(111, 205, 255), false, Paint.Align.RIGHT);
            endPressed(canvas, phoneCard, 8, phoneSave);

            statusCard.set(width * .55f, top, width - 32, top + 86);
            card(canvas, statusCard, Color.rgb(25, 34, 44));
            String status = prefs.getString("status", "준비됨");
            String controllerState = prefs.getString("controller_state", "INITIALIZING");
            boolean connected = "CONNECTED".equals(controllerState);
            boolean working = "INITIALIZING".equals(controllerState)
                || "WAITING_FOR_BLUETOOTH".equals(controllerState)
                || "BLUETOOTH_SETTLING".equals(controllerState)
                || "WAITING_FOR_PHONE".equals(controllerState)
                || "STARTING_PAN".equals(controllerState)
                || "CONNECTING".equals(controllerState)
                || "CONFIGURING_NETWORK".equals(controllerState)
                || "DISCONNECTING".equals(controllerState)
                || "RECONNECT_COOLDOWN".equals(controllerState)
                || "RECOVERING_PAN".equals(controllerState);
            String feedback = activeFeedback();
            int statusColor = feedback != null ? Color.rgb(111, 205, 255)
                : connected ? Color.rgb(53, 214, 139)
                : working ? Color.rgb(255, 181, 71) : Color.rgb(235, 86, 86);
            paint.setColor(statusColor);
            canvas.drawCircle(statusCard.left + 22, statusCard.top + 25, 6, paint);
            String badge = feedback != null ? "요청됨" : connected ? "연결됨" : working ? "진행 중" : "연결 안 됨";
            text(canvas, badge, statusCard.left + 37, statusCard.top + 30, 14, statusColor, true);
            String[] lines = (feedback != null ? feedback : status).split("\\n");
            text(canvas, lines[0], statusCard.left + 20, statusCard.top + 59, 17, Color.WHITE, true);
            if (lines.length > 1) text(canvas, lines[1], statusCard.left + 20, statusCard.top + 78, 12, Color.rgb(160, 176, 191), true);

            float buttonTop = top + 110;
            connectButton.set(32, buttonTop, width * .62f, buttonTop + 76);
            int connectSave = beginPressed(canvas, connectButton, TARGET_CONNECT);
            round(canvas, connectButton, Color.rgb(0, 132, 203), 10);
            text(canvas, "연결", connectButton.centerX(), connectButton.centerY() + 8, 22, Color.WHITE, true, Paint.Align.CENTER);
            endPressed(canvas, connectButton, 10, connectSave);

            disconnectButton.set(width * .64f, buttonTop, width - 32, buttonTop + 76);
            int disconnectSave = beginPressed(canvas, disconnectButton, TARGET_DISCONNECT);
            round(canvas, disconnectButton, Color.rgb(43, 54, 66), 10);
            text(canvas, "연결 해제", disconnectButton.centerX(), disconnectButton.centerY() + 7, 18,
                Color.rgb(224, 231, 237), true, Paint.Align.CENTER);
            endPressed(canvas, disconnectButton, 10, disconnectSave);

            autoButton.set(32, buttonTop + 99, width - 32, buttonTop + 155);
            int autoSave = beginPressed(canvas, autoButton, TARGET_AUTO);
            card(canvas, autoButton, Color.rgb(20, 28, 37));
            text(canvas, "자동으로 다시 연결", autoButton.left + 20, autoButton.centerY() + 6, 17, Color.rgb(220, 229, 237), true);
            float switchRight = autoButton.right - 18;
            switchTrack.set(switchRight - 56, autoButton.centerY() - 13, switchRight, autoButton.centerY() + 13);
            round(canvas, switchTrack, blend(Color.rgb(66, 77, 88), Color.rgb(0, 132, 203), switchPosition), 13);
            paint.setColor(Color.WHITE);
            float knobX = switchTrack.left + 13 + (switchTrack.width() - 26) * switchPosition;
            canvas.drawCircle(knobX, switchTrack.centerY(), 10, paint);
            endPressed(canvas, autoButton, 8, autoSave);

            text(canvas, "선택한 휴대폰의 블루투스 테더링을 사용합니다. 자동 재연결은 기본으로 꺼져 있습니다.",
                36, height - 19, 14, Color.rgb(112, 130, 147), false);
            canvas.restore();
        }

        // ----- 눌림 효과 -----

        /** 이 대상이 눌려 있으면 중심 기준으로 살짝 줄여 그림. 반환값은 endPressed에 넘길 save 번호 (-1 = 효과 없음) */
        private int beginPressed(Canvas canvas, RectF bounds, int target) {
            if (target != pressedTarget || pressAmount <= 0f) return -1;
            int saveCount = canvas.save();
            float scale = 1f - PRESSED_SHRINK * pressAmount;
            canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
            return saveCount;
        }

        /** 눌린 대상 위에 밝은 막을 덮고 크기 변환을 되돌림 */
        private void endPressed(Canvas canvas, RectF bounds, float radius, int saveCount) {
            if (saveCount < 0) return;
            paint.setColor(Color.argb((int) (60 * pressAmount), 255, 255, 255));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            canvas.restoreToCount(saveCount);
        }

        private void animatePress(float to, long durationMs) {
            if (pressAnimator != null) pressAnimator.cancel();
            pressAnimator = ValueAnimator.ofFloat(pressAmount, to);
            pressAnimator.setDuration(durationMs);
            pressAnimator.setInterpolator(new DecelerateInterpolator());
            pressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    pressAmount = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            pressAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (pressAmount <= 0f && touchTarget == TARGET_NONE) pressedTarget = TARGET_NONE;
                }
            });
            pressAnimator.start();
        }

        private void animateSwitch(boolean on) {
            if (switchAnimator != null) switchAnimator.cancel();
            switchAnimator = ValueAnimator.ofFloat(switchPosition, on ? 1f : 0f);
            switchAnimator.setDuration(SWITCH_SLIDE_MS);
            switchAnimator.setInterpolator(new DecelerateInterpolator());
            switchAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    switchPosition = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            switchAnimator.start();
        }

        // ----- 터치 -----

        private int hitTarget(float x, float y) {
            if (phoneCard.contains(x, y)) return TARGET_PHONE;
            if (connectButton.contains(x, y)) return TARGET_CONNECT;
            if (disconnectButton.contains(x, y)) return TARGET_DISCONNECT;
            if (autoButton.contains(x, y)) return TARGET_AUTO;
            return TARGET_NONE;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() / drawScale, y = event.getY() / drawScale;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchTarget = hitTarget(x, y);
                    if (touchTarget != TARGET_NONE) {
                        pressedTarget = touchTarget;
                        animatePress(1f, PRESS_DOWN_MS);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // 누른 채로 버튼 밖으로 나가면 취소 (손을 떼도 동작하지 않음)
                    if (touchTarget != TARGET_NONE && hitTarget(x, y) != touchTarget) {
                        touchTarget = TARGET_NONE;
                        animatePress(0f, PRESS_UP_MS);
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    int releasedTarget = touchTarget;
                    touchTarget = TARGET_NONE;
                    if (releasedTarget != TARGET_NONE) {
                        animatePress(0f, PRESS_UP_MS);
                        if (hitTarget(x, y) == releasedTarget) activate(releasedTarget);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    touchTarget = TARGET_NONE;
                    animatePress(0f, PRESS_UP_MS);
                    return true;
                default:
                    return true;
            }
        }

        private void activate(int target) {
            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (target == TARGET_PHONE) selectNextPhone();
            else if (target == TARGET_CONNECT) request(true);
            else if (target == TARGET_DISCONNECT) request(false);
            else if (target == TARGET_AUTO) {
                boolean automatic = !prefs.getBoolean("automatic", false);
                prefs.edit().putBoolean("automatic", automatic).commit();
                animateSwitch(automatic);
            }
        }

        // ----- 그리기 도우미 -----

        private void card(Canvas canvas, RectF bounds, int color) { round(canvas, bounds, color, 8); }
        private void round(Canvas canvas, RectF bounds, int color, float radius) {
            paint.setColor(color); paint.setStyle(Paint.Style.FILL); canvas.drawRoundRect(bounds, radius, radius, paint);
        }
        private void text(Canvas canvas, String value, float x, float y, float size, int color, boolean bold) {
            text(canvas, value, x, y, size, color, bold, Paint.Align.LEFT);
        }
        private void text(Canvas canvas, String value, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(align);
            paint.setTypeface(bold ? boldFace : regularFace);
            canvas.drawText(value, x, y, paint);
        }
    }

    /** 두 색을 amount(0~1) 비율로 섞음 */
    private static int blend(int from, int to, float amount) {
        float keep = 1f - amount;
        return Color.rgb(
            (int) (Color.red(from) * keep + Color.red(to) * amount),
            (int) (Color.green(from) * keep + Color.green(to) * amount),
            (int) (Color.blue(from) * keep + Color.blue(to) * amount));
    }

    private static String name(BluetoothDevice device) {
        return device.getName() == null ? "이름 없는 휴대폰" : device.getName();
    }
}
