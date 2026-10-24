package com.example.pocketshutdown;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.Manifest;
import java.util.Locale;

public class ShutdownAccessibilityService extends AccessibilityService {
    public static final String PREFS = "settings";
    public static final String DIAG_PREFS = "diagnostics";

    public static final long MULTI_TAP_WINDOW_MS = 3000; // 3秒以内

    private static volatile ShutdownAccessibilityService instance;

    private long lastVolumeActionTime = 0;
    private long lastMediaActionTime = 0;

    private long lastDuplicateEventTime = 0;
    private int lastDuplicateEventCode = -1;

    private MediaSession mediaSession;
    private AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ContentObserver volumeObserver;
    private BroadcastReceiver volumeReceiver;
    private int lastMusicVolume = -1;
    private long lastVolumeChangeTime = 0;
    private int initialVolumeBeforeLock = -1;

    public static ShutdownAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                info = new AccessibilityServiceInfo();
            }
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            setServiceInfo(info);
        } catch (Exception ignored) {}

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setupMediaSession();
        setupVolumeObserver();
    }

    private void setupVolumeObserver() {
        try {
            if (audioManager != null) {
                lastMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            }

            // 1. ContentObserver で音量変化を監視（Bluetoothイヤホンの絶対音量変更を確実にキャッチ）
            if (volumeObserver == null) {
                volumeObserver = new ContentObserver(mainHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        checkVolumeChanged("ContentObserver");
                    }
                };
                getContentResolver().registerContentObserver(
                        Settings.System.CONTENT_URI, true, volumeObserver);
            }

            // 2. BroadcastReceiver ("android.media.VOLUME_CHANGED_ACTION") で音量変化を二重監視
            if (volumeReceiver == null) {
                volumeReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent != null && "android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                            int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                            if (streamType == AudioManager.STREAM_MUSIC) {
                                checkVolumeChanged("VolumeReceiver");
                            }
                        }
                    }
                };
                IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(volumeReceiver, filter);
                }
            }
        } catch (Exception ignored) {}
    }

    private void checkVolumeChanged(String source) {
        if (audioManager == null) return;
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVol == lastMusicVolume) {
            return;
        }
        int prevVol = lastMusicVolume;
        lastMusicVolume = currentVol;

        // 画面が既にOFF（スリープ中）の場合は一切処理しない（誤動作防止）
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            return;
        }

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("autoClick", true)) {
            return;
        }

        // イヤホン接続中のみ発動（本体ボタン誤爆防止）
        if (!isEarphoneConnected()) {
            return;
        }

        long now = System.currentTimeMillis();
        long diff = now - lastVolumeChangeTime;

        if (lastVolumeChangeTime > 0 && diff <= MULTI_TAP_WINDOW_MS && diff >= 150) {
            lastVolumeChangeTime = 0;
            recordKey("イヤホン音量操作 (" + prevVol + "→" + currentVol + ") [2回目: 画面OFF実行]", 24, source, true);

            // ロック実行前に元の音量にスマート復元（音が上がりすぎないようにする）
            if (initialVolumeBeforeLock >= 0) {
                final int restoreVol = initialVolumeBeforeLock;
                initialVolumeBeforeLock = -1;
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0);
                    lastMusicVolume = restoreVol;
                } catch (Exception ignored) {}
            }

            triggerLock("イヤホン音量ダブルタップ (" + diff + "ms)");
        } else {
            lastVolumeChangeTime = now;
            initialVolumeBeforeLock = prevVol;
            recordKey("イヤホン音量操作 (" + prevVol + "→" + currentVol + ") [1回目: 3秒以内に再度操作で画面OFF]", 24, source, false);
        }
    }

    private void setupMediaSession() {
        try {
            if (mediaSession != null) {
                mediaSession.release();
            }
            mediaSession = new MediaSession(this, "PocketLockInput");

            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setClass(this, MediaButtonBroadcastReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
            mediaSession.setMediaButtonReceiver(pendingIntent);

            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public boolean onMediaButtonEvent(Intent intent) {
                    if (intent != null) {
                        KeyEvent e = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                        if (e != null && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                            return handleKeyInput(e.getKeyCode(), "MediaSession");
                        }
                    }
                    return super.onMediaButtonEvent(intent);
                }

                @Override
                public void onPlay() {
                    handleKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY, "MediaSession.onPlay");
                }

                @Override
                public void onPause() {
                    handleKeyInput(KeyEvent.KEYCODE_MEDIA_PAUSE, "MediaSession.onPause");
                }

                @Override
                public void onSkipToNext() {
                    handleKeyInput(KeyEvent.KEYCODE_MEDIA_NEXT, "MediaSession.onSkipToNext");
                }

                @Override
                public void onSkipToPrevious() {
                    handleKeyInput(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "MediaSession.onSkipToPrevious");
                }
            });

            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);

            // メディアボタン受信をOSに許可させるため有効なアクションを指定
            long actions = PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PAUSED, 0, 0f)
                    .setActions(actions)
                    .build());

            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean enabled = sp.getBoolean("autoClick", true);
            mediaSession.setActive(enabled);

            try {
                ComponentName component = new ComponentName(this, MediaButtonBroadcastReceiver.class);
                audioManager.registerMediaButtonEventReceiver(component);
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }

    public void pauseService() {
        if (mediaSession != null) {
            try { mediaSession.setActive(false); } catch (Exception ignored) {}
        }
        lastMediaActionTime = 0;
        lastVolumeActionTime = 0;
        lastVolumeChangeTime = 0;
    }

    public void resumeService() {
        if (mediaSession != null) {
            try { mediaSession.setActive(true); } catch (Exception ignored) {}
        }
    }

    public static void stopAll(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("autoClick", false)
                .putBoolean("volumeDoubleTap", false)
                .putBoolean("vibrate", false)
                .apply();
        if (instance != null) {
            instance.pauseService();
        }
    }

    public static void restoreDefaults(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("autoClick", true)
                .putBoolean("volumeDoubleTap", true)
                .putBoolean("earphoneOnly", true)
                .putBoolean("vibrate", true)
                .apply();
        if (instance != null) {
            instance.resumeService();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        if (mediaSession != null) {
            try { mediaSession.setActive(false); mediaSession.release(); } catch (Exception ignored) {}
            mediaSession = null;
        }
        if (volumeObserver != null) {
            try { getContentResolver().unregisterContentObserver(volumeObserver); } catch (Exception ignored) {}
            volumeObserver = null;
        }
        if (volumeReceiver != null) {
            try { unregisterReceiver(volumeReceiver); } catch (Exception ignored) {}
            volumeReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            return false;
        }

        // 画面が既にOFF（スリープ中）の場合は一切処理しない（電源ボタンOFF後の誤動作を完全防止）
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            return false;
        }

        int code = event.getKeyCode();
        boolean isVolumeKey = (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN);
        boolean isMediaKey = isMediaKeyCode(code);

        if (!isVolumeKey && !isMediaKey) {
            return false;
        }

        // 本体の音量キーでの画面OFFは完全撤廃！イヤホン接続時のみ処理する
        if (isVolumeKey) {
            if (!isEarphoneConnected()) {
                return false; // 本体音量キーは完全に無視
            }
        }

        return handleKeyInput(code, "Accessibility");
    }

    public static void onExternalMediaButton(Context context, int code, String source) {
        if (instance != null) {
            instance.handleKeyInput(code, source);
        } else {
            handleStandaloneMediaButton(context, code, source);
        }
    }

    private static void handleStandaloneMediaButton(Context context, int code, String source) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("autoClick", true)) return;

        SharedPreferences diag = context.getSharedPreferences(DIAG_PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastTime = diag.getLong("last_media_tap_time", 0);
        long diff = now - lastTime;

        if (lastTime > 0 && diff <= MULTI_TAP_WINDOW_MS && diff >= 150) {
            diag.edit()
                    .putLong("last_media_tap_time", 0)
                    .putString("last_key_name", "イヤホン操作 (" + code + ") [2回目検知 → ロック要求]")
                    .putString("last_key_source", source)
                    .putLong("last_key_time", now)
                    .putBoolean("last_key_triggered", true)
                    .apply();

            if (instance != null) {
                instance.triggerLock("外部レシーバー経由 (" + diff + "ms)");
            }
        } else {
            diag.edit()
                    .putLong("last_media_tap_time", now)
                    .putString("last_key_name", "イヤホン操作 (" + code + ") [1回目: 3秒以内に再度操作で画面OFF]")
                    .putString("last_key_source", source)
                    .putLong("last_key_time", now)
                    .putBoolean("last_key_triggered", false)
                    .apply();
        }
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

    public boolean handleKeyInput(int code, String source) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            return false;
        }

        long now = System.currentTimeMillis();

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

        // イヤホン接続中のみ実行（本体誤爆防止）
        if (!isEarphoneConnected()) {
            return false;
        }

        boolean isVolume = (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN);
        boolean isMedia = isMediaKeyCode(code);

        if (!isVolume && !isMedia) {
            return false;
        }

        String keyName = getKeyName(code);

        // 1) 音量キーの判定 (イヤホンからのKeyEvent)
        if (isVolume) {
            long diff = now - lastVolumeActionTime;
            if (lastVolumeActionTime > 0 && diff <= MULTI_TAP_WINDOW_MS && diff >= 150) {
                lastVolumeActionTime = 0;
                recordKey(keyName + " [2回目検知: 画面OFF実行]", code, source, true);
                triggerLock(keyName + " 3秒以内連続2回 (" + diff + "ms)");
                return true;
            } else {
                lastVolumeActionTime = now;
                recordKey(keyName + " [1回目: 3秒以内に再度操作で画面OFF]", code, source, false);
                return false;
            }
        }

        // 2) メディア操作キーの判定 (イヤホンの1回タップ＝再生/停止など)
        if (isMedia) {
            long diff = now - lastMediaActionTime;
            if (lastMediaActionTime > 0 && diff <= MULTI_TAP_WINDOW_MS && diff >= 150) {
                lastMediaActionTime = 0;
                recordKey(keyName + " [2回目検知: 画面OFF実行]", code, source, true);
                triggerLock(keyName + " 3秒以内連続2回 (" + diff + "ms)");
                return true;
            } else {
                lastMediaActionTime = now;
                recordKey(keyName + " [1回目: 3秒以内に再度操作で画面OFF]", code, source, false);
                return false;
            }
        }

        return false;
    }

    public void triggerLock(String reason) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            return;
        }

        mainHandler.post(() -> {
            boolean success = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            } else {
                success = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            }

            vibrateFeedback();
            recordTrigger(reason + (success ? " [成功]" : " [アクション未実行]"));
        });
    }

    public static boolean triggerLockManual(Context context) {
        if (instance != null) {
            instance.triggerLock("手動テスト実行");
            return true;
        }
        return false;
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
