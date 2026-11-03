package com.xiaojing.jingge;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 悬浮球：点一下进听音识曲，拖到屏幕下方正中的圆圈里松手就关掉。
 * 横屏也按当前屏幕尺寸重新算，不会跑到屏幕外。
 */
public class BallService extends Service {
    static volatile boolean running;

    private WindowManager wm;
    private View ball;
    private View target;
    private WindowManager.LayoutParams ballLp;
    private WindowManager.LayoutParams targetLp;

    private int size, targetSize, gap;
    private int screenW, screenH;
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean hidden;          // 收进侧边了
    private boolean justRevealed;    // 这一下触摸只是把它拉出来，不算点击
    private Runnable hider;

    private float downRawX, downRawY;
    private int startX, startY;
    private boolean dragging, over;

    static boolean wanted(Context c) {
        return Ai.prefs(c).getBoolean("ball_on", false);
    }

    static void setWanted(Context c, boolean on) {
        Ai.prefs(c).edit().putBoolean("ball_on", on).apply();
    }

    static void start(Context c) {
        try {
            c.startService(new Intent(c, BallService.class));
        } catch (Throwable ignored) {
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, BallService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            size = Ui.dp(this, 42);
            targetSize = Ui.dp(this, 74);
            gap = Ui.dp(this, 26);
            metrics();
            addBall();
            running = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (target != null) wm.removeView(target);
        } catch (Throwable ignored) {
        }
        try {
            if (ball != null) wm.removeView(ball);
        } catch (Throwable ignored) {
        }
        target = null;
        ball = null;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        metrics();
        if (ballLp != null) {
            ballLp.x = clampX(ballLp.x);
            ballLp.y = clampY(ballLp.y);
            try {
                wm.updateViewLayout(ball, ballLp);
            } catch (Throwable ignored) {
            }
        }
        if (ballLp != null && ball != null && hidden) {
            ballLp.x = onLeftSide() ? -(int) (size * 0.62f) : screenW - (int) (size * 0.38f);
            try {
                wm.updateViewLayout(ball, ballLp);
            } catch (Throwable ignored) {
            }
        }
        if (target != null && targetLp != null) {
            targetLp.x = (screenW - targetSize) / 2;
            targetLp.y = screenH - targetSize - gap;
            try {
                wm.updateViewLayout(target, targetLp);
            } catch (Throwable ignored) {
            }
        }
    }

