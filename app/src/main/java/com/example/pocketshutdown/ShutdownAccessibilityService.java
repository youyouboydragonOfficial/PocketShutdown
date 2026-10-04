package com.example.pocketshutdown;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class ShutdownAccessibilityService extends AccessibilityService {
    private static final long DOUBLE_TAP_MS=400, MENU_DELAY_MS=300;
    private long lastVolumeUp=0, lastVolumeDown=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(event.getAction()!=KeyEvent.ACTION_DOWN || event.getRepeatCount()!=0) return false;
        int code=event.getKeyCode(); long now=System.currentTimeMillis();
        if(code==KeyEvent.KEYCODE_VOLUME_UP) { if(now-lastVolumeUp<=DOUBLE_TAP_MS){ trigger(); lastVolumeUp=0; } else lastVolumeUp=now; return true; }
        if(code==KeyEvent.KEYCODE_VOLUME_DOWN) { if(now-lastVolumeDown<=DOUBLE_TAP_MS){ trigger(); lastVolumeDown=0; } else lastVolumeDown=now; return true; }
        return false;
    }
    private void trigger() { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); if(getSharedPreferences("settings",MODE_PRIVATE).getBoolean("autoClick",true)) handler.postDelayed(this::clickPowerOff, MENU_DELAY_MS); }
    private void clickPowerOff() { AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null) return; String[] labels={"電源を切る","Power off","Shut down","Turn off"}; for(String label:labels){ AccessibilityNodeInfo n=find(root,label); if(n!=null){ if(n.isClickable()) n.performAction(AccessibilityNodeInfo.ACTION_CLICK); else if(n.getParent()!=null) n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK); return; } } }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String label){ for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(label)) if(n!=null) return n; return null; }
    public static boolean isEnabled(Context c){ String id=new ComponentName(c,ShutdownAccessibilityService.class).flattenToString(); String enabled=android.provider.Settings.Secure.getString(c.getContentResolver(),"enabled_accessibility_services"); return enabled!=null && enabled.toLowerCase(Locale.ROOT).contains(id.toLowerCase(Locale.ROOT)); }
}
