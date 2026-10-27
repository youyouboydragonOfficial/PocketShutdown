package com.example.pocketshutdown;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class IntroActivity extends Activity {
    private boolean opened = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView logoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

            FrameLayout root = new FrameLayout(this);
            // メイン画面と同じディープネイビー背景色でロゴエンブレムだけを美しく浮かび上がらせる
            root.setBackgroundColor(Color.rgb(9, 11, 22));

            logoView = new ImageView(this);
            logoView.setImageResource(R.drawable.intro_logo);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoView.setAlpha(0f);
            logoView.setScaleX(0.96f);
            logoView.setScaleY(0.96f);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            );
            root.addView(logoView, lp);

            // スキップ機能はユーザーの指示により完全削除（タップしてもスキップせず演出を完走）

            setContentView(root);

            // Unity風の「ゆっくり点いて、じっくり見せて、ゆっくり消える」アニメーション演出 (約3.8秒)
            startUnitySplashAnimation();
        } catch (Throwable error) {
            openMain();
        }
    }

    private void startUnitySplashAnimation() {
        // 1) ゆっくり幻想的にフェードイン (1400ms)
        logoView.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(1400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (opened) return;
                    // 2) 1400ms ほどじっくり静止してドラゴンエンブレムを見せる
                    handler.postDelayed(() -> {
                        if (opened) return;
                        // 3) ゆっくり余韻を残してフェードアウト (1000ms)
                        logoView.animate()
                                .alpha(0f)
                                .scaleX(1.04f)
                                .scaleY(1.04f)
                                .setDuration(1000)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .withEndAction(this::openMain)
                                .start();
                    }, 1400);
                })
                .start();

        // 途中で打ち切られないよう長めの安全タイマー (8秒)
        handler.postDelayed(this::openMain, 8000);
    }

    private void openMain() {
        if (opened) return;
        opened = true;
        handler.removeCallbacksAndMessages(null);
        try {
            startActivity(new Intent(this, MainActivity.class));
            // 滑らかなクロスフェード切り替え
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Throwable ignored) {}
        finish();
    }
}
