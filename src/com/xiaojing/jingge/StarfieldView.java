package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** 群星风格的底：慢慢往上飘的星点 + 两层极淡的星云。只在挂着的时候跑。 */
public class StarfieldView extends View {
    private static final int N = 110;

    private final float[] x = new float[N];
    private final float[] y = new float[N];
    private final float[] r = new float[N];
    private final float[] sp = new float[N];
    private final float[] ph = new float[N];

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient g1;
    private RadialGradient g2;
    private long last;
    private long lastFrame;
    private boolean running;
    private float density = 1f;

    public StarfieldView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        Random rnd = new Random(0x51AE7);
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            r[i] = 0.5f + rnd.nextFloat() * 1.4f;
            sp[i] = 0.004f + rnd.nextFloat() * 0.012f;
            ph[i] = rnd.nextFloat() * 6.2832f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        last = 0;
        postInvalidateOnAnimation();
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
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float R = Math.max(w, h);
        g1 = new RadialGradient(w * 0.2f, h * 0.08f, R * 0.55f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.88f), Ui.BG, Shader.TileMode.CLAMP);
        g2 = new RadialGradient(w * 0.95f, h * 0.9f, R * 0.5f,
                Ui.blend(Ui.ACCENT, Ui.BG, 0.94f), Ui.BG, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        c.drawColor(Ui.BG);
        if (g1 != null) {
            neb.setShader(g1);
            c.drawRect(0, 0, w, h, neb);
            neb.setShader(g2);
            c.drawRect(0, 0, w, h, neb);
        }

        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;

        p.setColor(Ui.ACCENT);
        for (int i = 0; i < N; i++) {
            y[i] -= sp[i] * dt;
            if (y[i] < -0.02f) y[i] = 1.02f;
            float a = 0.16f + 0.4f * (0.5f + 0.5f * (float) Math.sin(ph[i] + now * 0.0015f));
            p.setAlpha((int) (a * 255));
            c.drawCircle(x[i] * w, y[i] * h, Math.max(1f, r[i] * density * 0.5f), p);
        }

        // 30fps 够用了，别每帧都画
        if (running && now - lastFrame >= 33) {
            lastFrame = now;
            postInvalidateOnAnimation();
        }
    }
}
