package com.xiaojing.jingge;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * 群星风格的面：暗底 + 细网格 + 外发光描边 + 一道沿边扫过的光。
 *
 * 光不是整圈一起呼吸（那样像在喘），是沿边匀速走：
 * 用 LinearGradient 做一段窄光，靠 Matrix 平移它，一圈走 4 秒。
 * 这样只需要多一次 drawRoundRect，不按段画线。
 */
final class GlowDrawable extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final Matrix mat = new Matrix();
    private final int fill;
    private final int accent;
    private final float radius;
    private final float grid;
    private final boolean border;

    private LinearGradient shine;
    private int shineW;

    GlowDrawable(int fill, int accent, float radius, float grid, boolean border) {
        this.fill = fill;
        this.accent = accent;
        this.radius = radius;
        this.grid = grid;
        this.border = border;
    }

    @Override
    public void draw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float breathe = 0.5f + 0.5f * (float) Math.sin(now / 1400.0);
        Rect b = getBounds();
        rf.set(b);
        path.reset();
        path.addRoundRect(rf, radius, radius, Path.Direction.CW);

        if (border) {
            // 外发光：三层往外扩，越外越淡；呼吸幅度压小，别晃眼
            p.setStyle(Paint.Style.STROKE);
            p.setShader(null);
            for (int i = 3; i >= 1; i--) {
                p.setStrokeWidth(i * 1.7f);
                p.setColor(alpha(accent, (int) ((3 + i * 3) * (0.65f + 0.45f * breathe))));
                c.drawRoundRect(rf, radius, radius, p);
            }
        }

        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(fill);
        c.drawPath(path, p);

        if (grid > 0) {
            int save = c.save();
            c.clipPath(path);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(alpha(accent, 12));
            for (float x = b.left + grid; x < b.right; x += grid) {
                c.drawLine(x, b.top, x, b.bottom, p);
            }
            for (float y = b.top + grid; y < b.bottom; y += grid) {
                c.drawLine(b.left, y, b.right, y, p);
            }
            c.restoreToCount(save);
        }

        if (border) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f);
            p.setShader(null);
            p.setColor(alpha(accent, (int) (46 + 30 * breathe)));
            c.drawRoundRect(rf, radius, radius, p);

            // 沿边扫光
            if (b.width() > 8) {
                if (shine == null || shineW != b.width()) {
                    shineW = b.width();
                    float w = shineW;
                    shine = new LinearGradient(0, 0, w, 0, new int[]{
                            alpha(accent, 0),
                            alpha(accent, 26),
                            alpha(accent, 120),
                            alpha(accent, 26),
                            alpha(accent, 0),
                    }, new float[]{0f, 0.40f, 0.50f, 0.60f, 1f}, Shader.TileMode.CLAMP);
                }
                float phase = (now % 4200L) / 4200f;
                mat.setTranslate(-shineW + phase * shineW * 2f, 0);
                shine.setLocalMatrix(mat);
                p.setShader(shine);
                p.setStrokeWidth(1.6f);
                c.drawRoundRect(rf, radius, radius, p);
                p.setShader(null);
            }
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) invalidateSelf();
        return changed;
    }

    private static int alpha(int color, int a) {
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    public void setAlpha(int a) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
