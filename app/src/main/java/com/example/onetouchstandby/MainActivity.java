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
    private static final long RESTANDBY_DELAY_MS = 3000; // 唤醒后无操作重新待机的时间
    private static final String STANDBY_PASSWORD = "2024"; // 密码待机模式的退出密码
    private static final int PWD_LENGTH = 4; // 密码位数

    private LinearLayout homeLayout;
    private Button btnStandby;
    private Button btnPasswordStandby;
    private Button btnExitApp;
    private FrameLayout standbyLayout;
    private TextView tvTime;
    private Button btnExitStandby;
    private Handler handler;
    private Runnable timeRunnable;
    private Runnable hideTimeRunnable;
    private Runnable restandbyRunnable;
    private SimpleDateFormat timeFormat;
    private boolean isDeepStandby = false;
    private boolean passwordRequired = false; // 当前待机是否要求密码才能退出
    private float originalBrightness;

    // 密码解锁界面（iPhone 风格）
    private FrameLayout passwordOverlay;
    private final StringBuilder pwdInput = new StringBuilder();
    private TextView[] dotViews = new TextView[PWD_LENGTH];
    private TextView tvPwdError;

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

    /** 屏蔽返回键：小白点/悬浮球模拟的返回会直接退出 app 绕过密码，一律无效 */
    @Override
    public void onBackPressed() {
        // 什么都不做：返回键在待机与密码解锁期间一律拦截
    }

    private void initViews() {
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        homeLayout = new LinearLayout(this);
        homeLayout.setOrientation(LinearLayout.VERTICAL);
        homeLayout.setGravity(Gravity.CENTER);
        homeLayout.setBackgroundColor(Color.parseColor("#121212"));

        // 普通待机：居中主按钮
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
        btnStandbyParams.bottomMargin = 40;
        homeLayout.addView(btnStandby, btnStandbyParams);

        // 密码待机：屏幕中间底部区域
        btnPasswordStandby = new Button(this);
        btnPasswordStandby.setText("密码待机模式");
        btnPasswordStandby.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        btnPasswordStandby.setTextColor(Color.WHITE);
        btnPasswordStandby.setBackgroundColor(Color.parseColor("#455A64"));
        btnPasswordStandby.setPadding(60, 30, 60, 30);
        LinearLayout.LayoutParams pwStandbyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pwStandbyParams.bottomMargin = 40;
        homeLayout.addView(btnPasswordStandby, pwStandbyParams);

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

        // 唤醒后显示的「退出待机」按钮：位于时间正下方，间距适中
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
        exitStandbyParams.topMargin = 100; // 中心下方 100px（topMargin 为正值时向下偏移）
        standbyLayout.addView(btnExitStandby, exitStandbyParams);

        // 密码解锁浮层（iPhone 风格），盖在待机界面之上
        passwordOverlay = buildPasswordOverlay();
        passwordOverlay.setVisibility(View.GONE);

        rootLayout.addView(homeLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(standbyLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(passwordOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);
    }

    /** 构建 iPhone 风格密码解锁界面：4 个圆点 + 数字键盘 */
    private FrameLayout buildPasswordOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#EE000000"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        // 标题
        TextView title = new TextView(this);
        title.setText("输入密码");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = 30;
        box.addView(title, titleParams);

        // 4 个密码圆点
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < PWD_LENGTH; i++) {
            TextView dot = new TextView(this);
            dot.setText("○");
            dot.setTextColor(Color.WHITE);
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dotParams.leftMargin = 20;
            dotParams.rightMargin = 20;
            dotsRow.addView(dot, dotParams);
            dotViews[i] = dot;
        }
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsParams.bottomMargin = 16;
        box.addView(dotsRow, dotsParams);

        // 错误提示（密码错误时显示「景和年」）
        tvPwdError = new TextView(this);
        tvPwdError.setText("");
        tvPwdError.setTextColor(Color.parseColor("#FF5252"));
        tvPwdError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvPwdError.setGravity(Gravity.CENTER);
        tvPwdError.setMinHeight((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics()));
        LinearLayout.LayoutParams errParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        errParams.bottomMargin = 20;
        box.addView(tvPwdError, errParams);

        // 数字键盘：3 行数字 + 最后一行（取消 / 0 / 删除）
        String[][] keys = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"取消", "0", "删除"}
        };
        for (String[] row : keys) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            for (String key : row) {
                Button btn;
                if ("取消".equals(key)) {
                    btn = buildKeyButton(key, Color.TRANSPARENT, Color.GRAY, 18);
                    btn.setOnClickListener(v -> hidePasswordOverlay());
                } else if ("删除".equals(key)) {
                    btn = buildKeyButton("⌫", Color.parseColor("#37474F"), Color.WHITE, 26);
                    btn.setOnClickListener(v -> onPwdDelete());
                } else {
                    btn = buildKeyButton(key, Color.parseColor("#37474F"), Color.WHITE, 30);
                    final String digit = key;
                    btn.setOnClickListener(v -> onPwdDigit(digit));
                }
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                        (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110,
                                getResources().getDisplayMetrics()),
                        (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72,
                                getResources().getDisplayMetrics()));
                keyParams.leftMargin = 12;
                keyParams.rightMargin = 12;
                keyParams.topMargin = 6;
                keyParams.bottomMargin = 6;
                rowLayout.addView(btn, keyParams);
            }
            box.addView(rowLayout);
        }

        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        boxParams.gravity = Gravity.CENTER;
        overlay.addView(box, boxParams);
        return overlay;
    }

    /** 生成键盘按钮 */
    private Button buildKeyButton(String label, int bgColor, int textColor, float spSize) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        btn.setTextColor(textColor);
        btn.setBackgroundColor(bgColor);
        btn.setTypeface(null, Typeface.BOLD);
        return btn;
    }

    private void setupListeners() {
        btnStandby.setOnClickListener(v -> enterStandbyMode(false));
        btnPasswordStandby.setOnClickListener(v -> enterStandbyMode(true));
        btnExitApp.setOnClickListener(v -> exitApp());
        standbyLayout.setOnClickListener(v -> {
            if (isDeepStandby) {
                wakeUpStandby();
            } else {
                // 唤醒态下点击屏幕 = 用户在看时间，重置「无操作重新待机」计时
                startRestandbyTimer();
            }
        });
        btnExitStandby.setOnClickListener(v -> {
            if (passwordRequired) {
                showPasswordOverlay();
            } else {
                exitStandbyMode();
            }
        });
    }

    /** 进入待机：普通模式直接退出，密码模式需输入正确密码 */
    private void enterStandbyMode(boolean requirePassword) {
        passwordRequired = requirePassword;
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
        // 3 秒后进入深度待机（隐藏时间、压暗亮度）
        hideTimeRunnable = () -> enterDeepStandby();
        handler.postDelayed(hideTimeRunnable, 3000);
    }

    /** 深度待机：隐藏时间与按钮，窗口亮度压到最低 */
    private void enterDeepStandby() {
        isDeepStandby = true;
        tvTime.setVisibility(View.GONE);
        btnExitStandby.setVisibility(View.GONE);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.0f;
        getWindow().setAttributes(lp);
    }

    /** 唤醒：恢复时间显示，并启动「3 秒无操作重新待机」计时 */
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
        startRestandbyTimer();
    }

    /** 唤醒态下重置「无操作重新待机」计时 */
    private void startRestandbyTimer() {
        if (restandbyRunnable != null) {
            handler.removeCallbacks(restandbyRunnable);
        }
        restandbyRunnable = () -> {
            // 用户已看到时间，若 3 秒无任何操作则回到深度待机
            if (!isDeepStandby) {
                enterDeepStandby();
            }
        };
        handler.postDelayed(restandbyRunnable, RESTANDBY_DELAY_MS);
    }

    // ==================== 密码解锁（iPhone 风格） ====================

    /** 显示密码解锁浮层：清空输入、暂停自动回待机计时 */
    private void showPasswordOverlay() {
        cancelRestandbyTimer();
        pwdInput.setLength(0);
        tvPwdError.setText("");
        updatePwdDots();
        passwordOverlay.setVisibility(View.VISIBLE);
    }

    /** 隐藏密码解锁浮层：回到唤醒态并恢复计时 */
    private void hidePasswordOverlay() {
        passwordOverlay.setVisibility(View.GONE);
        if (!isDeepStandby) {
            startRestandbyTimer();
        }
    }

    /** 输入一位数字；输满 4 位后自动校验 */
    private void onPwdDigit(String digit) {
        if (pwdInput.length() >= PWD_LENGTH) {
            return; // 最多 4 位
        }
        tvPwdError.setText("");
        pwdInput.append(digit);
        updatePwdDots();
        if (pwdInput.length() == PWD_LENGTH) {
            // 稍作停顿让最后一个圆点可见，再校验
            handler.postDelayed(this::validatePwdInput, 150);
        }
    }

    /** 删除最后一位 */
    private void onPwdDelete() {
        if (pwdInput.length() > 0) {
            pwdInput.deleteCharAt(pwdInput.length() - 1);
            tvPwdError.setText("");
            updatePwdDots();
        }
    }

    /** 校验密码：正确则退出待机；错误显示「景和年」并清空，可多次重试 */
    private void validatePwdInput() {
        if (STANDBY_PASSWORD.contentEquals(pwdInput)) {
            passwordOverlay.setVisibility(View.GONE);
            exitStandbyMode();
        } else {
            tvPwdError.setText("景和年");
            pwdInput.setLength(0);
            updatePwdDots();
        }
    }

    /** 刷新密码圆点显示 */
    private void updatePwdDots() {
        for (int i = 0; i < PWD_LENGTH; i++) {
            dotViews[i].setText(i < pwdInput.length() ? "●" : "○");
        }
    }

    // ==================== 待机与退出 ====================

    private void cancelRestandbyTimer() {
        if (restandbyRunnable != null) {
            handler.removeCallbacks(restandbyRunnable);
        }
    }

    private void exitStandbyMode() {
        if (timeRunnable != null) handler.removeCallbacks(timeRunnable);
        if (hideTimeRunnable != null) handler.removeCallbacks(hideTimeRunnable);
        if (restandbyRunnable != null) handler.removeCallbacks(restandbyRunnable);
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
