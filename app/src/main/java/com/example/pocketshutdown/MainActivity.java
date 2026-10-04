package com.example.pocketshutdown;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.*;

public class MainActivity extends Activity {
    static final String PREFS="settings";
    int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    TextView status;
    @Override public void onCreate(Bundle b) { super.onCreate(b); build(); }
    TextView text(String s, float size, int color) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); return v; }
    GradientDrawable bg(int color, float r) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    void build() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(24),dp(24),dp(18)); root.setBackgroundColor(Color.rgb(9,11,22));
        TextView title=text("POCKET SHUTDOWN",12,0xffaaa6c8); title.setLetterSpacing(.22f); root.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
        FrameLayout hero=new FrameLayout(this); hero.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
        TextView orb=text("⌁",92,0xffe9e7ff); orb.setGravity(Gravity.CENTER); orb.setBackground(bg(0xff17152e,100)); FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(dp(170),dp(170),Gravity.CENTER); hero.addView(orb,op);
        TextView waves=text("◌",180,0x338b7cff); waves.setGravity(Gravity.CENTER); hero.addView(waves,new FrameLayout.LayoutParams(dp(290),dp(290),Gravity.CENTER)); waves.bringToFront(); orb.bringToFront(); animate(waves);
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
        setContentView(root); refresh();
        if(!getPreferences(0).getBoolean("onboarded",false)) root.postDelayed(()->showGuide(false),350);
    }
    void animate(View v) { AnimationSet a=new AnimationSet(true); a.addAnimation(new ScaleAnimation(.84f,1.08f,.84f,1.08f,1, .5f,1,.5f)); a.addAnimation(new AlphaAnimation(.2f,.65f)); a.setDuration(1800); a.setRepeatMode(android.view.animation.Animation.REVERSE); a.setRepeatCount(-1); v.startAnimation(a); }
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
    @Override protected void onResume(){super.onResume(); refresh();}
}
