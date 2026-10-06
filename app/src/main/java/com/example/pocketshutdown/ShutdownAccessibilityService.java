package com.example.pocketshutdown;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.Manifest;
import android.os.Build;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Locale;

public class ShutdownAccessibilityService extends AccessibilityService {
    private static final long DOUBLE_TAP_MS=400, MENU_DELAY_MS=300;
    private long lastVolumeUp=0, lastVolumeDown=0;
    private int lastDeviceId=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaSession mediaSession;
    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info=getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        mediaSession=new MediaSession(this,"PocketShutdownInput");
        mediaSession.setCallback(new MediaSession.Callback(){@Override public boolean onMediaButtonEvent(Intent intent){KeyEvent e=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);if(e!=null&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_MEDIA_NEXT||k==KeyEvent.KEYCODE_MEDIA_PREVIOUS||k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){handleExternalDoubleTap(k);return true;}}return false;}});
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1).build());
        mediaSession.setActive(true);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy(){if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();}super.onDestroy();}
    @Override protected boolean onKeyEvent(KeyEvent event) {
        // 緊急安全措置: Android端末の物理キーとBluetoothキーを
        // onKeyEvent上で確実に区別できない端末があるため、音量キーは処理しない。
        // Bluetoothのメディアキー経路のみ handleExternalDoubleTap() で扱う。
        return false;
    }
    private long lastMediaTime=0; private int lastMediaCode=-1;
    private void handleExternalDoubleTap(int code){if(!bluetoothAudioConnected())return;recordKey("メディアキー");long now=System.currentTimeMillis();if(code==lastMediaCode&&now-lastMediaTime<=DOUBLE_TAP_MS){trigger();lastMediaTime=0;}else{lastMediaCode=code;lastMediaTime=now;}}
    private void recordKey(String name){getSharedPreferences("diagnostics",MODE_PRIVATE).edit().putString("last_key_name",name).putLong("last_key_time",System.currentTimeMillis()).apply();}
    private void trigger() { if(!getSharedPreferences("settings",MODE_PRIVATE).getBoolean("autoClick",false)) return; if(!bluetoothAudioConnected()) return; performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); }
    private boolean bluetoothAudioConnected() {
        try {
            if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter(); if(a==null || !a.isEnabled()) return false;
            boolean audio=a.getProfileConnectionState(BluetoothProfile.A2DP)==BluetoothProfile.STATE_CONNECTED || a.getProfileConnectionState(BluetoothProfile.HEADSET)==BluetoothProfile.STATE_CONNECTED;
            if(Build.VERSION.SDK_INT>=31) audio=audio || a.getProfileConnectionState(BluetoothProfile.LE_AUDIO)==BluetoothProfile.STATE_CONNECTED;
            return audio;
        } catch(RuntimeException e) { return false; }
    }
    public static boolean isEnabled(Context c){ String id=new ComponentName(c,ShutdownAccessibilityService.class).flattenToString(); String enabled=android.provider.Settings.Secure.getString(c.getContentResolver(),"enabled_accessibility_services"); return enabled!=null && enabled.toLowerCase(Locale.ROOT).contains(id.toLowerCase(Locale.ROOT)); }
}
