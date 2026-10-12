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
import android.os.Handler;
import android.os.Looper;
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

    // Bluetooth通信の伝送遅延・実機での操作感を考慮したダブルタップ許容時間（ミリ秒）
    private static final long DOUBLE_TAP_MS = 650;

    private long lastVolumeUpTime = 0;
    private long lastVolumeDownTime = 0;
    private long lastMediaTime = 0;
    private int lastMediaCode = -1;

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
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
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
        boolean isMediaKey = (code == KeyEvent.KEYCODE_HEADSETHOOK
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_STOP
                || code == KeyEvent.KEYCODE_MEDIA_NEXT
                || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        if (!isVolumeKey && !isMediaKey) {
            return false;
        }

        return handleKeyInput(code, "Accessibility");
    }

    private boolean handleKeyInput(int code, String source) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean enabled = sp.getBoolean("autoClick", true);
        if (!enabled) {
            recordKey(getKeyName(code) + " [機能OFF]", code, source);
            return false;
        }

        boolean earphoneOnly = sp.getBoolean("earphoneOnly", true);
        boolean earphoneConnected = isEarphoneConnected();

        boolean isVolume = (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN);
        if (isVolume && earphoneOnly && !earphoneConnected) {
            recordKey(getKeyName(code) + " [イヤホン未接続のため除外]", code, source);
            return false;
        }

        long now = System.currentTimeMillis();
        String keyName = getKeyName(code);
        recordKey(keyName, code, source);

        // 1) 音量キーの2回押し判定
        if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            if (now - lastVolumeUpTime <= DOUBLE_TAP_MS) {
                lastVolumeUpTime = 0;
                triggerLock("音量UP ダブルタップ (" + source + ")");
                return true;
            } else {
                lastVolumeUpTime = now;
                return false;
            }
        } else if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (now - lastVolumeDownTime <= DOUBLE_TAP_MS) {
                lastVolumeDownTime = 0;
                triggerLock("音量DOWN ダブルタップ (" + source + ")");
                return true;
            } else {
                lastVolumeDownTime = now;
                return false;
            }
        }

        // 2) イヤホン側がダブルタップ/トリプルタップを「曲送り/曲戻し」として送信する機種へのサポート
        boolean triggerOnNextPrev = sp.getBoolean("triggerOnNextPrev", true);
        if (triggerOnNextPrev && (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            triggerLock(keyName + " 操作 (" + source + ")");
            return true;
        }

        // 3) HEADSETHOOK（イヤホンボタン）やメディア再生停止キーのダブルタップ判定
        if (code == lastMediaCode && (now - lastMediaTime <= DOUBLE_TAP_MS)) {
            lastMediaCode = -1;
            lastMediaTime = 0;
            triggerLock(keyName + " ダブルタップ (" + source + ")");
            return true;
        } else {
            lastMediaCode = code;
            lastMediaTime = now;
            return false;
        }
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

    private void recordKey(String name, int code, String source) {
        try {
            getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).edit()
                    .putString("last_key_name", name + " (" + code + ")")
                    .putString("last_key_source", source)
                    .putLong("last_key_time", System.currentTimeMillis())
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
            case KeyEvent.KEYCODE_HEADSETHOOK: return "イヤホン操作ボタン (Hook)";
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
