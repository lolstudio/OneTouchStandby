package com.example.onetouchstandby;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyFullscreenUi();
        originalBrightness = getWindow().getAttributes().screenBrightness;
        handler = new Handler(Looper.getMainLooper());
        timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        initViews();
        setupListeners();
    }

    /** 全屏沉浸式；部分车机 ROM 在窗口获得焦点后才生效，因此焦点变化时再次应用 */
    private void applyFullscreenUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreenUi();
        }
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

        // 时间：屏幕正中央（不偏移），文字超大
        tvTime = new TextView(this);
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120);
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTypeface(null, Typeface.BOLD);
        tvTime.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams timeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        timeParams.gravity = Gravity.CENTER;
        standbyLayout.addView(tvTime, timeParams);

        // 唤醒后显示的「退出待机」按钮：屏幕中央偏上
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
        exitStandbyParams.topMargin = 250;
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
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 0.0f;
            getWindow().setAttributes(lp);
        };
        handler.postDelayed(hideTimeRunnable, 3000);
    }

    private void wakeUpStandby() {
        isDeepStandby = false;
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

    private void exitStandbyMode() {
        if (timeRunnable != null) handler.removeCallbacks(timeRunnable);
        if (hideTimeRunnable != null) handler.removeCallbacks(hideTimeRunnable);
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
