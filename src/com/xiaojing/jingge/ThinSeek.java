package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * 2dp 的进度条。没有 thumb 的常驻装饰 —— 拖动时才露出一个点。
 * 原生 SeekBar 的默认样式（圆 thumb、轨道槽）在这套界面上太重。
 */
public class ThinSeek extends View {
    public interface Listener {
        void onSeekStart();

        void onSeekMove(int ms);

        void onSeekEnd(int ms);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int duration = 1;
    private int position;
    private boolean dragging;
    private Listener listener;

    public ThinSeek(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
        setClickable(true);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int position() {
        return position;
    }

    public void setDuration(int d) {
        int v = Math.max(1, d);
        if (v != duration) {
            duration = v;
            invalidate();
        }
    }

    public void setPosition(int ms) {
        if (dragging) return;
        int v = Math.max(0, Math.min(ms, duration));
        if (v != position) {
            position = v;
            invalidate();
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas c) {
        float y = getHeight() / 2f;
        float left = getPaddingLeft();
        float w = getWidth() - left - getPaddingRight();
        if (w <= 0) return;

        p.setStrokeWidth(dp(2.2f));
        p.setColor(0xFF262626);
        c.drawLine(left, y, left + w, y, p);

        float frac = duration <= 0 ? 0f : (float) position / duration;
        frac = Math.max(0f, Math.min(1f, frac));
        float x = left + w * frac;
        p.setColor(Ui.ACCENT);
        c.drawLine(left, y, x, y, p);

        p.setColor(0xFFEDEDED);
        c.drawCircle(x, y, dp(3.2f), p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                updateFrom(e.getX());
                if (listener != null) listener.onSeekStart();
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFrom(e.getX());
                if (listener != null) listener.onSeekMove(position);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateFrom(e.getX());
                dragging = false;
                if (listener != null) listener.onSeekEnd(position);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void updateFrom(float x) {
        float left = getPaddingLeft();
        float w = getWidth() - left - getPaddingRight();
        if (w <= 0) return;
        float frac = (x - left) / w;
        position = (int) (Math.max(0f, Math.min(1f, frac)) * duration);
        invalidate();
    }
}
