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
 * 群星风格的底：三层视差的星点 + 缓慢呼吸的星云 + 偶尔划过的流星。
 * 只在可见时跑，30fps 封顶；极简风格不会创建它。
 */
public class StarfieldView extends View {
    private static final int FAR = 170;
    private static final int MID = 70;
    private static final int NEAR = 26;
    private static final int N = FAR + MID + NEAR;

    private final float[] x = new float[N];
    private final float[] y = new float[N];
    private final float[] r = new float[N];
    private final float[] sp = new float[N];
    private final float[] ph = new float[N];
    private final float[] al = new float[N];

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

    private final float[] mx = new float[3];
    private final float[] my = new float[3];
    private final float[] mv = new float[3];
    private final float[] ma = new float[3];
    private final float[] ml = new float[3];
    private final float[] mlife = new float[3];
    private long nextMeteor;

    public StarfieldView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            ph[i] = rnd.nextFloat() * 6.2832f;
            if (i < FAR) {
                r[i] = 0.4f + rnd.nextFloat() * 0.5f;
                sp[i] = 0.004f + rnd.nextFloat() * 0.006f;
                al[i] = 0.10f + rnd.nextFloat() * 0.16f;
            } else if (i < FAR + MID) {
                r[i] = 0.7f + rnd.nextFloat() * 0.7f;
                sp[i] = 0.010f + rnd.nextFloat() * 0.014f;
                al[i] = 0.18f + rnd.nextFloat() * 0.22f;
            } else {
                r[i] = 1.3f + rnd.nextFloat() * 1.2f;
                sp[i] = 0.022f + rnd.nextFloat() * 0.020f;
                al[i] = 0.35f + rnd.nextFloat() * 0.35f;
            }
        }
        for (int i = 0; i < 3; i++) mlife[i] = -1f;
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
        g1 = new RadialGradient(w * 0.18f, h * 0.06f, R * 0.62f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.86f), Ui.BG, Shader.TileMode.CLAMP);
        g2 = new RadialGradient(w * 0.95f, h * 0.92f, R * 0.55f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.93f), Ui.BG, Shader.TileMode.CLAMP);
        g3 = new RadialGradient(w * 0.55f, h * 0.55f, R * 0.72f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.96f), Ui.BG, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || Ui.FX == 0) return;

        long now = SystemClock.uptimeMillis();
        if (t0 == 0) t0 = now;
        float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float t = (now - t0) / 1000f;

        c.drawColor(Ui.BG);

        if (g1 != null) {
            neb.setShader(g1);
            neb.setAlpha((int) (200 + 30 * Math.sin(t * 0.35f)));
            c.drawRect(0, 0, w, h, neb);
            neb.setShader(g2);
            neb.setAlpha((int) (200 + 30 * Math.sin(t * 0.27f + 2f)));
            c.drawRect(0, 0, w, h, neb);
            neb.setShader(g3);
            neb.setAlpha((int) (120 + 40 * Math.sin(t * 0.21f + 4f)));
            c.drawRect(0, 0, w, h, neb);
        }

        p.setColor(Ui.ACCENT);
        int limit = Ui.FX >= 2 ? N : FAR + MID;
        float dim = Ui.FX >= 2 ? 1f : 0.55f;
        for (int i = 0; i < limit; i++) {
            y[i] -= sp[i] * dt;
            if (y[i] < -0.02f) {
                y[i] = 1.02f;
                x[i] = rnd.nextFloat();
            }
            float tw = 0.55f + 0.45f * (float) Math.sin(ph[i] + t * (0.8f + (i % 7) * 0.19f));
            int a = (int) (al[i] * tw * dim * 255);
            if (a <= 0) continue;
            p.setAlpha(a);
            float px = x[i] * w;
            float py = y[i] * h;
            float rad = Math.max(1f, r[i] * density * 0.5f);
            c.drawCircle(px, py, rad, p);
            if (i >= FAR + MID) {
                p.setAlpha(a / 4);
                c.drawCircle(px, py, rad * 3.2f, p);
            }
        }

        if (Ui.FX < 2) return;
        if (now > nextMeteor) {
            spawnMeteor();
            nextMeteor = now + 2200 + (long) (rnd.nextFloat() * 4200);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Ui.ACCENT);
        for (int i = 0; i < 3; i++) {
            if (mlife[i] < 0) continue;
            mlife[i] += dt;
            float frac = mlife[i] / ml[i];
            if (frac > 1f) {
                mlife[i] = -1f;
                continue;
            }
            mx[i] += (float) Math.cos(ma[i]) * mv[i] * dt;
            my[i] += (float) Math.sin(ma[i]) * mv[i] * dt;
            int a = (int) (200 * (1f - frac) * (frac < 0.15f ? frac / 0.15f : 1f));
            p.setAlpha(Math.max(0, a));
            p.setStrokeWidth(Math.max(1f, density));
            float len = 26f * density;
            c.drawLine(mx[i], my[i],
                    mx[i] - (float) Math.cos(ma[i]) * len,
                    my[i] - (float) Math.sin(ma[i]) * len, p);
        }
        p.setStyle(Paint.Style.FILL);

        if (running && now - lastFrame >= 28) {
            lastFrame = now;
            postInvalidateOnAnimation();
        }
    }

    private void spawnMeteor() {
        for (int i = 0; i < 3; i++) {
            if (mlife[i] >= 0) continue;
            mx[i] = rnd.nextFloat() * getWidth() * 0.9f;
            my[i] = rnd.nextFloat() * getHeight() * 0.45f;
            mv[i] = (240f + rnd.nextFloat() * 260f) * density;
            ma[i] = (float) Math.toRadians(28 + rnd.nextFloat() * 26);
            ml[i] = 0.55f + rnd.nextFloat() * 0.5f;
            mlife[i] = 0f;
            return;
        }
    }
}
