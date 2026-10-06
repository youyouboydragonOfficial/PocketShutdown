package com.example.pocketshutdown;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

public class IntroActivity extends Activity implements TextureView.SurfaceTextureListener {
    private MediaPlayer player; private TextureView texture; private boolean opened;
    private final Handler handler=new Handler(); private final Runnable fallback=this::openMain;
    @Override public void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setNavigationBarColor(0xff000000);texture=new TextureView(this);texture.setBackgroundColor(0xff000000);texture.setKeepScreenOn(true);texture.setSurfaceTextureListener(this);texture.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);setContentView(texture);handler.postDelayed(fallback,6500);}
    @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st,int width,int height){try{player=new MediaPlayer();player.setDataSource(this,Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.intro_splash));player.setSurface(new Surface(st));player.setOnPreparedListener(mp->{mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);mp.start();});player.setOnCompletionListener(mp->openMain());player.setOnErrorListener((mp,what,extra)->{openMain();return true;});player.prepareAsync();}catch(Exception e){openMain();}}
    @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st,int width,int height){}
    @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st){}
    private void releasePlayer(){if(player!=null){player.stop();player.reset();player.release();player=null;}}
    private void openMain(){if(opened)return;opened=true;handler.removeCallbacks(fallback);releasePlayer();startActivity(new Intent(this,MainActivity.class));finish();}
}