    private void metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        try {
            wm.getDefaultDisplay().getRealMetrics(dm);
        } catch (Throwable t) {
            dm = getResources().getDisplayMetrics();
        }
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        if (screenW <= 0) screenW = getResources().getDisplayMetrics().widthPixels;
        if (screenH <= 0) screenH = getResources().getDisplayMetrics().heightPixels;
    }

    private int clampX(int x) {
        int max = Math.max(0, screenW - size);
        return Math.max(0, Math.min(x, max));
    }

    private int clampY(int y) {
        int max = Math.max(0, screenH - size);
        return Math.max(0, Math.min(y, max));
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void addBall() {
        FrameLayout wrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE60A0F12);
        bg.setStroke(Ui.dp(this, 1.2f), 0xFF2FE0C8);
        wrap.setBackground(bg);

        TextView glyph = new TextView(this);
        glyph.setText("♪");
        glyph.setTextSize(17);
        glyph.setTextColor(0xFF2FE0C8);
        glyph.setTypeface(Typeface.DEFAULT_BOLD);
        glyph.setGravity(Gravity.CENTER);
        wrap.addView(glyph, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ballLp = new WindowManager.LayoutParams(size, size, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        ballLp.gravity = Gravity.TOP | Gravity.START;
        int px = (int) Ai.prefs(this).getFloat("ball_x", 0.86f * screenW);
        int py = (int) Ai.prefs(this).getFloat("ball_y", screenH * 0.55f);
        ballLp.x = clampX(px);
        ballLp.y = clampY(py);

        ball = wrap;
        ball.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (hidden) {
                            justRevealed = true;
                            reveal();
                            return true;
                        }
                        justRevealed = false;
                        if (hider != null) h.removeCallbacks(hider);
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = ballLp.x;
                        startY = ballLp.y;
                        dragging = false;
                        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).start();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downRawX;
                        float dy = e.getRawY() - downRawY;
                        if (!dragging && Math.hypot(dx, dy) > Ui.dp(BallService.this, 6)) {
                            dragging = true;
                            showTarget();
                        }
                        if (dragging) {
                            ballLp.x = clampX((int) (startX + dx));
                            ballLp.y = clampY((int) (startY + dy));
                            try {
                                wm.updateViewLayout(ball, ballLp);
                            } catch (Throwable ignored) {
                            }
                            over = nearTarget(ballLp.x, ballLp.y);
                            paintTarget(over);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                        if (dragging) {
                            hideTarget();
                            if (over) {
                                setWanted(BallService.this, false);
                                Toast("悬浮球已关掉");
                                stopSelf();
                                return true;
                            }
                            savePos();
                            hideToEdge();
                        } else if (justRevealed) {
                            justRevealed = false;
                        } else {
                            openRecognize();
                            scheduleHide();
                        }
                        dragging = false;
                        over = false;
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(ball, ballLp);
        } catch (Throwable t) {
            stopSelf();
        }
    }

    private boolean onLeftSide() {
        return ballLp.x + size / 2 < screenW / 2;
    }

    private int shownX() {
        return onLeftSide() ? 0 : screenW - size;
    }

    /** 一帧一帧挪，别瞬移 */
    private void slideTo(final int tx) {
        if (ball == null || ballLp == null) return;
        final int from = ballLp.x, steps = 7;
        if (from == tx) return;
        for (int i = 1; i <= steps; i++) {
            final int k = i;
            h.postDelayed(new Runnable() {
                public void run() {
                    if (ball == null || ballLp == null) return;
                    ballLp.x = from + (tx - from) * k / steps;
                    try {
                        wm.updateViewLayout(ball, ballLp);
                    } catch (Throwable ignored) {
                    }
                }
            }, i * 16);
        }
    }

    /** 收进最近的那条竖边，只露三分之一 */
    private void hideToEdge() {
        if (ballLp == null || ball == null || dragging) return;
        hidden = true;
        slideTo(onLeftSide() ? -(int) (size * 0.62f) : screenW - (int) (size * 0.38f));
    }

    private void reveal() {
        if (ballLp == null) return;
        hidden = false;
        slideTo(shownX());
        scheduleHide();
    }

    private void scheduleHide() {
        if (hider == null) {
            hider = new Runnable() {
                public void run() {
                    if (!dragging) hideToEdge();
                }
            };
        }
        h.removeCallbacks(hider);
        h.postDelayed(hider, 3500);
    }

    private void savePos() {
        Ai.prefs(this).edit()
                .putFloat("ball_x", hidden ? shownX() : ballLp.x)
                .putFloat("ball_y", ballLp.y)
                .apply();
    }

    private boolean nearTarget(int bx, int by) {
        float cx = (screenW - targetSize) / 2f + targetSize / 2f;
        float cy = screenH - targetSize - gap + targetSize / 2f;
        float mx = bx + size / 2f;
        float my = by + size / 2f;
        return Math.hypot(mx - cx, my - cy) < targetSize * 0.85f;
    }

    private void showTarget() {
        if (target != null) return;
        View t = new TargetView(this);
        targetLp = new WindowManager.LayoutParams(targetSize, targetSize, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        targetLp.gravity = Gravity.TOP | Gravity.START;
        targetLp.x = (screenW - targetSize) / 2;
        targetLp.y = screenH - targetSize - gap;
        target = t;
        try {
            wm.addView(target, targetLp);
        } catch (Throwable e) {
            target = null;
        }
    }

    private void hideTarget() {
        if (target == null) return;
        try {
            wm.removeView(target);
        } catch (Throwable ignored) {
        }
        target = null;
    }

    private void paintTarget(boolean hot) {
        if (target instanceof TargetView) ((TargetView) target).setHot(hot);
    }

    private void openRecognize() {
        try {
            Intent i = new Intent(this, RecognizeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private void Toast(String s) {
        try {
            android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /** 底部那个"关"圈：虚线 + 变红表示松手会关 */
    static class TargetView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean hot;

        TargetView(Context c) {
            super(c);
            text.setColor(0xFF9AA5AA);
            text.setTextSize(Ui.dp(c, 15));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setHot(boolean h) {
            hot = h;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - 3;

            p.setStyle(Paint.Style.FILL);
            p.setColor(hot ? 0x33FF5A5A : 0x22000000);
            cv.drawCircle(cx, cy, r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(hot ? 0xFFFF6B6B : 0x66FFFFFF);
            cv.drawCircle(cx, cy, r, p);

            p.setColor(hot ? 0xFFFF6B6B : 0x55FFFFFF);
            for (int i = 0; i < 24; i++) {
                if (i % 2 == 0) continue;
                float a0 = (float) (i * Math.PI * 2 / 24);
                float a1 = (float) ((i + 1) * Math.PI * 2 / 24);
                cv.drawArc(cx - r + 6, cy - r + 6, cx + r - 6, cy + r - 6,
                        (float) Math.toDegrees(a0), (float) Math.toDegrees(a1 - a0) * 0.8f, false, p);
            }

            text.setColor(hot ? 0xFFFF8A8A : 0xCCFFFFFF);
            cv.drawText(hot ? "松手关掉" : "关闭", cx, cy + text.getTextSize() / 3f, text);
            if (hot) invalidate();
        }
    }
}
