package com.example.pocketshutdown;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class IntroActivity extends Activity implements SurfaceHolder.Callback {
    private MediaPlayer player;
    private SurfaceView surfaceView;
    private boolean opened;
    private final Handler handler=new Handler();
    private final Runnable fallback=this::openMain;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().setNavigationBarColor(0xff000000);
            surfaceView=new SurfaceView(this);
            surfaceView.setKeepScreenOn(true);
            surfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            surfaceView.getHolder().addCallback(this);
            setContentView(surfaceView);
            handler.postDelayed(fallback,8000);
        }catch(Throwable error){openMain();}
    }

    @Override public void surfaceCreated(SurfaceHolder holder){
        try{
            player=new MediaPlayer();
            player.setDisplay(holder);
            player.setDataSource(this,Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.intro_splash));
            player.setOnPreparedListener(mp->{try{mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);mp.start();}catch(Throwable error){openMain();}});
            player.setOnCompletionListener(mp->openMain());
            player.setOnErrorListener((mp,what,extra)->{openMain();return true;});
            player.prepareAsync();
        }catch(Throwable error){openMain();}
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){if(player!=null)player.setDisplay(holder);}
    @Override public void surfaceDestroyed(SurfaceHolder holder){releasePlayer();}
    private void releasePlayer(){if(player!=null){try{if(player.isPlaying())player.stop();}catch(Throwable ignored){}try{player.reset();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}}
    private void openMain(){if(opened)return;opened=true;handler.removeCallbacks(fallback);releasePlayer();try{startActivity(new Intent(this,MainActivity.class));}catch(Throwable ignored){}finish();}
}
