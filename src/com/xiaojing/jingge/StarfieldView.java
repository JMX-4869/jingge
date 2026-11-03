package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 群星风格的底：四层视差星点 + 会漂的星云 + 流星。
 *
 * ⚠️ 它是一段"演出"，不是循环动画：
 *   0～1.2s  切进来，星点从暗到亮
 *   0～6s    流星一两颗，星云慢慢浮上来
 *   6～9.5s  越转越慢，最后停住
 *   9.5s 之后 一帧都不再画（省电），画面定格
 * 再次"播一次"的时机：页面重新可见、回到前台、或手动 replay()。
 */
public class StarfieldView extends View {
    private static final int FAR = 210;
    private static final int MID = 84;
    private static final int NEAR = 30;
    private static final int BRIGHT = 6;
    private static final int N = FAR + MID + NEAR + BRIGHT;

    /** 整段演出的长度（秒） */
    private static final float SHOW = 9.5f;
    /** 流星最晚出现到第几秒 */
    private static final float METEOR_UNTIL = 6.0f;

    private static final List<WeakReference<StarfieldView>> LIVE =
            new ArrayList<WeakReference<StarfieldView>>();

    private final float[] x = new float[N];
    private final float[] y = new float[N];
    private final float[] r = new float[N];
    private final float[] sp = new float[N];
    private final float[] ph = new float[N];
    private final float[] tw = new float[N];
    private final float[] al = new float[N];
    private final float[] dx = new float[N];

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient g1;
    private RadialGradient g2;
    private RadialGradient g3;

    private long last;
    private long lastFrame;
    private long showStart;
    private boolean running;
    private boolean finished;
    private float density = 1f;
    private final Random rnd = new Random(0x51AE7);

    private final float[] mx = new float[2];
    private final float[] my = new float[2];
    private final float[] mv = new float[2];
    private final float[] ma = new float[2];
    private final float[] ml = new float[2];
    private final float[] mlife = new float[2];
    private long nextMeteor;

