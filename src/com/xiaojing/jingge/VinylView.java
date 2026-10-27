package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import java.util.Locale;

/** 极简唱片：只有一个圆，里面是封面。没有槽纹、外环、中心孔、渐变。 */
public class VinylView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint art = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private Bitmap cover;
    private BitmapShader shader;
    private float rotation;
    private boolean spinning;
    private String placeholder = "";

    public VinylView(Context c) {
        super(c);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setCover(Bitmap b) {
        if (b == cover) {
            invalidate();
            return;
        }
        cover = b;
        shader = b == null ? null
                : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    /** 没有封面时画歌名首字，这是鲸歌自己的占位。 */
    public void setPlaceholder(String s) {
        placeholder = s == null ? "" : s;
        invalidate();
    }

    public void setSpinning(boolean s) {
        if (spinning == s) return;
        spinning = s;
        if (s) {
            postOnAnimation(spin);
        } else {
            invalidate();
        }
    }

    public boolean isSpinning() {
        return spinning;
    }

    private final Runnable spin = new Runnable() {
        @Override
        public void run() {
            if (!spinning) return;
            rotation += 0.45f;
            if (rotation >= 360f) rotation -= 360f;
            invalidate();
            postOnAnimation(this);
        }
    };

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(14);
        if (r <= 0) return;

        if (shader != null && cover != null) {
            c.save();
            c.rotate(rotation, cx, cy);
            float scale = (r * 2f) / Math.min(cover.getWidth(), cover.getHeight());
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(cx - cover.getWidth() * scale / 2f,
                    cy - cover.getHeight() * scale / 2f);
            shader.setLocalMatrix(matrix);
            art.setShader(shader);
            c.drawCircle(cx, cy, r, art);
            c.restore();
        } else {
            p.setColor(0xFF14181F);
            c.drawCircle(cx, cy, r, p);
            if (placeholder.length() > 0) {
                p.setColor(0x59FFFFFF);
                p.setTextSize(r * 0.42f);
                p.setTextAlign(Paint.Align.CENTER);
                p.setFakeBoldText(true);
                c.drawText(placeholder.substring(0, 1).toUpperCase(Locale.ROOT),
                        cx, cy + r * 0.14f, p);
                p.setFakeBoldText(false);
            }
            p.setColor(0x6658E0C8);
            c.drawCircle(cx, cy - r * 0.7f, Math.max(dp(2f), r * 0.035f), p);
        }
    }
}
