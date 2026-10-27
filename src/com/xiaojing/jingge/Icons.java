package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 全部图标自绘：不引资源、不引 AndroidX，24×24 网格。
 * 好处是同一套几何在通知、按钮、播放页里长得一样。
 */
public class Icons extends View {
    public static final int PLAY = 1, PAUSE = 2, PREV = 3, NEXT = 4, SHUFFLE = 5,
            REPEAT = 6, REPEAT_ONE = 7, CHEVRON_DOWN = 8, REFRESH = 9, SEARCH = 10,
            CLOSE = 11, EQ = 12, NOTE = 13, LIST = 14, CHEVRON_LEFT = 15, FOLDER = 16;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int type = PLAY;
    private int color = Ui.TEXT;
    private float ratio = 0.52f;

    public Icons(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.FILL);
        setClickable(false);
    }

    public Icons icon(int t) {
        type = t;
        invalidate();
        return this;
    }

    public Icons color(int c) {
        color = c;
        invalidate();
        return this;
    }

    /** 图标占控件短边的比例 */
    public Icons ratio(float r) {
        ratio = r;
        invalidate();
        return this;
    }

    public int iconType() {
        return type;
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float s = Math.min(w, h) * ratio / 24f;
        c.save();
        c.translate((w - 24 * s) / 2f, (h - 24 * s) / 2f);
        c.scale(s, s);

        p.setColor(color);
        p.setAlpha(isPressed() ? 140 : 255);
        p.setStrokeWidth(1.9f);
        p.setStyle(Paint.Style.FILL);

        switch (type) {
            case PLAY:
                path.reset();
                path.moveTo(8.2f, 5.4f);
                path.lineTo(19.2f, 12f);
                path.lineTo(8.2f, 18.6f);
                path.close();
                c.drawPath(path, p);
                break;
            case PAUSE:
                rect.set(6.8f, 5.2f, 10.2f, 18.8f);
                c.drawRoundRect(rect, 1.5f, 1.5f, p);
                rect.set(13.8f, 5.2f, 17.2f, 18.8f);
                c.drawRoundRect(rect, 1.5f, 1.5f, p);
                break;
            case PREV:
                path.reset();
                path.moveTo(14.6f, 5f);
                path.lineTo(5.4f, 12f);
                path.lineTo(14.6f, 19f);
                path.close();
                c.drawPath(path, p);
                rect.set(16.2f, 5f, 18.9f, 19f);
                c.drawRoundRect(rect, 1.2f, 1.2f, p);
                break;
            case NEXT:
                path.reset();
                path.moveTo(9.4f, 5f);
                path.lineTo(18.6f, 12f);
                path.lineTo(9.4f, 19f);
                path.close();
                c.drawPath(path, p);
                rect.set(5.1f, 5f, 7.8f, 19f);
                c.drawRoundRect(rect, 1.2f, 1.2f, p);
                break;
            case SHUFFLE:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f);
                path.reset();
                path.moveTo(3.4f, 7.5f);
                path.lineTo(8f, 7.5f);
                path.lineTo(16f, 16.5f);
                path.lineTo(19.4f, 16.5f);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(3.4f, 16.5f);
                path.lineTo(8f, 16.5f);
                path.lineTo(16f, 7.5f);
                path.lineTo(19.4f, 7.5f);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(17.2f, 13.9f);
                path.lineTo(20.8f, 16.5f);
                path.lineTo(17.2f, 19.1f);
                path.close();
                c.drawPath(path, p);
                path.reset();
                path.moveTo(17.2f, 4.9f);
                path.lineTo(20.8f, 7.5f);
                path.lineTo(17.2f, 10.1f);
                path.close();
                c.drawPath(path, p);
                break;
            case REPEAT:
            case REPEAT_ONE:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f);
                rect.set(3.6f, 5.6f, 20.4f, 18.4f);
                c.drawRoundRect(rect, 3.4f, 3.4f, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(17.3f, 10.3f);
                path.lineTo(20.6f, 12f);
                path.lineTo(17.3f, 13.7f);
                path.close();
                c.drawPath(path, p);
                path.reset();
                path.moveTo(6.7f, 10.3f);
                path.lineTo(3.4f, 12f);
                path.lineTo(6.7f, 13.7f);
                path.close();
                c.drawPath(path, p);
                if (type == REPEAT_ONE) {
                    p.setTextSize(8.4f);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setFakeBoldText(true);
                    c.drawText("1", 12f, 15f, p);
                    p.setFakeBoldText(false);
                }
                break;
            case CHEVRON_DOWN:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.1f);
                path.reset();
                path.moveTo(6.5f, 9.5f);
                path.lineTo(12f, 15f);
                path.lineTo(17.5f, 9.5f);
                c.drawPath(path, p);
                break;
            case CHEVRON_LEFT:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.1f);
                path.reset();
                path.moveTo(15f, 6.5f);
                path.lineTo(9f, 12f);
                path.lineTo(15f, 17.5f);
                c.drawPath(path, p);
                break;
            case REFRESH:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.9f);
                rect.set(4.6f, 4.6f, 19.4f, 19.4f);
                c.drawArc(rect, -50f, 300f, false, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(19.6f, 3.2f);
                path.lineTo(21.8f, 7.4f);
                path.lineTo(17.2f, 7.6f);
                path.close();
                c.drawPath(path, p);
                break;
            case SEARCH:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.9f);
                rect.set(4.4f, 4.4f, 15.6f, 15.6f);
                c.drawOval(rect, p);
                p.setStrokeWidth(2.2f);
                c.drawLine(14.4f, 14.4f, 19.6f, 19.6f, p);
                break;
            case CLOSE:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.1f);
                c.drawLine(7f, 7f, 17f, 17f, p);
                c.drawLine(17f, 7f, 7f, 17f, p);
                break;
            case EQ:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.6f);
                c.drawLine(7f, 8f, 7f, 16f, p);
                c.drawLine(12f, 4.5f, 12f, 19.5f, p);
                c.drawLine(17f, 7f, 17f, 17f, p);
                break;
            case NOTE:
                p.setStyle(Paint.Style.FILL);
                rect.set(5.4f, 14.6f, 10.2f, 19.2f);
                c.drawOval(rect, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.7f);
                c.drawLine(9.9f, 17f, 9.9f, 4.8f, p);
                c.drawLine(9.9f, 5.2f, 16.4f, 7f, p);
                break;
            case LIST:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.9f);
                c.drawLine(4.5f, 7f, 19.5f, 7f, p);
                c.drawLine(4.5f, 12f, 19.5f, 12f, p);
                c.drawLine(4.5f, 17f, 19.5f, 17f, p);
                break;
            case FOLDER:
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(3.5f, 7.5f);
                path.lineTo(9f, 7.5f);
                path.lineTo(10.6f, 9.6f);
                path.lineTo(20.5f, 9.6f);
                path.lineTo(20.5f, 18.5f);
                path.lineTo(3.5f, 18.5f);
                path.close();
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        c.restore();
    }
}
