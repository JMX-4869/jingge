package com.xiaojing.jingge;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** 熵光风格：暗底 + 细网格 + 外发光描边。 */
final class GlowDrawable extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final int fill;
    private final int accent;
    private final float radius;
    private final float grid;
    private final boolean border;

    GlowDrawable(int fill, int accent, float radius, float grid, boolean border) {
        this.fill = fill;
        this.accent = accent;
        this.radius = radius;
        this.grid = grid;
        this.border = border;
    }

    @Override
    public void draw(Canvas c) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(SystemClock.uptimeMillis() / 900.0);
        Rect b = getBounds();
        rf.set(b);
        path.reset();
        path.addRoundRect(rf, radius, radius, Path.Direction.CW);

        if (border) {
            // 外发光：三层往外扩，越外越淡
            p.setStyle(Paint.Style.STROKE);
            for (int i = 3; i >= 1; i--) {
                p.setStrokeWidth(i * 1.8f);
                p.setColor(alpha(accent, (int) ((3 + i * 4) * (0.55f + 0.75f * pulse))));
                c.drawRoundRect(rf, radius, radius, p);
            }
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawPath(path, p);

        if (grid > 0) {
            int save = c.save();
            c.clipPath(path);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(alpha(accent, 13));
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
            p.setColor(alpha(accent, (int) (60 + 70 * pulse)));
            c.drawRoundRect(rf, radius, radius, p);
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) invalidateSelf();
        return changed;
    }

    private static int alpha(int color, int a) {
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
