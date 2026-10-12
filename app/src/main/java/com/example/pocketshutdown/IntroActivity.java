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
            // 画像の地の色（(255, 255, 251)）と画面全体を完全に一致させてシームレスに表示
            root.setBackgroundColor(Color.rgb(255, 255, 251));

            logoView = new ImageView(this);
            logoView.setImageResource(R.drawable.intro_logo);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoView.setAlpha(0f);
            logoView.setScaleX(0.95f);
            logoView.setScaleY(0.95f);

            int size = (int)(Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) * 0.74f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            root.addView(logoView, lp);

            // 画面タップで即スキップ
            root.setOnClickListener(v -> openMain());

            setContentView(root);

            // Unity風の「ゆっくり点いて、ゆっくり消える」アニメーション演出 (約2.4秒)
            startUnitySplashAnimation();
        } catch (Throwable error) {
            openMain();
        }
    }

    private void startUnitySplashAnimation() {
        // 1) ゆっくりフェードイン (1000ms)
        logoView.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(1000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (opened) return;
                    // 2) 700ms ほど静止してロゴを見せる
                    handler.postDelayed(() -> {
                        if (opened) return;
                        // 3) ゆっくりフェードアウト (700ms)
                        logoView.animate()
                                .alpha(0f)
                                .scaleX(1.04f)
                                .scaleY(1.04f)
                                .setDuration(700)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .withEndAction(this::openMain)
                                .start();
                    }, 700);
                })
                .start();

        // 5秒以内に確実にメイン画面へ開くためのセーフティタイマー (3秒)
        handler.postDelayed(this::openMain, 3000);
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
