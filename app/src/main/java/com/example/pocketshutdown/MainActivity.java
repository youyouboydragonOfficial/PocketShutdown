package com.example.pocketshutdown;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.Manifest;
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
import android.os.PowerManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
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
    TextView bluetoothStatus;
    TextView keyStatus;
    TextView updateStatus;
    Dialog updateDialog;
    Dialog accessibilityGate;
    @Override public void onCreate(Bundle b) { super.onCreate(b); build(); requestBluetoothPermission(); }
    void requestBluetoothPermission(){ if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},41); }
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
        status=text("●  サービス未接続",14,0xffffb86b); status.setGravity(Gravity.CENTER); status.setPadding(0,dp(12),0,dp(4)); root.addView(status,new LinearLayout.LayoutParams(-1,dp(40)));
        bluetoothStatus=text("Bluetooth: 接続状態を確認中…",13,0xffa9a8bc); bluetoothStatus.setGravity(Gravity.CENTER); root.addView(bluetoothStatus,new LinearLayout.LayoutParams(-1,dp(34)));
        keyStatus=text("入力診断: まだキー入力を受信していません",12,0xff77768a); keyStatus.setGravity(Gravity.CENTER); root.addView(keyStatus,new LinearLayout.LayoutParams(-1,dp(30)));
        Button settings=new Button(this); settings.setText("アクセシビリティを設定"); settings.setTextColor(Color.WHITE); settings.setTextSize(15); settings.setAllCaps(false); settings.setBackground(bg(0xff5547b8,18)); settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(settings,new LinearLayout.LayoutParams(-1,dp(54)));
        Button battery=new Button(this); battery.setText("バックグラウンド維持設定"); battery.setTextColor(0xffd9d5ff); battery.setTextSize(13); battery.setAllCaps(false); battery.setBackground(bg(0xff242044,18)); battery.setOnClickListener(v->openBatterySettings()); root.addView(battery,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout autoRow=new LinearLayout(this); autoRow.setGravity(Gravity.CENTER_VERTICAL); autoRow.setPadding(dp(14),0,dp(8),0); autoRow.setBackground(bg(0xff15172a,16));
        TextView autoLabel=text("イヤホン2回タップで画面OFF",15,Color.WHITE); autoRow.addView(autoLabel,new LinearLayout.LayoutParams(0,dp(54),1));
        Switch auto=new Switch(this); auto.setChecked(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("autoClick",false)); auto.setOnCheckedChangeListener((button, checked)->getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("autoClick",checked).apply()); autoRow.addView(auto); root.addView(autoRow,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView note=text("Bluetoothイヤホンの2回タップで画面をOFF・ロックします。\n必要な設定はユーザー補助とBluetooth接続です。",12,0xff77768a); note.setGravity(Gravity.CENTER); note.setPadding(0,dp(8),0,0); root.addView(note,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView terms=text("チュートリアル・使い方・利用規約を表示",12,0xffaaa6c8); terms.setGravity(Gravity.CENTER); terms.setOnClickListener(v->showGuide(true)); root.addView(terms,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView creator=text("©youyouboydragon",11,0xff5e5d70); creator.setGravity(Gravity.CENTER); root.addView(creator,new LinearLayout.LayoutParams(-1,dp(26)));
        updateStatus=text("GitHubの最新リリースを確認できます",11,0xff77768a); updateStatus.setGravity(Gravity.CENTER); root.addView(updateStatus,new LinearLayout.LayoutParams(-1,dp(30)));
        Button update=new Button(this); update.setText("アップデートを確認"); update.setTextSize(13); update.setAllCaps(false); update.setTextColor(0xffd9d5ff); update.setBackground(bg(0xff242044,16)); update.setOnClickListener(v->checkForUpdate()); root.addView(update,new LinearLayout.LayoutParams(-1,dp(46)));
        setContentView(root); refresh(); refreshBluetoothStatus(); refreshDiagnostics();
        root.postDelayed(()->continueAfterAccessibility(root),350);
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
        new AlertDialog.Builder(this).setTitle("チュートリアル").setMessage("Pocket Shutdownへようこそ。\n\nBluetoothイヤホンのメディア操作を400ms以内に2回押すと、画面をOFFにして端末をロックします。アクセシビリティ設定を有効にしてからご利用ください。\n\n次に「使い方」と「利用規約」を確認します。")
            .setPositiveButton("使い方へ",(d,w)->showHowTo()).setNegativeButton("閉じる",null).show();
    }
    void showHowTo() {
        new AlertDialog.Builder(this).setTitle("使い方").setMessage("1. アクセシビリティを設定をタップ\n2. Pocket ShutdownをONにする\n3. Bluetoothイヤホンのメディア操作を素早く2回押す\n\n「イヤホン2回タップで画面OFF」をOFFにすると、画面ロックは実行されません。")
            .setPositiveButton("利用規約へ",(d,w)->showTerms()).show();
    }
    void showTerms() {
        new AlertDialog.Builder(this).setTitle("利用規約・注意事項").setMessage("本アプリは利用者自身の端末上で、利用者の明示操作を補助するツールです。\n\n・ダブルタップで画面がOFFになり端末がロックされます。\n・端末メーカー、Androidバージョン、Bluetooth機器により入力が届かない場合があります。\n・画面OFF前に、必要なデータを保存してください。\n・作者は入力遅延、画面ロック、サービス停止等について責任を負いません。\n\n©youyouboydragon")
            .setPositiveButton("同意して始める",(d,w)->getPreferences(0).edit().putBoolean("onboarded",true).apply()).setCancelable(false).show();
    }
    void checkForUpdate() {
        showUpdateLoading();
        final long started=System.currentTimeMillis();
        new Thread(()->{ try {
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/youyouboydragonOfficial/PocketShutdown/releases/latest").openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setRequestProperty("Accept","application/vnd.github+json");
            InputStream in=c.getInputStream(); java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1)b.write(buf,0,n); in.close(); JSONObject release=new JSONObject(new String(b.toByteArray(),"UTF-8")); String tag=release.optString("tag_name"); String apkUrl=null; org.json.JSONArray assets=release.optJSONArray("assets"); if(assets!=null) for(int i=0;i<assets.length();i++){ JSONObject a=assets.getJSONObject(i); if(a.optString("name").endsWith(".apk")){apkUrl=a.optString("browser_download_url");break;} } String current=getPackageManager().getPackageInfo(getPackageName(),0).versionName; boolean newer=compareVersion(tag.replace("v", ""),current)>0; final String downloadUrl=apkUrl; waitForLoading(started); runOnUiThread(()->{finishUpdateLoading(); if(newer&&downloadUrl!=null){updateStatus.setText("新しいバージョンがあります: "+tag); showUpdateResult("UPDATE READY","現在  "+current+"\n最新  "+tag+"\n\n新しいバージョンをダウンロードできます。",true,downloadUrl);} else {updateStatus.setText("最新バージョンです  •  "+current); showUpdateResult("ALL CLEAR","現在のバージョン  "+current+"\n\n最新バージョンを使用しています。",false,null);} });
        }catch(Exception e){waitForLoading(started); runOnUiThread(()->{finishUpdateLoading(); updateStatus.setText("更新確認を完了できませんでした"); showUpdateResult("CHECK FAILED","GitHub Releasesを確認できませんでした。\n\n通信状態を確認して再試行してください。",false,null);});} }).start();
    }
    GradientDrawable updateCard(){GradientDrawable g=bg(0xff15172a,24);g.setStroke(dp(1),0xff39355f);return g;}
    void showUpdateLoading(){ if(updateDialog!=null&&updateDialog.isShowing())return; LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(28),dp(24),dp(28),dp(24)); box.setBackground(updateCard()); TextView over=text("SYSTEM UPDATE",11,0xff8b7cff); over.setLetterSpacing(.18f); box.addView(over,new LinearLayout.LayoutParams(-1,dp(24))); ProgressBar spinner=new ProgressBar(this); spinner.setIndeterminate(true); box.addView(spinner,new LinearLayout.LayoutParams(dp(44),dp(52))); TextView t=text("GitHub Releasesを確認中…",15,Color.WHITE);t.setGravity(Gravity.CENTER);box.addView(t,new LinearLayout.LayoutParams(-1,dp(34))); updateDialog=new Dialog(this); updateDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); updateDialog.setContentView(box); updateDialog.setCancelable(false); updateDialog.show(); if(updateDialog.getWindow()!=null){updateDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);updateDialog.getWindow().setDimAmount(.72f);updateDialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.84f),-2);} }
    void showUpdateResult(String title,String message,boolean canDownload,String url){ final Dialog d=new Dialog(this); d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(26),dp(24),dp(26),dp(18));box.setBackground(updateCard()); TextView over=text("POCKET SHUTDOWN  /  UPDATE",11,0xff8b7cff);over.setLetterSpacing(.1f);box.addView(over,new LinearLayout.LayoutParams(-1,dp(28))); TextView h=text(title,24,Color.WHITE);h.setTypeface(null,1);box.addView(h,new LinearLayout.LayoutParams(-1,dp(42))); TextView body=text(message,14,0xffc0bfd0);body.setPadding(0,0,0,dp(16));box.addView(body,new LinearLayout.LayoutParams(-1,0,1)); LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);TextView close=text("閉じる",14,0xffaaa6c8);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->d.dismiss());actions.addView(close,new LinearLayout.LayoutParams(dp(86),dp(44)));if(canDownload){TextView dl=text("ダウンロード",14,Color.WHITE);dl.setGravity(Gravity.CENTER);dl.setBackground(bg(0xff5547b8,14));dl.setOnClickListener(v->{d.dismiss();downloadUpdate(url);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(126),dp(44));lp.leftMargin=dp(8);actions.addView(dl,lp);}box.addView(actions,new LinearLayout.LayoutParams(-1,dp(48)));d.setContentView(box);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setDimAmount(.72f);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.84f),-2);}}
    void waitForLoading(long started){ long rest=3000-(System.currentTimeMillis()-started); if(rest>0)try{Thread.sleep(rest);}catch(InterruptedException ignored){} }
    void finishUpdateLoading(){ if(updateDialog!=null&&updateDialog.isShowing())updateDialog.dismiss(); }
    int compareVersion(String a,String b){ try {String[] x=a.split("\\."),y=b.split("\\."); for(int i=0;i<Math.max(x.length,y.length);i++){int p=i<x.length?Integer.parseInt(x[i]):0,q=i<y.length?Integer.parseInt(y[i]):0;if(p!=q)return p>q?1:-1;}}catch(Exception ignored){} return 0; }
    void downloadUpdate(String url){ updateStatus.setText("APKをダウンロード中…"); new Thread(()->{try{File dir=new File(getCacheDir(),"updates");dir.mkdirs();File apk=new File(dir,"PocketShutdown-update.apk");HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(30000);InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(apk);byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();out.close();runOnUiThread(()->installApk(apk));}catch(Exception e){runOnUiThread(()->updateStatus.setText("APKのダウンロードに失敗しました"));}}).start(); }
    void installApk(File apk){ try { Uri uri=Uri.parse("content://com.example.pocketshutdown.fileprovider/update"); Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){updateStatus.setText("インストーラーを開けませんでした");} }
    @Override protected void onResume(){super.onResume(); refresh(); refreshBluetoothStatus(); refreshDiagnostics(); if(getWindow().getDecorView().getRootView()!=null) getWindow().getDecorView().postDelayed(()->continueAfterAccessibility(null),250);}
    void refreshDiagnostics(){if(keyStatus==null)return;android.content.SharedPreferences p=getSharedPreferences("diagnostics",0);long t=p.getLong("last_key_time",0);String k=p.getString("last_key_name","");if(t==0)keyStatus.setText("入力診断: まだキー入力を受信していません");else keyStatus.setText("入力診断: "+k+" を受信  •  "+((System.currentTimeMillis()-t)/1000)+"秒前");}
    void openBatterySettings(){try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    void continueAfterAccessibility(View root){ if(!ShutdownAccessibilityService.isEnabled(this)){showAccessibilityGate();return;} if(accessibilityGate!=null&&accessibilityGate.isShowing())accessibilityGate.dismiss(); if(!getPreferences(0).getBoolean("tutorialShown",false)){getPreferences(0).edit().putBoolean("tutorialShown",true).apply();showGuide(false);} }
    void showAccessibilityGate(){ if(accessibilityGate!=null&&accessibilityGate.isShowing())return; AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("ユーザー補助を有効にしてください"); b.setMessage("このアプリは、Bluetoothイヤホンの音量キーをバックグラウンドで監視するためにユーザー補助サービスを使用します。\n\n有効にするまでアプリは使用できません。"); b.setPositiveButton("設定を開く",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); accessibilityGate=b.create();accessibilityGate.setCancelable(false);accessibilityGate.setCanceledOnTouchOutside(false);accessibilityGate.show(); }
void refreshBluetoothStatus(){ if(bluetoothStatus==null)return; if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){bluetoothStatus.setText("Bluetooth: 接続確認の許可が必要");return;} BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter(); if(adapter==null||!adapter.isEnabled()){bluetoothStatus.setText("Bluetooth: OFF");return;} bluetoothStatus.setText("Bluetooth: 接続中の機器を確認中…"); final java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>(); final Runnable render=()->{if(names.size()>0){StringBuilder s=new StringBuilder("Bluetooth: ");for(String n:names){if(s.length()>12)s.append(" / ");s.append(n);}bluetoothStatus.setText(s.toString());}else bluetoothStatus.setText("Bluetooth: オーディオ未接続");}; BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){public void onServiceConnected(int profile,BluetoothProfile proxy){try{for(BluetoothDevice d:proxy.getConnectedDevices())if(d.getName()!=null)names.add(d.getName());}catch(SecurityException ignored){}render.run();adapter.closeProfileProxy(profile,proxy);}public void onServiceDisconnected(int profile){}}; try{adapter.getProfileProxy(this,listener,BluetoothProfile.A2DP);adapter.getProfileProxy(this,listener,BluetoothProfile.HEADSET);}catch(SecurityException ignored){bluetoothStatus.setText("Bluetooth: 接続確認の許可が必要");} }
}
