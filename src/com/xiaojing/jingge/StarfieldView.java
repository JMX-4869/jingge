package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * 群星风格的底：四层视差星点 + 会漂的星云 + 偶尔划过的流星。
 *
 * 这一版的重点不是"更多"，是"更深"：
 *   · 星星分四层，速度和亮度不一样，近处的带光晕、最亮的带十字星芒
 *   · 星云靠 canvas 平移让渐变缓慢游动（不新建 Gradient，避免每帧分配）
 *   · 流星 3.5~9 秒一颗，尾巴用四段递减的线画出来，不是一根棍
 * 只在可见时跑，30fps 封顶。
 */
public class StarfieldView extends View {
    private static final int FAR = 210;
    private static final int MID = 84;
    private static final int NEAR = 30;
    private static final int BRIGHT = 6;
    private static final int N = FAR + MID + NEAR + BRIGHT;

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
    private long t0;
    private boolean running;
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        last = 0;
        lastFrame = 0;
        t0 = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        running = v == VISIBLE;
        if (running) {
            last = 0;
            lastFrame = 0;
            postInvalidateOnAnimation();
        }
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
        if (t0 == 0) t0 = now;
        float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float t = (now - t0) / 1000f;

        cv.drawColor(Ui.BG);

        // 星云：靠画布平移让渐变游动，不重建 Gradient
        if (g1 != null) {
            int save = cv.save();
            cv.translate((float) Math.sin(t * 0.045f) * w * 0.035f,
                    (float) Math.cos(t * 0.037f) * h * 0.025f);
            neb.setShader(g1);
            neb.setAlpha((int) (206 + 26 * Math.sin(t * 0.33f)));
            cv.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, neb);
            cv.restoreToCount(save);

            save = cv.save();
            cv.translate((float) Math.cos(t * 0.032f + 1f) * w * 0.03f,
                    (float) Math.sin(t * 0.041f + 2f) * h * 0.03f);
            neb.setShader(g2);
            neb.setAlpha((int) (200 + 28 * Math.sin(t * 0.25f + 2f)));
            cv.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, neb);
            cv.restoreToCount(save);

            neb.setShader(g3);
            neb.setAlpha((int) (110 + 44 * Math.sin(t * 0.19f + 4f)));
            cv.drawRect(0, 0, w, h, neb);
        }

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < N; i++) {
            y[i] -= sp[i] * dt;
            x[i] += dx[i] * dt;
            if (y[i] < -0.03f) {
                y[i] = 1.03f;
                x[i] = rnd.nextFloat();
            }
            if (x[i] < -0.02f) x[i] = 1.02f;
            if (x[i] > 1.02f) x[i] = -0.02f;

            float twk = 0.55f + 0.45f * (float) Math.sin(ph[i] + t * tw[i]);
            int a = (int) (al[i] * twk * 255);
            if (a <= 1) continue;
            float px = x[i] * w;
            float py = y[i] * h;
            float rad = Math.max(1f, r[i] * density * 0.55f);

            // 冷白和青色混着来，纯青会显得假
            p.setColor((i % 5) != 0 ? Ui.ACCENT : 0xFFDCE8EC);
            p.setAlpha(a);
            cv.drawCircle(px, py, rad, p);

            if (i >= FAR + MID) {
                p.setAlpha(Math.max(1, a / 5));
                cv.drawCircle(px, py, rad * 3.4f, p);
            }
            if (i >= FAR + MID + NEAR) {
                // 最亮的几颗：十字星芒
                p.setAlpha(Math.max(1, a / 3));
                float L = rad * 6.5f;
                p.setStrokeWidth(Math.max(1f, rad * 0.7f));
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(px - L, py, px + L, py, p);
                cv.drawLine(px, py - L, px, py + L, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        // 流星
        if (now > nextMeteor) {
            spawnMeteor();
            nextMeteor = now + 3500 + (long) (rnd.nextFloat() * 5500);
        }
        for (int i = 0; i < 2; i++) {
            if (mlife[i] < 0) continue;
            mlife[i] += dt;
            float frac = mlife[i] / ml[i];
            if (frac > 1f) {
                mlife[i] = -1f;
                continue;
            }
            float vx = (float) Math.cos(ma[i]);
            float vy = (float) Math.sin(ma[i]);
            mx[i] += vx * mv[i] * dt;
            my[i] += vy * mv[i] * dt;

            float fade = frac < 0.18f ? frac / 0.18f : 1f - (frac - 0.18f) / 0.82f;
            fade = Math.max(0f, fade);
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
