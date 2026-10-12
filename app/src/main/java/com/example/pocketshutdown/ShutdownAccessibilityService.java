package com.example.pocketshutdown;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.Manifest;
import java.util.Locale;

public class ShutdownAccessibilityService extends AccessibilityService {
    public static final String PREFS = "settings";
    public static final String DIAG_PREFS = "diagnostics";

    // ユーザー指定: イヤホンの操作（再生/停止等）を3秒以内に連続で行うと画面OFF
    public static final long MEDIA_MULTI_TAP_WINDOW_MS = 3000; // 3秒以内
    // 音量ボタンのダブルタップ許容時間
    public static final long VOLUME_DOUBLE_TAP_MS = 650;

    private long lastVolumeUpTime = 0;
    private long lastVolumeDownTime = 0;

    // メディア操作の連続判定
    private long lastMediaActionTime = 0;

    // AccessibilityServiceとMediaSessionの二重受信防止用
    private long lastDuplicateEventTime = 0;
    private int lastDuplicateEventCode = -1;

    private MediaSession mediaSession;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                info = new AccessibilityServiceInfo();
            }
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            setServiceInfo(info);
        } catch (Exception ignored) {}

        setupMediaSession();
    }

    private void setupMediaSession() {
        try {
            if (mediaSession != null) {
                mediaSession.release();
            }
            mediaSession = new MediaSession(this, "PocketLockInput");
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public boolean onMediaButtonEvent(Intent intent) {
                    KeyEvent e = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (e != null && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                        return handleKeyInput(e.getKeyCode(), "MediaSession");
                    }
                    return super.onMediaButtonEvent(intent);
                }
            });
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .build());
            mediaSession.setActive(true);
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            return false;
        }

        int code = event.getKeyCode();

        boolean isVolumeKey = (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN);
        boolean isMediaKey = isMediaKeyCode(code);

        if (!isVolumeKey && !isMediaKey) {
            return false;
        }

        return handleKeyInput(code, "Accessibility");
    }

    private boolean isMediaKeyCode(int code) {
        return (code == KeyEvent.KEYCODE_HEADSETHOOK
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_STOP
                || code == KeyEvent.KEYCODE_MEDIA_NEXT
                || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    private boolean handleKeyInput(int code, String source) {
        long now = System.currentTimeMillis();

        // 120ms以内の同一キー重複検知（AccessibilityとMediaSessionの二重受信）を防止
        if (code == lastDuplicateEventCode && (now - lastDuplicateEventTime < 120)) {
            return false;
        }
        lastDuplicateEventTime = now;
        lastDuplicateEventCode = code;

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean enabled = sp.getBoolean("autoClick", true);
        if (!enabled) {
            recordKey(getKeyName(code) + " [機能OFF]", code, source, false);
            return false;
        }

        boolean earphoneOnly = sp.getBoolean("earphoneOnly", true);
        boolean earphoneConnected = isEarphoneConnected();

        boolean isVolume = (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN);
        if (isVolume && earphoneOnly && !earphoneConnected) {
            recordKey(getKeyName(code) + " [イヤホン未接続のため除外]", code, source, false);
            return false;
        }

        String keyName = getKeyName(code);

        // 1) 音量キーのダブルタップ判定 (UPまたはDOWN)
        if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            recordKey(keyName, code, source, false);
            if (now - lastVolumeUpTime <= VOLUME_DOUBLE_TAP_MS) {
                lastVolumeUpTime = 0;
                triggerLock("音量UP 2回押し (" + source + ")");
                return true;
            } else {
                lastVolumeUpTime = now;
                return false;
            }
        } else if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            recordKey(keyName, code, source, false);
            if (now - lastVolumeDownTime <= VOLUME_DOUBLE_TAP_MS) {
                lastVolumeDownTime = 0;
                triggerLock("音量DOWN 2回押し (" + source + ")");
                return true;
            } else {
                lastVolumeDownTime = now;
                return false;
            }
        }

        // 2) イヤホン操作キーの判定:
        // イヤホン側で「2回連続押して再生・停止する機能」を利用し、
        // その操作を3秒以内に連続で行うと画面OFF
        if (isMediaKeyCode(code)) {
            long diff = now - lastMediaActionTime;
            if (lastMediaActionTime > 0 && diff <= MEDIA_MULTI_TAP_WINDOW_MS && diff >= 200) {
                // 3秒以内に2回目の操作を受信 -> 画面OFF実行！
                lastMediaActionTime = 0;
                recordKey(keyName + " [2回目検知: 画面OFF実行]", code, source, true);
                triggerLock("ダブルタップ 3秒以内連続 (" + diff + "ms)");
                return true;
            } else {
                // 1回目の操作を受信 -> タイマー開始
                lastMediaActionTime = now;
                recordKey(keyName + " [1回目: 3秒以内に再度操作で画面OFF]", code, source, false);
                return false;
            }
        }

        return false;
    }

    private void triggerLock(String reason) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } else {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }

        vibrateFeedback();
        recordTrigger(reason);
    }

    private void vibrateFeedback() {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (!sp.getBoolean("vibrate", true)) return;

            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(80);
                }
            }
        } catch (Exception ignored) {}
    }

    private void recordKey(String name, int code, String source, boolean triggered) {
        try {
            getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).edit()
                    .putString("last_key_name", name + " (" + code + ")")
                    .putString("last_key_source", source)
                    .putLong("last_key_time", System.currentTimeMillis())
                    .putBoolean("last_key_triggered", triggered)
                    .apply();
        } catch (Exception ignored) {}
    }

    private void recordTrigger(String reason) {
        try {
            getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).edit()
                    .putString("last_trigger_reason", reason)
                    .putLong("last_trigger_time", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    public boolean isEarphoneConnected() {
        return checkEarphoneConnected(this);
    }

    public static boolean checkEarphoneConnected(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (am.isWiredHeadsetOn()) return true;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo d : devices) {
                        int t = d.getType();
                        if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                                || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                                || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || t == AudioDeviceInfo.TYPE_USB_HEADSET
                                || t == AudioDeviceInfo.TYPE_USB_DEVICE
                                || (Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER))) {
                            return true;
                        }
                    }
                } else {
                    if (am.isBluetoothA2dpOn() || am.isBluetoothScoOn()) return true;
                }
            }

            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && a.isEnabled()) {
                if (a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                        || a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED) {
                    return true;
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        if (a.getProfileConnectionState(BluetoothProfile.LE_AUDIO) == BluetoothProfile.STATE_CONNECTED) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String getKeyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_VOLUME_UP: return "音量UP";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "音量DOWN";
            case KeyEvent.KEYCODE_HEADSETHOOK: return "イヤホンボタン (Hook)";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "再生/一時停止";
            case KeyEvent.KEYCODE_MEDIA_PLAY: return "再生";
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "一時停止";
            case KeyEvent.KEYCODE_MEDIA_STOP: return "停止";
            case KeyEvent.KEYCODE_MEDIA_NEXT: return "曲送り (NEXT)";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "曲戻し (PREV)";
            default: return "Key #" + code;
        }
    }

    public static boolean isEnabled(Context c) {
        String id = new ComponentName(c, ShutdownAccessibilityService.class).flattenToString();
        String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        return enabled != null && enabled.toLowerCase(Locale.ROOT).contains(id.toLowerCase(Locale.ROOT));
    }
}
