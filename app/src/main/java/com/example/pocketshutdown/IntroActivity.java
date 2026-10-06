package com.example.pocketshutdown;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

public class IntroActivity extends Activity {
    private boolean opened;
    private final Handler handler=new Handler();
    @Override public void onCreate(Bundle state){ super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE); getWindow().setFlags(WindowManagerFlags.FULLSCREEN,WindowManagerFlags.FULLSCREEN); getWindow().setNavigationBarColor(0xff000000); VideoView video=new VideoView(this); video.setBackgroundColor(0xff000000); video.setKeepScreenOn(true); video.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION); setContentView(video); Uri uri=Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.intro_splash); video.setVideoURI(uri); video.setOnPreparedListener(mp->{mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);video.start();}); video.setOnCompletionListener(mp->openMain()); video.setOnErrorListener((mp,what,extra)->{openMain();return true;}); handler.postDelayed(this::openMain,8000); }
    private void openMain(){if(opened)return;opened=true;handler.removeCallbacksAndMessages(null);startActivity(new Intent(this,MainActivity.class));finish();}
    static final class WindowManagerFlags { static final int FULLSCREEN=WindowManager.LayoutParams.FLAG_FULLSCREEN; }
}
