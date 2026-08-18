package com.example.onetouchstandby;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    // 深度待机时写入的系统全局亮度 (0-255, 0 为最暗)
    private static final int MIN_SYSTEM_BRIGHTNESS = 0;

    private LinearLayout homeLayout;
    private Button btnStandby;
    private Button btnExitApp;
    private FrameLayout standbyLayout;
    private TextView tvTime;
    private Button btnExitStandby;
    private Handler handler;
    private Runnable timeRunnable;
    private Runnable hideTimeRunnable;
    private SimpleDateFormat timeFormat;
    private boolean isDeepStandby = false;
    private float originalBrightness;
    private int originalSystemBrightness = -1;
    private int originalBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        originalBrightness = getWindow().getAttributes().screenBrightness;
        originalSystemBrightness = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, -1);
        originalBrightnessMode = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        handler = new Handler(Looper.getMainLooper());
        timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        initViews();
        setupListeners();
        checkWriteSettingsPermission();
    }

    /** 首次启动引导授权「修改系统设置」，深度待机才能写入系统亮度 */
    private void checkWriteSettingsPermission() {
        if (Settings.System.canWrite(this)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("需要「修改系统设置」权限")
                .setMessage("为了让深度待机真正熄灭屏幕背光，需要授权本应用修改系统亮度。\n\n点击「去授权」后，请在系统设置中允许本应用「修改系统设置」。")
                .setPositiveButton("去授权", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "无法打开设置页，请到系统设置中手动授权", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("稍后再说", null)
                .show();
    }

    private void initViews() {
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        homeLayout = new LinearLayout(this);
        homeLayout.setOrientation(LinearLayout.VERTICAL);
        homeLayout.setGravity(Gravity.CENTER);
        homeLayout.setBackgroundColor(Color.parseColor("#121212"));

        btnStandby = new Button(this);
        btnStandby.setText("待机模式");
        btnStandby.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        btnStandby.setTextColor(Color.WHITE);
        btnStandby.setBackgroundColor(Color.parseColor("#1976D2"));
        btnStandby.setPadding(80, 40, 80, 40);
        btnStandby.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams btnStandbyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnStandbyParams.bottomMargin = 60;
        homeLayout.addView(btnStandby, btnStandbyParams);

        btnExitApp = new Button(this);
        btnExitApp.setText("退出app");
        btnExitApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnExitApp.setTextColor(Color.GRAY);
        btnExitApp.setBackgroundColor(Color.TRANSPARENT);
        homeLayout.addView(btnExitApp);

        standbyLayout = new FrameLayout(this);
        standbyLayout.setBackgroundColor(Color.BLACK);
        standbyLayout.setVisibility(View.GONE);

        tvTime = new TextView(this);
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 100);
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTypeface(null, Typeface.BOLD);
        tvTime.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams timeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        timeParams.gravity = Gravity.CENTER;
        timeParams.bottomMargin = 200;
        standbyLayout.addView(tvTime, timeParams);

        btnExitStandby = new Button(this);
        btnExitStandby.setText("退出待机");
        btnExitStandby.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        btnExitStandby.setTextColor(Color.WHITE);
        btnExitStandby.setBackgroundColor(Color.parseColor("#424242"));
        btnExitStandby.setPadding(60, 20, 60, 20);
        btnExitStandby.setVisibility(View.GONE);
        FrameLayout.LayoutParams exitStandbyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        exitStandbyParams.gravity = Gravity.CENTER;
        exitStandbyParams.topMargin = 150;
        standbyLayout.addView(btnExitStandby, exitStandbyParams);

        rootLayout.addView(homeLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(standbyLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);
    }

    private void setupListeners() {
        btnStandby.setOnClickListener(v -> enterStandbyMode());
        btnExitApp.setOnClickListener(v -> exitApp());
        standbyLayout.setOnClickListener(v -> {
            if (isDeepStandby) {
                wakeUpStandby();
            }
        });
        btnExitStandby.setOnClickListener(v -> exitStandbyMode());
    }

    private void enterStandbyMode() {
        homeLayout.setVisibility(View.GONE);
        standbyLayout.setVisibility(View.VISIBLE);
        tvTime.setVisibility(View.VISIBLE);
        btnExitStandby.setVisibility(View.GONE);
        isDeepStandby = false;
        updateTime();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timeRunnable);
        hideTimeRunnable = () -> {
            isDeepStandby = true;
            tvTime.setVisibility(View.GONE);
            // 1. 窗口亮度归零 (部分 ROM 有效)
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 0.0f;
            getWindow().setAttributes(lp);
            // 2. 写入系统全局亮度 (车机 ROM 更可靠，需 WRITE_SETTINGS 权限)
            setSystemBrightness(MIN_SYSTEM_BRIGHTNESS);
        };
        handler.postDelayed(hideTimeRunnable, 3000);
    }

    private void wakeUpStandby() {
        isDeepStandby = false;
        restoreSystemBrightness();
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = originalBrightness == -1.0f ? 0.5f : originalBrightness;
        getWindow().setAttributes(lp);
        tvTime.setVisibility(View.VISIBLE);
        btnExitStandby.setVisibility(View.VISIBLE);
        if (timeRunnable != null) {
            handler.removeCallbacks(timeRunnable);
            handler.post(timeRunnable);
        }
    }

    /** 写入系统全局亮度 (0-255)，失败时静默降级为仅窗口亮度 */
    private void setSystemBrightness(int value) {
        if (!Settings.System.canWrite(this)) {
            return;
        }
        try {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, value);
        } catch (Exception e) {
            // 部分 ROM 写入可能被拦截，忽略即可
        }
    }

    /** 恢复系统全局亮度与亮度模式 */
    private void restoreSystemBrightness() {
        if (!Settings.System.canWrite(this)) {
            return;
        }
        try {
            if (originalSystemBrightness >= 0) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, originalSystemBrightness);
            }
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, originalBrightnessMode);
        } catch (Exception e) {
            // ignore
        }
    }

    private void exitStandbyMode() {
        if (timeRunnable != null) handler.removeCallbacks(timeRunnable);
        if (hideTimeRunnable != null) handler.removeCallbacks(hideTimeRunnable);
        restoreSystemBrightness();
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = -1.0f;
        getWindow().setAttributes(lp);
        exitApp();
    }

    private void exitApp() {
        finish();
        System.exit(0);
    }

    private void updateTime() {
        String currentTime = timeFormat.format(new Date());
        tvTime.setText(currentTime);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
