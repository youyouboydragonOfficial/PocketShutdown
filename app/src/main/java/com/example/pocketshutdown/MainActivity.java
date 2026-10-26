package com.example.pocketshutdown;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import android.animation.ValueAnimator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String PREFS = ShutdownAccessibilityService.PREFS;
    static final String DIAG_PREFS = ShutdownAccessibilityService.DIAG_PREFS;

    int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    TextView status;
    TextView earphoneStatus;
    TextView keyStatus;
    GradientDrawable statusCardBg;
    TextView updateStatus;
    Dialog updateDialog;
    Dialog downloadDialog;
    SmoothProgressBar downloadBar;
    TextView downloadPercentText;
    TextView downloadSizeText;
    final java.util.Map<String, Switch> switchMap = new java.util.HashMap<>();
    boolean isUpdatingSwitches = false;
    AlertDialog accessibilityDialog;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        requestBluetoothPermission();
    }

    void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 41);
        }
    }

    TextView text(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    GradientDrawable bg(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff090a1a);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(24));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
            int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setPadding(dp(18), dp(8) + top, dp(18), dp(18) + bottom);
            return insets;
        });

        // 1. トップヒーロー画像（星雲背景 + POCKET LOCKロゴ + 多層ネオン発光エンブレム）
        FrameLayout hero = new FrameLayout(this);
        hero.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        ImageView heroImg = new ImageView(this);
        int heroResId = getResources().getIdentifier("hero_header", "drawable", getPackageName());
        if (heroResId != 0) {
            heroImg.setImageResource(heroResId);
        }
        heroImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroImg.setAdjustViewBounds(true);
        hero.addView(heroImg, new FrameLayout.LayoutParams(-1, -2));

        // 右上の歯車付きバージョン表示
        TextView verBadge = text("v2.6.0 ⚙", 12, 0xfffcd34d);
        verBadge.setTypeface(null, 1);
        verBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams verLp = new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.TOP);
        verLp.topMargin = dp(14);
        verLp.rightMargin = dp(8);
        verBadge.setOnClickListener(v -> checkForUpdate());
        hero.addView(verBadge, verLp);

        root.addView(hero);

        // 2. メインキャッチコピー (立体ゴールドグラデーション)
        TextView head = text("ポケットの中から、画面をOFF", 23, Color.WHITE);
        head.setGravity(Gravity.CENTER);
        head.setTypeface(null, 1);
        head.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (head.getWidth() > 0) {
                Shader shader = new LinearGradient(
                        0, 0, 0, head.getTextSize() * 1.8f,
                        new int[]{0xfffff7d6, 0xffeab308, 0xffa16207},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP
                );
                head.getPaint().setShader(shader);
                head.setShadowLayer(dp(3), 0, dp(2), 0x99000000);
                head.invalidate();
            }
        });
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(-1, dp(40));
        headLp.topMargin = dp(4);
        root.addView(head, headLp);

        TextView sub = text("イヤホン操作（ダブルタップ＝音量UPや1回タップ）を\n3秒以内に連続2回行うと画面ロック", 12.5f, 0xff9ca3af);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(2), 1.15f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.bottomMargin = dp(14);
        root.addView(sub, subLp);

        // 3. ステータスカード (ゴールドガラス調 ＆ 発光枠線)
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(16), dp(12), dp(16), dp(12));

        statusCardBg = new GradientDrawable();
        statusCardBg.setColor(0x18f59e0b);
        statusCardBg.setStroke(dp(1.5f), 0xffd97706);
        statusCardBg.setCornerRadius(dp(16));
        statusCard.setBackground(statusCardBg);

        // 左側：ゴールドのイヤホンアイコン
        EarphoneIconView earphoneIcon = new EarphoneIconView(this);
        statusCard.addView(earphoneIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        // 右側：3行テキスト情報
        LinearLayout statusTextCol = new LinearLayout(this);
        statusTextCol.setOrientation(LinearLayout.VERTICAL);
        statusTextCol.setPadding(dp(12), 0, 0, 0);

        status = text("⚠ ユーザー補助サービス未接続", 13.5f, 0xfff59e0b);
        status.setTypeface(null, 1);
        statusTextCol.addView(status, new LinearLayout.LayoutParams(-1, -2));

        earphoneStatus = text("⚠ イヤホン未接続", 12, 0xfffcd34d);
        earphoneStatus.setPadding(0, dp(3), 0, 0);
        statusTextCol.addView(earphoneStatus, new LinearLayout.LayoutParams(-1, -2));

        keyStatus = text("キー入力: 未検知", 11, 0xffa1a1aa);
        keyStatus.setPadding(0, dp(2), 0, 0);
        statusTextCol.addView(keyStatus, new LinearLayout.LayoutParams(-1, -2));

        statusCard.addView(statusTextCol, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(statusCard, new LinearLayout.LayoutParams(-1, -2));

        View space1 = new View(this);
        root.addView(space1, new LinearLayout.LayoutParams(-1, dp(12)));

        // 4. 2分割アクションボタン (横並び)
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        // 左ボタン: 鮮やかなネオンパープル〜エレクトリックブルータッチ
        Button settingsBtn = new Button(this);
        settingsBtn.setText("ユーザー補助\n(アクセシビリティ) を設定");
        settingsBtn.setTextColor(Color.WHITE);
        settingsBtn.setTextSize(12.5f);
        settingsBtn.setTypeface(null, 1);
        settingsBtn.setAllCaps(false);
        settingsBtn.setLineSpacing(dp(2), 1f);

        GradientDrawable settingsBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff6366f1, 0xff8b5cf6, 0xffa855f7}
        );
        settingsBg.setCornerRadius(dp(14));
        settingsBg.setStroke(dp(1.5f), 0xffc084fc);
        settingsBtn.setBackground(settingsBg);
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(0, dp(58), 1);
        btnLp1.rightMargin = dp(6);
        actionRow.addView(settingsBtn, btnLp1);

        // 右ボタン: ディープネイビー ガラス調
        Button batteryBtn = new Button(this);
        batteryBtn.setText("バックグラウンド維持・\nバッテリー制限解除");
        batteryBtn.setTextColor(0xffe2e8f0);
        batteryBtn.setTextSize(12f);
        batteryBtn.setAllCaps(false);
        batteryBtn.setLineSpacing(dp(2), 1f);

        GradientDrawable batteryBg = new GradientDrawable();
        batteryBg.setColor(0xff13182e);
        batteryBg.setCornerRadius(dp(14));
        batteryBg.setStroke(dp(1), 0x5538bdf8);
        batteryBtn.setBackground(batteryBg);
        batteryBtn.setOnClickListener(v -> openBatterySettings());

        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(0, dp(58), 1);
        btnLp2.leftMargin = dp(6);
        actionRow.addView(batteryBtn, btnLp2);

        root.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));

        View space2 = new View(this);
        root.addView(space2, new LinearLayout.LayoutParams(-1, dp(12)));

        // ※ 手動テストボタンはユーザー指示により完全削除

        // 5. 設定スイッチ項目群 (画像通りの美しい発光トグルスイッチ)
        addSwitchRowCustom(root, "イヤホン操作で画面OFF", "autoClick", true, 0xffa855f7, 0xff4338ca);
        addSwitchRowCustom(root, "画面OFF時に振動 (バイブ)", "vibrate", true, 0xfffbbf24, 0xff92400e);

        View spaceDanger = new View(this);
        root.addView(spaceDanger, new LinearLayout.LayoutParams(-1, dp(8)));

        // 6. ⚠ 危険区域 (DANGER ZONE)
        LinearLayout dangerCard = new LinearLayout(this);
        dangerCard.setOrientation(LinearLayout.VERTICAL);
        dangerCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable dangerBg = bg(0xff1c0e12, 16);
        dangerBg.setStroke(dp(1.5f), 0xffef4444);
        dangerCard.setBackground(dangerBg);

        TextView dangerTitle = text("⚠ 危険区域 / DANGER ZONE", 12.5f, 0xffef4444);
        dangerTitle.setTypeface(null, 1);
        dangerTitle.setLetterSpacing(.08f);
        dangerCard.addView(dangerTitle, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView dangerDesc = text("使わない時は全設定をOFFにして、バックグラウンド待機を停止できます。", 11, 0xfffca5a5);
        dangerDesc.setPadding(0, 0, 0, dp(10));
        dangerCard.addView(dangerDesc, new LinearLayout.LayoutParams(-1, -2));

        Button stopAllBtn = new Button(this);
        stopAllBtn.setText("🛑 全設定をOFFにして完全停止");
        stopAllBtn.setTextColor(Color.WHITE);
        stopAllBtn.setTextSize(13);
        stopAllBtn.setTypeface(null, 1);
        stopAllBtn.setAllCaps(false);

        GradientDrawable stopBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xffdc2626, 0xff991b1b}
        );
        stopBg.setCornerRadius(dp(12));
        stopBg.setStroke(dp(1), 0xfff87171);
        stopAllBtn.setBackground(stopBg);
        stopAllBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("全機能を完全停止しますか？")
                    .setMessage("イヤホン操作による画面OFFなど、本アプリの全設定をOFFにし、バックグラウンド監視を停止（休止状態）にします。\n\n再び使いたい時は『推奨設定に一括復元』をタップしてください。")
                    .setPositiveButton("完全停止する", (dialog, which) -> {
                        ShutdownAccessibilityService.stopAll(this);
                        refreshSwitches();
                        refresh();
                        Toast.makeText(this, "全設定をOFFにし、停止しました", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        });
        dangerCard.addView(stopAllBtn, new LinearLayout.LayoutParams(-1, dp(42)));

        View spaceD1 = new View(this);
        dangerCard.addView(spaceD1, new LinearLayout.LayoutParams(-1, dp(8)));

        Button restoreBtn = new Button(this);
        restoreBtn.setText("🔄 推奨設定に一括復元 (再開)");
        restoreBtn.setTextColor(0xffe2e8f0);
        restoreBtn.setTextSize(12);
        restoreBtn.setAllCaps(false);

        GradientDrawable restoreBg = bg(0xff1e1724, 12);
        restoreBg.setStroke(dp(1), 0x5564748b);
        restoreBtn.setBackground(restoreBg);
        restoreBtn.setOnClickListener(v -> {
            ShutdownAccessibilityService.restoreDefaults(this);
            refreshSwitches();
            refresh();
            Toast.makeText(this, "推奨設定に復元しました", Toast.LENGTH_SHORT).show();
            if (!ShutdownAccessibilityService.isEnabled(this)) {
                showAccessibilityRequiredDialog();
            }
        });
        dangerCard.addView(restoreBtn, new LinearLayout.LayoutParams(-1, dp(40)));

        root.addView(dangerCard, new LinearLayout.LayoutParams(-1, -2));

        View space4 = new View(this);
        root.addView(space4, new LinearLayout.LayoutParams(-1, dp(16)));

        // 7. フッター (ピル型ボタン ＆ 著作権表示)
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);

        TextView terms = text("チュートリアル・使い方を表示 〉", 12, 0xff94a3b8);
        terms.setGravity(Gravity.CENTER);
        GradientDrawable termsBg = bg(0xff16172e, 20);
        termsBg.setStroke(dp(0.8f), 0x3364748b);
        terms.setBackground(termsBg);
        terms.setPadding(dp(22), dp(9), dp(22), dp(9));
        terms.setOnClickListener(v -> showGuide(true));
        footer.addView(terms, new LinearLayout.LayoutParams(-2, -2));

        updateStatus = text("最新バージョン v2.6.0", 11, 0xff5e5d70);
        updateStatus.setVisibility(View.GONE); // バージョンは上部に表示するため非表示
        footer.addView(updateStatus);

        TextView creator = text("youyouboydragon", 11, 0xffb45309);
        creator.setGravity(Gravity.CENTER);
        creator.setPadding(0, dp(14), 0, dp(4));
        footer.addView(creator, new LinearLayout.LayoutParams(-1, -2));

        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(root);
        setContentView(scroll);

        refresh();
        refreshEarphoneStatus();
        refreshDiagnostics();

        scroll.postDelayed(this::checkForUpdate, 800);
    }

    void addSwitchRowCustom(LinearLayout parent, String label, String prefKey, boolean defaultVal, int thumbColor, int trackColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(0xff121429);
        rowBg.setCornerRadius(dp(14));
        rowBg.setStroke(dp(0.8f), 0x336366f1);
        row.setBackground(rowBg);

        TextView t = text(label, 14, Color.WHITE);
        t.setTypeface(null, 1);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

        Switch sw = new Switch(this);
        sw.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(prefKey, defaultVal));

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] thumbColors = new int[]{thumbColor, 0xff475569};
        int[] trackColors = new int[]{trackColor, 0xff1e293b};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sw.setThumbTintList(new ColorStateList(states, thumbColors));
            sw.setTrackTintList(new ColorStateList(states, trackColors));
        }

        sw.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingSwitches) return;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(prefKey, checked).apply();
            if (checked && !ShutdownAccessibilityService.isEnabled(this)) {
                showAccessibilityRequiredDialog();
            }
            refresh();
        });
        row.addView(sw);
        switchMap.put(prefKey, sw);

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));

        View sep = new View(this);
        parent.addView(sep, new LinearLayout.LayoutParams(-1, dp(8)));
    }

    void refreshSwitches() {
        isUpdatingSwitches = true;
        try {
            android.content.SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            for (java.util.Map.Entry<String, Switch> entry : switchMap.entrySet()) {
                boolean def = !entry.getKey().equals("earphoneOnly") || sp.getBoolean("earphoneOnly", true);
                entry.getValue().setChecked(sp.getBoolean(entry.getKey(), def));
            }
        } finally {
            isUpdatingSwitches = false;
        }
    }

    static class EarphoneIconView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        EarphoneIconView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.08f);
            p.setColor(0xfffbbf24);
            p.setStrokeCap(Paint.Cap.ROUND);

            // ヘッドバンド (上部の弧)
            RectF arc = new RectF(cx - w * 0.35f, cy - h * 0.42f, cx + w * 0.35f, cy + h * 0.28f);
            canvas.drawArc(arc, 180, 180, false, p);

            // 左右のイヤホンイヤーピース
            p.setStyle(Paint.Style.FILL);
            RectF leftPiece = new RectF(cx - w * 0.42f, cy - h * 0.05f, cx - w * 0.22f, cy + h * 0.35f);
            canvas.drawRoundRect(leftPiece, w * 0.08f, w * 0.08f, p);

            RectF rightPiece = new RectF(cx + w * 0.22f, cy - h * 0.05f, cx + w * 0.42f, cy + h * 0.35f);
            canvas.drawRoundRect(rightPiece, w * 0.08f, w * 0.08f, p);
        }
    }

    static class DownloadAnimationView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float phase;
        ValueAnimator anim;

        DownloadAnimationView(Context c) {
            super(c);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1600);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(v -> {
                phase = (float) v.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dpStatic(getContext(), 4);

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xff122b1d);
            c.drawCircle(cx, cy, dpStatic(getContext(), 28), p);

            for (int i = 0; i < 2; i++) {
                float t = (phase + i * 0.5f) % 1.0f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dpStatic(getContext(), 2));
                p.setColor(Color.argb((int)(110 * (1f - t)), 34, 197, 94));
                c.drawCircle(cx, cy, dpStatic(getContext(), 26 + t * 22), p);
            }

            p.setStyle(Paint.Style.FILL);
            float[] xOffsets = {-22f, -11f, 0f, 11f, 22f};
            float[] speedOffsets = {0.1f, 0.5f, 0.75f, 0.3f, 0.9f};
            for (int i = 0; i < 5; i++) {
                float prg = (phase + speedOffsets[i]) % 1.0f;
                float px = cx + dpStatic(getContext(), xOffsets[i]);
                float py = cy - dpStatic(getContext(), 36) + prg * dpStatic(getContext(), 72);
                int alpha = (int)(255 * Math.sin(prg * Math.PI));
                p.setColor(Color.argb(Math.max(0, Math.min(255, alpha)), 134, 239, 172));
                c.drawCircle(px, py, dpStatic(getContext(), 2.2f), p);
            }

            float trayY = cy + dpStatic(getContext(), 20);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpStatic(getContext(), 3));
            p.setColor(0xff22c55e);
            float trayW = dpStatic(getContext(), 20);
            c.drawLine(cx - trayW, trayY, cx + trayW, trayY, p);
            c.drawLine(cx - trayW, trayY - dpStatic(getContext(), 5), cx - trayW, trayY, p);
            c.drawLine(cx + trayW, trayY - dpStatic(getContext(), 5), cx + trayW, trayY, p);

            float bounce = (float) Math.sin(phase * Math.PI * 2) * dpStatic(getContext(), 4);
            float arrowY = cy + bounce;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpStatic(getContext(), 4.5f));
            p.setColor(0xff4ade80);

            c.drawLine(cx, arrowY - dpStatic(getContext(), 14), cx, arrowY + dpStatic(getContext(), 7), p);
            float wing = dpStatic(getContext(), 9);
            c.drawLine(cx - wing, arrowY - dpStatic(getContext(), 2), cx, arrowY + dpStatic(getContext(), 7), p);
            c.drawLine(cx + wing, arrowY - dpStatic(getContext(), 2), cx, arrowY + dpStatic(getContext(), 7), p);
        }

        static int dpStatic(Context c, float n) {
            return (int)(n * c.getResources().getDisplayMetrics().density + .5f);
        }
    }

    static class SmoothProgressBar extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        float currentProgress = 0f;
        float targetProgress = 0f;
        ValueAnimator progAnim;
        ValueAnimator shimmerAnim;
        float shimmerPhase = 0f;

        SmoothProgressBar(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            shimmerAnim = ValueAnimator.ofFloat(0f, 1f);
            shimmerAnim.setDuration(1300);
            shimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
            shimmerAnim.addUpdateListener(v -> {
                shimmerPhase = (float) v.getAnimatedValue();
                invalidate();
            });
            shimmerAnim.start();
        }

        public void setProgressSmooth(float target) {
            if (target < 0) target = 0;
            if (target > 100) target = 100;
            targetProgress = target;

            if (progAnim != null) {
                progAnim.cancel();
            }
            progAnim = ValueAnimator.ofFloat(currentProgress, targetProgress);
            progAnim.setDuration(140);
            progAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            progAnim.addUpdateListener(v -> {
                currentProgress = (float) v.getAnimatedValue();
                invalidate();
            });
            progAnim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (progAnim != null) progAnim.cancel();
            if (shimmerAnim != null) shimmerAnim.cancel();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float radius = h / 2f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xff122319);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, radius, radius, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpStatic(getContext(), 1));
            p.setColor(0xff1c3e2b);
            c.drawRoundRect(r, radius, radius, p);

            float fillW = w * (currentProgress / 100f);
            if (fillW > 0) {
                c.save();
                r.set(0, 0, w, h);
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
                c.clipPath(clip);

                p.setStyle(Paint.Style.FILL);
                p.setColor(0xff22c55e);
                r.set(0, 0, fillW, h);
                c.drawRect(r, p);

                float shimmerW = dpStatic(getContext(), 50);
                float shimmerX = -shimmerW + shimmerPhase * (fillW + shimmerW * 2);
                LinearGradient gradient = new LinearGradient(
                        shimmerX, 0, shimmerX + shimmerW, 0,
                        new int[]{0x00ffffff, 0x66ffffff, 0x00ffffff},
                        null, Shader.TileMode.CLAMP
                );
                p.setShader(gradient);
                r.set(shimmerX, 0, shimmerX + shimmerW, h);
                c.drawRect(r, p);
                p.setShader(null);

                c.restore();
            }
        }

        static int dpStatic(Context c, float n) {
            return (int)(n * c.getResources().getDisplayMetrics().density + .5f);
        }
    }

    void refresh() {
        boolean on = ShutdownAccessibilityService.isEnabled(this);
        boolean active = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("autoClick", true);
        if (status != null && statusCardBg != null) {
            if (!on) {
                status.setText("⚠  ユーザー補助サービス未接続");
                status.setTextColor(0xfff59e0b);
                statusCardBg.setColor(0x18f59e0b);
                statusCardBg.setStroke(dp(1.5f), 0xffd97706);
            } else if (!active) {
                status.setText("🛑  全機能停止中 (休止)");
                status.setTextColor(0xffef4444);
                statusCardBg.setColor(0x18ef4444);
                statusCardBg.setStroke(dp(1.5f), 0xffdc2626);
            } else {
                status.setText("●  監視中 — 準備完了");
                status.setTextColor(0xff22c55e);
                statusCardBg.setColor(0x1822c55e);
                statusCardBg.setStroke(dp(1.5f), 0xff16a34a);
            }
        }
    }

    void showGuide(boolean manual) {
        new AlertDialog.Builder(this)
                .setTitle("使い方")
                .setMessage("【画面OFFにする方法】\n\n"
                        + "1. イヤホンのダブルタップ（音量UP/DOWN）や1回タップ（再生/停止）を、3秒以内に連続で2回行います。\n"
                        + "（トントン … トントン と3秒以内に2回操作）\n\n"
                        + "2. 3秒以内に2回目の操作を受信すると、画面がOFFになり端末がロックされます（短く振動します）。\n\n"
                        + "※ スマホ本体の物理音量ボタンを押しても画面が消えることはありません。イヤホンを操作してください。")
                .setPositiveButton("OK", null)
                .show();
    }

    void checkForUpdate() {
        showUpdateLoading();
        final long started = System.currentTimeMillis();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/youyouboydragonOfficial/PocketShutdown/releases/latest").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
                in.close();
                JSONObject release = new JSONObject(new String(b.toByteArray(), "UTF-8"));
                String tag = release.optString("tag_name");
                String apkUrl = null;
                long apkSize = 0;
                org.json.JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url");
                            apkSize = a.optLong("size", 0);
                            break;
                        }
                    }
                }
                String current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                boolean newer = compareVersion(tag.replace("v", ""), current) > 0;
                final String downloadUrl = apkUrl;
                final long downloadSize = apkSize;
                waitForLoading(started);
                runOnUiThread(() -> {
                    finishUpdateLoading();
                    if (newer && downloadUrl != null) {
                        if (updateStatus != null) updateStatus.setText("新しいバージョンがあります: " + tag);
                        String sizeText = downloadSize > 0 ? "\n更新サイズ  約 " + formatFileSize(downloadSize) : "";
                        showUpdateResult("UPDATE READY", "現在  " + current + "\n最新  " + tag + sizeText + "\n\n新しいバージョンをダウンロードできます。", true, downloadUrl, downloadSize);
                    } else {
                        if (updateStatus != null) updateStatus.setText("最新バージョンです  •  " + current);
                        showUpdateResult("ALL CLEAR", "現在のバージョン  " + current + "\n\n最新バージョンを使用しています。", false, null, 0);
                    }
                });
            } catch (Exception e) {
                waitForLoading(started);
                runOnUiThread(() -> {
                    finishUpdateLoading();
                    if (updateStatus != null) updateStatus.setText("更新確認を完了できませんでした");
                    showUpdateResult("CHECK FAILED", "GitHub Releasesを確認できませんでした。\n\n通信状態を確認して再試行してください。", false, null, 0);
                });
            }
        }).start();
    }

    GradientDrawable updateCard() {
        GradientDrawable g = bg(0xff15172a, 24);
        g.setStroke(dp(1), 0xff39355f);
        return g;
    }

    void showUpdateLoading() {
        if (updateDialog != null && updateDialog.isShowing()) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(24), dp(28), dp(24));
        box.setBackground(updateCard());
        TextView over = text("SYSTEM UPDATE", 11, 0xff8b7cff);
        over.setLetterSpacing(.18f);
        box.addView(over, new LinearLayout.LayoutParams(-1, dp(24)));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        box.addView(spinner, new LinearLayout.LayoutParams(dp(44), dp(52)));
        TextView t = text("GitHub Releasesを確認中…", 15, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(-1, dp(34)));
        updateDialog = new Dialog(this);
        updateDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        updateDialog.setContentView(box);
        updateDialog.setCancelable(false);
        updateDialog.show();
        if (updateDialog.getWindow() != null) {
            updateDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            updateDialog.getWindow().setDimAmount(.72f);
            updateDialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * .84f), -2);
        }
    }

    void showUpdateResult(String title, String message, boolean canDownload, String url, long downloadSize) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(24), dp(26), dp(18));
        box.setBackground(updateCard());
        TextView over = text("POCKET LOCK  /  UPDATE", 11, 0xff8b7cff);
        over.setLetterSpacing(.1f);
        box.addView(over, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView h = text(title, 24, Color.WHITE);
        h.setTypeface(null, 1);
        box.addView(h, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView body = text(message, 14, 0xffc0bfd0);
        body.setPadding(0, 0, 0, dp(16));
        box.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView close = text("閉じる", 14, 0xffaaa6c8);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> d.dismiss());
        actions.addView(close, new LinearLayout.LayoutParams(dp(86), dp(44)));
        if (canDownload) {
            TextView dl = text("ダウンロード", 14, Color.WHITE);
            dl.setGravity(Gravity.CENTER);
            dl.setBackground(bg(0xff5547b8, 14));
            dl.setOnClickListener(v -> {
                d.dismiss();
                downloadUpdate(url, downloadSize);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(126), dp(44));
            lp.leftMargin = dp(8);
            actions.addView(dl, lp);
        }
        box.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));
        d.setContentView(box);
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setDimAmount(.72f);
            d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * .84f), -2);
        }
    }

    void waitForLoading(long started) {
        long rest = 2000 - (System.currentTimeMillis() - started);
        if (rest > 0) try { Thread.sleep(rest); } catch (InterruptedException ignored) {}
    }

    void finishUpdateLoading() {
        if (updateDialog != null && updateDialog.isShowing()) updateDialog.dismiss();
    }

    int compareVersion(String a, String b) {
        try {
            String[] x = a.split("\\."), y = b.split("\\.");
            for (int i = 0; i < Math.max(x.length, y.length); i++) {
                int p = i < x.length ? Integer.parseInt(x[i]): 0;
                int q = i < y.length ? Integer.parseInt(y[i]): 0;
                if (p != q) return p > q ? 1 : -1;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    String formatFileSize(long bytes) {
        if (bytes < 1024L * 1024L) return (bytes / 1024L) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) return "";
        if (bytesPerSec < 1024L * 1024L) return (bytesPerSec / 1024L) + " KB/s";
        return String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }

    void updateDownloadProgress(float percent, long currentBytes, long totalBytes, long speed) {
        if (downloadBar != null) {
            if (percent >= 0) {
                downloadBar.setProgressSmooth(percent);
            }
        }
        if (downloadPercentText != null) {
            if (percent >= 0) {
                downloadPercentText.setText(String.format(java.util.Locale.US, "%.1f%%", percent));
            } else {
                downloadPercentText.setText("--- %");
            }
        }
        if (downloadSizeText != null) {
            String spdStr = formatSpeed(speed);
            String prefix = spdStr.isEmpty() ? "" : spdStr + "  •  ";
            if (totalBytes > 0) {
                downloadSizeText.setText(prefix + formatFileSize(currentBytes) + " / " + formatFileSize(totalBytes));
            } else {
                downloadSizeText.setText(prefix + formatFileSize(currentBytes));
            }
        }
    }

    void downloadUpdate(String url, long expectedSize) {
        showDownloadLoading();
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "updates");
                dir.mkdirs();
                File apk = new File(dir, "PocketShutdown-update.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "PocketLockApp");

                long total = c.getContentLengthLong();
                if (total <= 0) total = expectedSize;
                final long totalBytes = total;

                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[16384];
                int n;
                long downloaded = 0;
                long lastUpdate = 0;
                long speedLastBytes = 0;
                long speedLastTime = System.currentTimeMillis();
                long currentSpeed = 0;

                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    long now = System.currentTimeMillis();

                    if (now - speedLastTime >= 250) {
                        currentSpeed = (downloaded - speedLastBytes) * 1000 / Math.max(1, (now - speedLastTime));
                        speedLastBytes = downloaded;
                        speedLastTime = now;
                    }

                    if (now - lastUpdate > 16 || (totalBytes > 0 && downloaded >= totalBytes)) {
                        lastUpdate = now;
                        final float percent = totalBytes > 0 ? ((float) downloaded * 100f / (float) totalBytes) : -1f;
                        final long cur = downloaded;
                        final long spd = currentSpeed;
                        runOnUiThread(() -> updateDownloadProgress(percent, cur, totalBytes, spd));
                    }
                }
                in.close();
                out.close();

                final long finalTotal = totalBytes > 0 ? totalBytes : downloaded;
                final long finalSpd = currentSpeed;
                runOnUiThread(() -> updateDownloadProgress(100f, finalTotal, finalTotal, finalSpd));
                Thread.sleep(300);

                runOnUiThread(() -> {
                    finishDownloadLoading();
                    installApk(apk);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    finishDownloadLoading();
                    if (updateStatus != null) updateStatus.setText("APKのダウンロードに失敗しました");
                    showUpdateResult("DOWNLOAD FAILED", "APKをダウンロードできませんでした。\n\n通信状態を確認して、もう一度お試しください。", false, null, 0);
                });
            }
        }).start();
    }

    void showDownloadLoading() {
        if (downloadDialog != null && downloadDialog.isShowing()) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(22), dp(26), dp(22));
        box.setBackground(updateCard());

        TextView over = text("POCKET LOCK  /  INSTALL", 11, 0xff8b7cff);
        over.setLetterSpacing(.12f);
        box.addView(over, new LinearLayout.LayoutParams(-1, dp(22)));

        DownloadAnimationView animView = new DownloadAnimationView(this);
        box.addView(animView, new LinearLayout.LayoutParams(-1, dp(92)));

        TextView title = text("最新アップデートをダウンロード中…", 16, Color.WHITE);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));

        downloadPercentText = text("0.0%", 28, 0xff22c55e);
        downloadPercentText.setTypeface(null, 1);
        downloadPercentText.setGravity(Gravity.CENTER);
        box.addView(downloadPercentText, new LinearLayout.LayoutParams(-1, dp(40)));

        downloadBar = new SmoothProgressBar(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(16));
        barLp.topMargin = dp(4);
        barLp.bottomMargin = dp(4);
        box.addView(downloadBar, barLp);

        downloadSizeText = text("0 KB / ---", 12, 0xffa9a8bc);
        downloadSizeText.setGravity(Gravity.CENTER);
        box.addView(downloadSizeText, new LinearLayout.LayoutParams(-1, dp(24)));

        TextView sub = text("この画面を閉じずにお待ちください", 11, 0xff77768a);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, 0);
        box.addView(sub, new LinearLayout.LayoutParams(-1, dp(26)));

        downloadDialog = new Dialog(this);
        downloadDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        downloadDialog.setContentView(box);
        downloadDialog.setCancelable(false);
        downloadDialog.show();
        if (downloadDialog.getWindow() != null) {
            downloadDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            downloadDialog.getWindow().setDimAmount(.78f);
            downloadDialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * .88f), -2);
        }
    }

    void finishDownloadLoading() {
        if (downloadDialog != null && downloadDialog.isShowing()) downloadDialog.dismiss();
    }

    void installApk(File apk) {
        try {
            Uri uri = Uri.parse("content://com.example.pocketshutdown.fileprovider/update");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            if (updateStatus != null) updateStatus.setText("インストーラーを開けませんでした");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        refreshEarphoneStatus();
        refreshDiagnostics();
    }

    void refreshDiagnostics() {
        if (keyStatus == null) return;
        android.content.SharedPreferences p = getSharedPreferences(DIAG_PREFS, MODE_PRIVATE);
        String k = p.getString("last_key_name", "");
        String s = p.getString("last_key_source", "");
        if (k == null || k.isEmpty()) {
            keyStatus.setText("キー入力: 未検知");
        } else {
            keyStatus.setText("最新キー: " + k + (s != null && !s.isEmpty() ? " [" + s + "]" : ""));
        }
    }

    void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }

    void showAccessibilityRequiredDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
            return;
        }
        accessibilityDialog = new AlertDialog.Builder(this)
                .setTitle("ユーザー補助の許可が必要です")
                .setMessage("画面をOFFにして端末をロックするには、Pocket Lockのユーザー補助サービスを有効にする必要があります。\n\n『設定を開く』をタップして、インストール済みのアプリから『Pocket Lock』をONにしてください。\n\n※設定がグレーアウトしている場合は、アプリ情報画面の右上『︙』メニューから『制限付き設定を許可』を行ってください。")
                .setPositiveButton("設定を開く", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("閉じる", null)
                .show();
    }

    void refreshEarphoneStatus() {
        if (earphoneStatus == null) return;
        boolean connected = ShutdownAccessibilityService.checkEarphoneConnected(this);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            final Runnable render = () -> {
                if (names.size() > 0) {
                    StringBuilder sb = new StringBuilder("🎧 BTイヤホン接続中: ");
                    for (String n : names) {
                        if (sb.length() > 16) sb.append(", ");
                        sb.append(n);
                    }
                    earphoneStatus.setText(sb.toString());
                    earphoneStatus.setTextColor(0xff38bdf8);
                } else if (connected) {
                    earphoneStatus.setText("🎧 有線イヤホン接続中");
                    earphoneStatus.setTextColor(0xff38bdf8);
                } else {
                    earphoneStatus.setText("⚠ イヤホン未接続");
                    earphoneStatus.setTextColor(0xfffcd34d);
                }
            };

            BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        for (BluetoothDevice d : proxy.getConnectedDevices()) {
                            if (d.getName() != null) names.add(d.getName());
                        }
                    } catch (SecurityException ignored) {}
                    render.run();
                    adapter.closeProfileProxy(profile, proxy);
                }
                public void onServiceDisconnected(int profile) {}
            };

            try {
                adapter.getProfileProxy(this, listener, BluetoothProfile.A2DP);
                adapter.getProfileProxy(this, listener, BluetoothProfile.HEADSET);
            } catch (SecurityException ignored) {
                render.run();
            }
        } else {
            if (connected) {
                earphoneStatus.setText("🎧 有線イヤホン接続中");
                earphoneStatus.setTextColor(0xff38bdf8);
            } else {
                earphoneStatus.setText("⚠ イヤホン未接続 (Bluetooth OFF)");
                earphoneStatus.setTextColor(0xfffcd34d);
            }
        }
    }
}
