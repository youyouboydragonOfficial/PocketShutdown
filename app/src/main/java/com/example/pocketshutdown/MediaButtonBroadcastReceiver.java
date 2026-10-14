package com.example.pocketshutdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class MediaButtonBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                int keyCode = event.getKeyCode();
                ShutdownAccessibilityService.onExternalMediaButton(context, keyCode, "MediaButtonReceiver");
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
            }
        }
    }
}
