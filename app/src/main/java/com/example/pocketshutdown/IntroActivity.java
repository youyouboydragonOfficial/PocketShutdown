package com.example.pocketshutdown;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class IntroActivity extends Activity implements TextureView.SurfaceTextureListener {
    private MediaPlayer player; private TextureView texture; private Surface videoSurface; private boolean opened, prepared;
    private final Handler handler=new Handler(); private final Runnable fallback=this::openMain;
    @Override public void onCreate(Bundle state){super.onCreate(state);try{requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setNavigationBarColor(0xff000000);texture=new TextureView(this);texture.setOpaque(false);texture.setBackgroundColor(0xff000000);texture.setKeepScreenOn(true);texture.setSurfaceTextureListener(this);texture.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);setContentView(texture);handler.postDelayed(fallback,6500);}catch(Throwable e){openMain();}}
    @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st,int width,int height){try{videoSurface=new Surface(st);player=new MediaPlayer();player.setDataSource(this,Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.intro_splash));player.setSurface(videoSurface);player.setOnPreparedListener(mp->{try{prepared=true;mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);mp.start();texture.invalidate();}catch(Throwable e){openMain();}});player.setOnCompletionListener(mp->openMain());player.setOnErrorListener((mp,what,extra)->{openMain();return true;});player.prepareAsync();}catch(Throwable e){openMain();}}
    @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st,int width,int height){}
    @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st){}
    private void releasePlayer(){if(player!=null){try{if(prepared)player.stop();}catch(Throwable ignored){}try{player.reset();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;prepared=false;}if(videoSurface!=null){try{videoSurface.release();}catch(Throwable ignored){}videoSurface=null;}}
    private void openMain(){if(opened)return;opened=true;handler.removeCallbacks(fallback);releasePlayer();try{startActivity(new Intent(this,MainActivity.class));}catch(Throwable ignored){}finish();}
}
