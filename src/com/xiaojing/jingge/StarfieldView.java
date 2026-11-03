package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Path;
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
 * 群星风格的底：四层视差星点 + 会漂的星云 + 流星，一直在动。
 *
 * 让它别看出"循环"的办法不是停，是**周期互不整除**：
 *   · 星云游动 11.3s / 16.7s 两个周期叠着走
 *   · 星点闪烁每颗自己的相位和频率
 *   · 流星间隔 3.5~9 秒随机，方向和速度也随机
 * 所以画面永远不重复，也不会像 GIF 那样"啪"一下回到起点。
 * 只在可见时跑，30fps 封顶；进页面先淡入 1.2 秒。
 */
public class StarfieldView extends View {
    private static final int FAR = 210;
    private static final int MID = 84;
    private static final int NEAR = 30;
    private static final int BRIGHT = 6;
    private static final int N = FAR + MID + NEAR + BRIGHT;

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
    private float density = 1f;
    private final Random rnd = new Random(0x51AE7);

    private final float[] mx = new float[2];
    private final float[] my = new float[2];
    private final float[] mv = new float[2];
    private final float[] ma = new float[2];
    private final float[] ml = new float[2];
    private final float[] mlife = new float[2];
    private long nextMeteor;

    // 科幻层：透视网格地平线、扫描光带、超空间速度线
    private final Paint hud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient scanGrad;
    private float scanH;
    private final int STREAK = 30;
    private final float[] sx = new float[STREAK];
    private final float[] sy = new float[STREAK];
    private final float[] sv = new float[STREAK];
    private final float[] sl = new float[STREAK];
    private final float[] sa = new float[STREAK];
    private long nextWarp;
    private float warpX, warpY, warpA, warpLen;
    private long warpAt;
    private static final int CYAN = 0xFF7BE9FF;
    private static final int VIOLET = 0xFF9B7BFF;
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path beamPath = new Path();

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
        for (int i = 0; i < STREAK; i++) {
            sx[i] = rnd.nextFloat();
            sy[i] = rnd.nextFloat();
            sv[i] = 0.10f + rnd.nextFloat() * 0.26f;
            sl[i] = 0.02f + rnd.nextFloat() * 0.05f;
            sa[i] = 0.10f + rnd.nextFloat() * 0.22f;
        }
        LIVE.add(new WeakReference<StarfieldView>(this));
    }

    /** 重新播一次（页面切回来、回到前台时用）。 */
    public void replay() {
        showStart = 0;
        last = 0;
        lastFrame = 0;
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
        scanH = h * 0.22f;
        scanGrad = new LinearGradient(0, 0, 0, scanH, new int[]{
                alpha(Ui.ACCENT, 0), alpha(Ui.ACCENT, 22), alpha(CYAN, 9), alpha(Ui.ACCENT, 0)},
                null, Shader.TileMode.CLAMP);
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

        // 只有入场那 1.2 秒做淡入，之后一直动
        float appear = Math.min(1f, t / 1.2f);
        appear = appear * appear * (3 - 2 * appear);

        // 三个互不整除的周期，叠起来看不出循环
        float w1 = (float) Math.sin(2 * Math.PI * t / 11.3);
        float w2 = (float) Math.sin(2 * Math.PI * t / 16.7 + 1.7);
        float w3 = (float) Math.sin(2 * Math.PI * t / 23.9 + 3.1);

        cv.drawColor(Ui.BG);

        // 星云：跟着画布平移慢慢游，入场时亮度从 0 拉到满
        if (g1 != null) {
            float nebA = appear;
            int save = cv.save();
            cv.translate(w1 * w * 0.035f, w2 * h * 0.025f);
            neb.setShader(g1);
            neb.setAlpha((int) ((206 + 20 * w2 + 8 * w3) * nebA));
            cv.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, neb);
            cv.restoreToCount(save);

            save = cv.save();
            cv.translate(w2 * w * 0.03f, w3 * h * 0.03f);
            neb.setShader(g2);
            neb.setAlpha((int) ((200 + 18 * w1 + 10 * w2) * nebA));
            cv.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, neb);
            cv.restoreToCount(save);

            neb.setShader(g3);
            neb.setAlpha((int) ((110 + 26 * w3 + 12 * w1) * nebA));
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

            float flash = (float) Math.sin(ph[i] + t * tw[i]);
            float twk = 0.55f + 0.45f * flash;
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

        drawRings(cv, w, h, t, appear);
        drawBeams(cv, w, h, t, appear);
        drawHorizon(cv, w, h, t, appear);
        drawStreaks(cv, w, h, dt, appear);
        drawScan(cv, w, h, t);
        drawWarp(cv, w, h, now);

        // 流星：一直都会有，间隔随机
        if (nextMeteor == 0 || now > nextMeteor) {
            spawnMeteor();
            long gap = 3200 + (long) (rnd.nextFloat() * 6200);
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

        if (running && now - lastFrame >= 28) {
            lastFrame = now;
            postInvalidateOnAnimation();
        }
    }

    private static int alpha(int color, int a) {
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 同心刻度环：三个环反向缓慢旋转，HUD 的骨架 */
    private void drawRings(Canvas cv, int w, int h, float t, float appear) {
        if (appear <= 0.01f) return;
        float cx = w * 0.74f;
        float cy = h * 0.32f;
        float[] radii = {w * 0.30f, w * 0.42f, w * 0.54f};
        float[] speed = {5.5f, -3.4f, 2.2f};
        hud.setStyle(Paint.Style.STROKE);
        for (int r = 0; r < radii.length; r++) {
            float rad = radii[r];
            if (rad <= 0) continue;
            int baseA = (int) ((r == 1 ? 26 : 18) * appear);
            hud.setStrokeWidth(Math.max(1f, density * 0.6f));
            hud.setColor(alpha(r == 2 ? VIOLET : Ui.ACCENT, baseA));
            cv.drawCircle(cx, cy, rad, hud);

            // 刻度：每 7.5° 一根短线
            float start = t * speed[r] * (float) Math.PI / 180f;
            int ticks = 48;
            for (int i = 0; i < ticks; i++) {
                float a = start + i * (float) (Math.PI * 2 / ticks);
                boolean major = i % 6 == 0;
                float inner = rad - (major ? 9f : 5f) * density;
                float px = cx + (float) Math.cos(a) * rad;
                float py = cy + (float) Math.sin(a) * rad;
                float qx = cx + (float) Math.cos(a) * inner;
                float qy = cy + (float) Math.sin(a) * inner;
                hud.setStrokeWidth(Math.max(1f, density * (major ? 1.1f : 0.6f)));
                hud.setColor(alpha(major ? CYAN : Ui.ACCENT,
                        (int) ((major ? baseA + 26 : baseA) * appear)));
                cv.drawLine(px, py, qx, qy, hud);
            }
        }
    }

    /** 从地平线消失点往上打的几道光柱，缓慢摆 */
    private void drawBeams(Canvas cv, int w, int h, float t, float appear) {
        if (appear <= 0.01f) return;
        float vpx = w * 0.5f;
        float vpy = h * 0.80f;
        hud.setStyle(Paint.Style.FILL);
        beamPath.reset();
        for (int i = 0; i < 4; i++) {
            float sway = (float) Math.sin(t * (0.16f + i * 0.05f) + i * 1.9f);
            float ang = -1.5708f + (i - 1.5f) * 0.30f + sway * 0.05f;
            float topX = vpx + (float) Math.cos(ang) * h * 0.95f;
            float topY = vpy + (float) Math.sin(ang) * h * 0.95f;
            float spread = (26f + i * 10f) * density;
            beamPath.reset();
            beamPath.moveTo(vpx - spread * 0.25f, vpy);
            beamPath.lineTo(vpx + spread * 0.25f, vpy);
            beamPath.lineTo(topX + spread, topY);
            beamPath.lineTo(topX - spread, topY);
            beamPath.close();
            int a = (int) ((i % 2 == 0 ? 7 : 5) * appear);
            hud.setColor(alpha(i % 2 == 0 ? CYAN : VIOLET, a));
            cv.drawPath(beamPath, hud);
        }
    }

    /** 底部透视网格：越远越密、越往前跑，科幻片里的"地平线" */
    private void drawHorizon(Canvas cv, int w, int h, float t, float appear) {
        float horizon = h * 0.80f;
        float vpx = w * 0.5f;
        hud.setShader(null);
        hud.setStyle(Paint.Style.STROKE);
        hud.setStrokeWidth(Math.max(1f, density * 0.7f));

        // 竖线：向消失点收
        for (int i = -7; i <= 7; i++) {
            float bx = vpx + i * w * 0.19f;
            int a = (int) ((i == 0 ? 46 : 30 - Math.abs(i) * 2.2f) * appear);
            hud.setColor(alpha(Ui.ACCENT, a));
            cv.drawLine(vpx, horizon, bx, h * 1.04f, hud);
        }

        // 横线：间距按透视压缩，整体缓慢往前推
        float phase = (t * 0.11f) % 1f;
        for (int i = 0; i < 9; i++) {
            float k = (i + phase) / 9f;
            float y = horizon + (h * 0.25f) * k * k;
            int a = (int) ((8 + 52 * k) * appear);
            hud.setColor(alpha(Ui.ACCENT, a));
            cv.drawLine(0, y, w, y, hud);
        }

        // 地平线本身
        hud.setStrokeWidth(Math.max(1f, density * 1.1f));
        hud.setColor(alpha(CYAN, (int) (58 * appear)));
        cv.drawLine(0, horizon, w, horizon, hud);
    }

    /** 超空间速度线：短线往上飞，越近越长越亮 */
    private void drawStreaks(Canvas cv, int w, int h, float dt, float appear) {
        hud.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < STREAK; i++) {
            sy[i] -= sv[i] * dt;
            if (sy[i] < -0.05f) {
                sy[i] = 1.05f;
                sx[i] = rnd.nextFloat();
                sv[i] = 0.10f + rnd.nextFloat() * 0.26f;
                sa[i] = 0.10f + rnd.nextFloat() * 0.22f;
            }
            float px = sx[i] * w;
            float py = sy[i] * h;
            float len = sl[i] * h * (0.4f + (1f - sy[i]) * 1.6f);
            hud.setStrokeWidth(Math.max(1f, density * 0.8f));
            hud.setColor(alpha(CYAN, (int) (sa[i] * 210 * appear)));
            cv.drawLine(px, py, px, py + len, hud);
        }
    }

    /** 一道很淡的扫描光带从上往下走 */
    private void drawScan(Canvas cv, int w, int h, float t) {
        if (scanGrad == null) return;
        float span = h + scanH;
        float y = (t * 34f) % span - scanH;
        int save = cv.save();
        cv.translate(0, y);
        hud.setShader(scanGrad);
        hud.setStyle(Paint.Style.FILL);
        cv.drawRect(0, 0, w, scanH, hud);
        hud.setShader(null);
        cv.restoreToCount(save);
    }

    /** 偶尔一道贯穿画面的跃迁光 */
    private void drawWarp(Canvas cv, int w, int h, long now) {
        if (warpAt == 0) {
            warpAt = now;
            nextWarp = now + 9000 + (long) (rnd.nextFloat() * 9000);
        }
        if (warpA <= 0f && now > nextWarp) {
            warpA = 1f;
            warpAt = now;
            warpX = rnd.nextFloat() * w * 0.5f;
            warpY = rnd.nextFloat() * h * 0.3f;
            warpLen = w * (0.55f + rnd.nextFloat() * 0.4f);
            nextWarp = now + 9000 + (long) (rnd.nextFloat() * 9000);
        }
        if (warpA <= 0f) return;
        float age = (now - warpAt) / 1000f;
        warpA = Math.max(0f, 1f - age / 0.9f);
        float x = warpX + age * w * 1.35f;
        float y = warpY + age * h * 0.30f;
        hud.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < 3; k++) {
            hud.setStrokeWidth((3 - k) * density * 0.9f);
            hud.setColor(alpha(k == 0 ? 0xFFFFFFFF : CYAN, (int) (warpA * (150 - k * 45))));
            float x0 = x - warpLen * (0.55f + k * 0.25f);
            float y0 = y - warpLen * (0.13f + k * 0.06f);
            cv.drawLine(x0, y0, x, y, hud);
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
