package com.example.pocketshutdown;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Environment;
import android.os.Build;
import android.content.pm.PackageInfo;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import android.animation.ValueAnimator;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String PREFS="settings";
    int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    TextView status;
    TextView updateStatus;
    @Override public void onCreate(Bundle b) { super.onCreate(b); build(); }
    TextView text(String s, float size, int color) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); return v; }
    GradientDrawable bg(int color, float r) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    void build() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(38),dp(24),dp(28)); root.setBackgroundColor(Color.rgb(9,11,22));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
            int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setPadding(dp(24), dp(24) + top, dp(24), dp(18) + bottom);
            return insets;
        });
        TextView title=text("POCKET SHUTDOWN",12,0xffaaa6c8); title.setLetterSpacing(.22f); root.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
        FrameLayout hero=new FrameLayout(this); hero.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
        SignalAnimationView signal=new SignalAnimationView(this); hero.addView(signal,new FrameLayout.LayoutParams(-1,-1));
        TextView head=text("ポケットの中から、\n電源をオフ",28,Color.WHITE); head.setGravity(Gravity.CENTER); head.setTypeface(null,1); FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,dp(100),Gravity.BOTTOM); hp.bottomMargin=dp(8); hero.addView(head,hp); root.addView(hero);
        TextView sub=text("Bluetoothイヤホンの音量ボタンを\n400ms以内に2回押すだけ",15,0xffa9a8bc); sub.setGravity(Gravity.CENTER); root.addView(sub,new LinearLayout.LayoutParams(-1,dp(55)));
        status=text("●  サービス未接続",14,0xffffb86b); status.setGravity(Gravity.CENTER); status.setPadding(0,dp(12),0,dp(12)); root.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));
        Button settings=new Button(this); settings.setText("アクセシビリティを設定"); settings.setTextColor(Color.WHITE); settings.setTextSize(15); settings.setAllCaps(false); settings.setBackground(bg(0xff5547b8,18)); settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(settings,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout autoRow=new LinearLayout(this); autoRow.setGravity(Gravity.CENTER_VERTICAL); autoRow.setPadding(dp(14),0,dp(8),0); autoRow.setBackground(bg(0xff15172a,16));
        TextView autoLabel=text("シャットダウンを自動実行",15,Color.WHITE); autoRow.addView(autoLabel,new LinearLayout.LayoutParams(0,dp(54),1));
        Switch auto=new Switch(this); auto.setChecked(getPreferences(0).getBoolean("autoClick",true)); auto.setOnCheckedChangeListener((button, checked)->getPreferences(0).edit().putBoolean("autoClick",checked).apply()); autoRow.addView(auto); root.addView(autoRow,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView note=text("電源メニューの表示後に「電源を切る」を自動選択します。\n端末メーカーにより自動選択できない場合があります。",12,0xff77768a); note.setGravity(Gravity.CENTER); note.setPadding(0,dp(8),0,0); root.addView(note,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView terms=text("チュートリアル・使い方・利用規約を表示",12,0xffaaa6c8); terms.setGravity(Gravity.CENTER); terms.setOnClickListener(v->showGuide(true)); root.addView(terms,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView creator=text("©youyouboydragon",11,0xff5e5d70); creator.setGravity(Gravity.CENTER); root.addView(creator,new LinearLayout.LayoutParams(-1,dp(26)));
        updateStatus=text("GitHubの最新リリースを確認できます",11,0xff77768a); updateStatus.setGravity(Gravity.CENTER); root.addView(updateStatus,new LinearLayout.LayoutParams(-1,dp(30)));
        Button update=new Button(this); update.setText("アップデートを確認"); update.setTextSize(13); update.setAllCaps(false); update.setTextColor(0xffd9d5ff); update.setBackground(bg(0xff242044,16)); update.setOnClickListener(v->checkForUpdate()); root.addView(update,new LinearLayout.LayoutParams(-1,dp(46)));
        setContentView(root); refresh();
        if(!getPreferences(0).getBoolean("onboarded",false)) root.postDelayed(()->showGuide(false),350);
        root.postDelayed(this::checkForUpdate,700);
    }
    static class SignalAnimationView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); final RectF r=new RectF(); float phase;
        SignalAnimationView(android.content.Context c){ super(c); p.setStrokeCap(Paint.Cap.ROUND); setLayerType(View.LAYER_TYPE_SOFTWARE,null); ValueAnimator a=ValueAnimator.ofFloat(0,1); a.setDuration(2200); a.setRepeatCount(ValueAnimator.INFINITE); a.addUpdateListener(v->{phase=(float)v.getAnimatedValue(); invalidate();}); a.start(); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); float cx=getWidth()/2f, cy=getHeight()/2f-18, d=Math.min(getWidth(),getHeight());
            p.setStyle(Paint.Style.FILL); p.setColor(0xff13162b); r.set(cx-d*.34f,cy-d*.34f,cx+d*.34f,cy+d*.34f); c.drawRoundRect(r,dpStatic(getContext(),30),dpStatic(getContext(),30),p);
            for(int i=0;i<3;i++){ float t=(phase+i/3f)%1f; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dpStatic(getContext(),2)); p.setColor(Color.argb((int)(90*(1-t)),139,124,255)); c.drawCircle(cx,cy,d*(.22f+t*.23f),p); }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dpStatic(getContext(),6)); p.setColor(0xff8b7cff); r.set(cx-42,cy-28,cx+42,cy+48); c.drawArc(r,210,120,false,p); c.drawLine(cx-42,cy+10,cx-42,cy+35,p); c.drawLine(cx+42,cy+10,cx+42,cy+35,p);
            p.setColor(Color.WHITE); p.setStrokeWidth(dpStatic(getContext(),4)); c.drawLine(cx,cy-44,cx,cy-5,p); c.drawArc(new RectF(cx-27,cy-27,cx+27,cy+27),-42,264,false,p);
        }
        static int dpStatic(android.content.Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    }
    void refresh() { boolean on=ShutdownAccessibilityService.isEnabled(this); if(status!=null){status.setText(on?"●  監視中 — 準備完了":"●  サービス未接続"); status.setTextColor(on?0xff74e0b0:0xffffb86b);} }
    void showGuide(boolean manual) {
        new AlertDialog.Builder(this).setTitle("チュートリアル").setMessage("Pocket Shutdownへようこそ。\n\nBluetoothイヤホンの音量ボタンを400ms以内に2回押すと、電源メニューを呼び出します。アクセシビリティ設定を有効にしてからご利用ください。\n\n次に「使い方」と「利用規約」を確認します。")
            .setPositiveButton("使い方へ",(d,w)->showHowTo()).setNegativeButton("閉じる",null).show();
    }
    void showHowTo() {
        new AlertDialog.Builder(this).setTitle("使い方").setMessage("1. アクセシビリティを設定をタップ\n2. Pocket ShutdownをONにする\n3. 同じ音量ボタンを素早く2回押す\n\n自動クリックをOFFにすると、電源メニューを表示するだけになります。")
            .setPositiveButton("利用規約へ",(d,w)->showTerms()).show();
    }
    void showTerms() {
        new AlertDialog.Builder(this).setTitle("利用規約・注意事項").setMessage("本アプリは利用者自身の端末上で、利用者の明示操作を補助するツールです。\n\n・ダブルタップで電源が切れるため、誤操作に注意してください。\n・端末メーカー、Androidバージョン、Bluetooth機器によりキー入力や自動クリックが動作しない場合があります。\n・自動シャットダウン前に、必要なデータを保存してください。\n・作者はデータ消失、予期せぬシャットダウン、サービス停止等について責任を負いません。\n\n©youyouboydragon")
            .setPositiveButton("同意して始める",(d,w)->getPreferences(0).edit().putBoolean("onboarded",true).apply()).setCancelable(false).show();
    }
    void checkForUpdate() {
        if(updateStatus!=null) updateStatus.setText("GitHub Releasesを確認中…");
        new Thread(()->{ try {
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/youyouboydragonOfficial/PocketShutdown/releases/latest").openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setRequestProperty("Accept","application/vnd.github+json");
            InputStream in=c.getInputStream(); java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1)b.write(buf,0,n); in.close(); JSONObject release=new JSONObject(new String(b.toByteArray(),"UTF-8")); String tag=release.optString("tag_name"); String apkUrl=null; org.json.JSONArray assets=release.optJSONArray("assets"); if(assets!=null) for(int i=0;i<assets.length();i++){ JSONObject a=assets.getJSONObject(i); if(a.optString("name").endsWith(".apk")){apkUrl=a.optString("browser_download_url");break;} } String current=getPackageManager().getPackageInfo(getPackageName(),0).versionName; boolean newer=compareVersion(tag.replace("v", ""),current)>0; final String downloadUrl=apkUrl; runOnUiThread(()->{ if(newer&&downloadUrl!=null){updateStatus.setText("新しいバージョンがあります: "+tag); new AlertDialog.Builder(this).setTitle("アップデート available").setMessage("GitHubに新しいAPKがあります。ダウンロードして更新しますか？").setPositiveButton("ダウンロード",(d,w)->downloadUpdate(downloadUrl)).setNegativeButton("あとで",null).show();} else updateStatus.setText("最新バージョンを使用中  •  "+current); });
        }catch(Exception e){runOnUiThread(()->updateStatus.setText("更新確認を完了できませんでした"));} }).start();
    }
    int compareVersion(String a,String b){ try {String[] x=a.split("\\."),y=b.split("\\."); for(int i=0;i<Math.max(x.length,y.length);i++){int p=i<x.length?Integer.parseInt(x[i]):0,q=i<y.length?Integer.parseInt(y[i]):0;if(p!=q)return p>q?1:-1;}}catch(Exception ignored){} return 0; }
    void downloadUpdate(String url){ updateStatus.setText("APKをダウンロード中…"); new Thread(()->{try{File dir=new File(getCacheDir(),"updates");dir.mkdirs();File apk=new File(dir,"PocketShutdown-update.apk");HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(30000);InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(apk);byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();out.close();runOnUiThread(()->installApk(apk));}catch(Exception e){runOnUiThread(()->updateStatus.setText("APKのダウンロードに失敗しました"));}}).start(); }
    void installApk(File apk){ try { Uri uri=Uri.parse("content://com.example.pocketshutdown.fileprovider/update"); Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){updateStatus.setText("インストーラーを開けませんでした");} }
    @Override protected void onResume(){super.onResume(); refresh();}
}
