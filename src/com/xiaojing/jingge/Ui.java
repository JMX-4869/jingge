package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** 颜色、密度、背景。整套配色由 ThemeStore 决定。 */
final class Ui {
    static int BG = 0xFF0A0A0A;
    static int PANEL = 0xFF0F0F0F;
    static int PANEL_UP = 0xFF141414;
    static int LINE = 0xFF1C1C1C;
    static int TEXT = 0xFFEDEDED;
    static int MUTED = 0xFF6E6E6E;
    static int DIM = 0xFF4A4A4A;
    /** 全屏唯一一处彩色：进度的"现在"，和正在播放的那一行。 */
    static int ACCENT = 0xFF58E0C8;
    /** 列表按下的那一层。 */
    static int RIPPLE = 0x14FFFFFF;
    /** 行与行之间的头发丝线 */
    static int LINE_SOFT = 0x22FFFFFF;

    /** 0 = 极简（现在的样子）；1 = 熵光（暗底细网格 + 发光描边） */
    static final int STYLE_MINIMAL = 0;
    static final int STYLE_GLOW = 1;
    static int STYLE = STYLE_MINIMAL;

    /** 动效：0 关，1 克制，2 华丽 */
    static int FX = 2;

    private static final String UI_PREFS = "jingge_ui";

    static int style(Context c) {
        return c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getInt("ui_style", 0);
    }

    static void setFx(Context c, int v) {
        c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()
                .putInt("ui_fx", v).apply();
    }

    static void setStyle(Context c, int s) {
        c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()
                .putInt("ui_style", s).apply();
    }

    private Ui() {
    }

    static void apply(Context c) {
        Theme t = ThemeStore.active(c);
        BG = t.bg;
        PANEL = blend(t.bg, t.text, 0.06f);
        PANEL_UP = blend(t.bg, t.text, 0.10f);
        LINE = t.line;
        TEXT = t.text;
        MUTED = t.muted;
        DIM = blend(t.muted, t.bg, 0.45f);
        ACCENT = t.accent;
        RIPPLE = (t.text & 0x00FFFFFF) | 0x14000000;
        LINE_SOFT = blend(t.line, t.bg, 0.45f);
        STYLE = style(c);
        FX = c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getInt("ui_fx", 2);
    }

    /** 对话框底层：极简是细描边，熵光是发光 + 网格。 */
    static android.graphics.drawable.Drawable dialogSurface(Context c, float radius) {
        if (STYLE == STYLE_GLOW) {
            return new GlowDrawable(PANEL, ACCENT, radius, 0f, FX > 0);
        }
        return roundStroke(PANEL, LINE_SOFT, radius, 1);
    }

    /** 整个界面的底。 */
    static android.graphics.drawable.Drawable screenSurface(Context c) {
        return null;
    }

    static boolean isDark() {
        return luminance(BG) < 0.5f;
    }

    static float luminance(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF)
                + 0.114f * (c & 0xFF)) / 255f;
    }

    static int blend(int a, int b, float f) {
        if (f < 0f) f = 0f;
        if (f > 1f) f = 1f;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * f);
        int g = (int) (ag + (bg - ag) * f);
        int bl = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 系统是不是深色 */
    static boolean systemDark(Context c) {
        return (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