    public StarfieldView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            ph[i] = rnd.nextFloat() * 6.2832f;
            dx[i] = (rnd.nextFloat() - 0.5f) * 0.006f;
            if (i < FAR) {
                r[i] = 0.35f + rnd.nextFloat() * 0.45f;
                sp[i] = 0.003f + rnd.nextFloat() * 0.005f;
                al[i] = 0.08f + rnd.nextFloat() * 0.13f;
                tw[i] = 0.6f + rnd.nextFloat() * 1.2f;
            } else if (i < FAR + MID) {
                r[i] = 0.6f + rnd.nextFloat() * 0.6f;
                sp[i] = 0.008f + rnd.nextFloat() * 0.010f;
                al[i] = 0.16f + rnd.nextFloat() * 0.18f;
                tw[i] = 0.5f + rnd.nextFloat() * 1.0f;
            } else if (i < FAR + MID + NEAR) {
                r[i] = 1.1f + rnd.nextFloat() * 1.0f;
                sp[i] = 0.016f + rnd.nextFloat() * 0.016f;
                al[i] = 0.30f + rnd.nextFloat() * 0.28f;
                tw[i] = 0.4f + rnd.nextFloat() * 0.8f;
            } else {
                r[i] = 1.5f + rnd.nextFloat() * 0.8f;
                sp[i] = 0.024f + rnd.nextFloat() * 0.012f;
                al[i] = 0.55f + rnd.nextFloat() * 0.30f;
                tw[i] = 0.35f + rnd.nextFloat() * 0.5f;
            }
        }
        for (int i = 0; i < 2; i++) mlife[i] = -1f;
        LIVE.add(new WeakReference<StarfieldView>(this));
    }

    /** 重新播一次（页面切回来、回到前台时用）。 */
    public void replay() {
        showStart = 0;
        last = 0;
        lastFrame = 0;
        finished = false;
        running = true;
        nextMeteor = 0;
        for (int i = 0; i < 2; i++) mlife[i] = -1f;
        invalidate();
    }

    /** 所有还活着的星野一起重播。 */
    static void replayAll() {
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            StarfieldView v = LIVE.get(i).get();
            if (v == null) {
                LIVE.remove(i);
            } else {
                v.replay();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        replay();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        // 每次重新露面，从头演一次
        if (v == VISIBLE) replay();
        else running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float R = Math.max(w, h);
        g1 = new RadialGradient(w * 0.16f, h * 0.04f, R * 0.68f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.84f), Ui.BG, Shader.TileMode.CLAMP);
        g2 = new RadialGradient(w * 0.96f, h * 0.94f, R * 0.60f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.92f), Ui.BG, Shader.TileMode.CLAMP);
        g3 = new RadialGradient(w * 0.52f, h * 0.58f, R * 0.78f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.95f), Ui.BG, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (showStart == 0) showStart = now;
        float t = (now - showStart) / 1000f;
        float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;

        // 演出进度 0→1；之后画面定格
        float prog = Math.min(1f, t / SHOW);
        // 入场淡入
        float appear = Math.min(1f, t / 1.2f);
        appear = appear * appear * (3 - 2 * appear);
        // 越到后面越慢，最后停住
        float slow = (float) Math.pow(1f - prog, 1.35f);

        cv.drawColor(Ui.BG);

        // 星云：跟着画布平移慢慢游，入场时亮度从 0 拉到满
        if (g1 != null) {
            float nebA = appear * (1f - 0.15f * prog);
            int save = cv.save();
            cv.translate((float) Math.sin(t * 0.045f) * w * 0.035f,
                    (float) Math.cos(t * 0.037f) * h * 0.025f);
            neb.setShader(g1);
            neb.setAlpha((int) ((206 + 26 * Math.sin(t * 0.33f)) * nebA));
            cv.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, neb);
            cv.restoreToCount(save);

            save = cv.save();
            cv.translate((float) Math.cos(t * 0.032f + 1f) * w * 0.03f,
                    (float) Math.sin(t * 0.041f + 2f) * h * 0.03f);
            neb.setShader(g2);
            neb.setAlpha((int) ((200 + 28 * Math.sin(t * 0.25f + 2f)) * nebA));
            cv.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, neb);
            cv.restoreToCount(save);

            neb.setShader(g3);
            neb.setAlpha((int) ((110 + 44 * Math.sin(t * 0.19f + 4f)) * nebA));
            cv.drawRect(0, 0, w, h, neb);
        }

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < N; i++) {
            y[i] -= sp[i] * dt * slow;
            x[i] += dx[i] * dt * slow;
            if (y[i] < -0.03f) {
                y[i] = 1.03f;
                x[i] = rnd.nextFloat();
            }
            if (x[i] < -0.02f) x[i] = 1.02f;
            if (x[i] > 1.02f) x[i] = -0.02f;

            // 闪烁随演出衰减：结束时星点是稳的，不再呼吸
            float flash = (float) Math.sin(ph[i] + t * tw[i]);
            float twk = 0.55f + 0.45f * flash * (0.15f + 0.85f * (1f - prog));
            int a = (int) (al[i] * twk * appear * 255);
            if (a <= 1) continue;
            float px = x[i] * w;
            float py = y[i] * h;
            float rad = Math.max(1f, r[i] * density * 0.55f);

            p.setColor((i % 5) != 0 ? Ui.ACCENT : 0xFFDCE8EC);
            p.setAlpha(a);
            cv.drawCircle(px, py, rad, p);

            if (i >= FAR + MID) {
                p.setAlpha(Math.max(1, a / 5));
                cv.drawCircle(px, py, rad * 3.4f, p);
            }
            if (i >= FAR + MID + NEAR) {
                p.setAlpha(Math.max(1, a / 3));
                float L = rad * 6.5f;
                p.setStrokeWidth(Math.max(1f, rad * 0.7f));
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(px - L, py, px + L, py, p);
                cv.drawLine(px, py - L, px, py + L, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        // 流星：只在演出前半段出现，之后不再生
        if (t < METEOR_UNTIL && (nextMeteor == 0 || now > nextMeteor)) {
            spawnMeteor();
            long gap = 900 + (long) (rnd.nextFloat() * 2600);
            nextMeteor = now + gap;
        }
        boolean meteorAlive = false;
        for (int i = 0; i < 2; i++) {
            if (mlife[i] < 0) continue;
            mlife[i] += dt;
            float frac = mlife[i] / ml[i];
            if (frac > 1f) {
                mlife[i] = -1f;
                continue;
            }
            meteorAlive = true;
            float vx = (float) Math.cos(ma[i]);
            float vy = (float) Math.sin(ma[i]);
            mx[i] += vx * mv[i] * dt;
            my[i] += vy * mv[i] * dt;

            float fade = frac < 0.18f ? frac / 0.18f : 1f - (frac - 0.18f) / 0.82f;
            fade = Math.max(0f, fade) * appear;
            float len = 54f * density;
            p.setStyle(Paint.Style.STROKE);
            for (int k = 0; k < 4; k++) {
                float k0 = k / 4f, k1 = (k + 1) / 4f;
                p.setStrokeWidth(Math.max(1f, (1f - k0) * 2.2f * density));
                p.setColor(Ui.blend(Ui.ACCENT, 0xFFFFFFFF, k0 * 0.5f));
                p.setAlpha((int) (fade * 170 * (1f - k0)));
                cv.drawLine(mx[i] - vx * len * k0, my[i] - vy * len * k0,
                        mx[i] - vx * len * k1, my[i] - vy * len * k1, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setAlpha((int) (fade * 235));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(mx[i], my[i], Math.max(1.2f, 1.6f * density), p);
        }

        boolean done = prog >= 1f && !meteorAlive;
        if (done) {
            // 定格：不再请求下一帧
            if (!finished) finished = true;
            return;
        }
        if (running && now - lastFrame >= 28) {
            lastFrame = now;
            postInvalidateOnAnimation();
        }
    }

    private void spawnMeteor() {
        for (int i = 0; i < 2; i++) {
            if (mlife[i] >= 0) continue;
            mx[i] = rnd.nextFloat() * getWidth() * 0.85f;
            my[i] = rnd.nextFloat() * getHeight() * 0.35f;
            mv[i] = (260f + rnd.nextFloat() * 300f) * density;
            ma[i] = (float) (Math.PI * (0.16 + rnd.nextFloat() * 0.12));
            ml[i] = 0.9f + rnd.nextFloat() * 0.7f;
            mlife[i] = 0f;
            return;
        }
    }
}
