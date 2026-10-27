package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** 颜色、密度、背景。极简：只有灰阶 + 一个"当前"色。 */
final class Ui {
    static final int BG = 0xFF0A0A0A;
    static final int PANEL = 0xFF0F0F0F;
    static final int PANEL_UP = 0xFF141414;
    static final int LINE = 0xFF1C1C1C;
    static final int TEXT = 0xFFEDEDED;
    static final int MUTED = 0xFF6E6E6E;
    static final int DIM = 0xFF4A4A4A;
    /** 全屏唯一一处彩色：进度的"现在"，和正在播放的那一行。 */
    static final int ACCENT = 0xFF58E0C8;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable round(int fill, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable roundStroke(int fill, int stroke, float radiusPx, int w) {
        GradientDrawable d = round(fill, radiusPx);
        d.setStroke(w, stroke);
        return d;
    }

    static GradientDrawable oval(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }
}
