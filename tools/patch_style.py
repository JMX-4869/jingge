# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- Ui：风格开关 ----------
p = 'src/com/xiaojing/jingge/Ui.java'
s = rd(p)
s = s.replace('''    private Ui() {
    }''', '''    /** 0 = 极简（现在的样子）；1 = 熵光（暗底细网格 + 发光描边） */
    static final int STYLE_MINIMAL = 0;
    static final int STYLE_GLOW = 1;
    static int STYLE = STYLE_MINIMAL;

    private static final String UI_PREFS = "jingge_ui";

    static int style(Context c) {
        return c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getInt("ui_style", 0);
    }

    static void setStyle(Context c, int s) {
        c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()
                .putInt("ui_style", s).apply();
    }

    private Ui() {
    }''')

s = s.replace('''        LINE_SOFT = blend(t.line, t.bg, 0.45f);
    }''', '''        LINE_SOFT = blend(t.line, t.bg, 0.45f);
        STYLE = style(c);
    }

    /** 对话框底层：极简是细描边，熵光是发光 + 网格。 */
    static android.graphics.drawable.Drawable dialogSurface(Context c, float radius) {
        if (STYLE == STYLE_GLOW) return new GlowDrawable(PANEL, ACCENT, radius, dp(c, 22), true);
        return roundStroke(PANEL, LINE_SOFT, radius, 1);
    }

    /** 整个界面的底。 */
    static android.graphics.drawable.Drawable screenSurface(Context c) {
        if (STYLE == STYLE_GLOW) return new GlowDrawable(BG, ACCENT, 0f, dp(c, 26), false);
        return null;
    }''')
wr(p, s)

# ---------- 主题：熵光 ----------
p = 'src/com/xiaojing/jingge/ThemeStore.java'
s = rd(p)
old = '''        l.add(new Theme("bi:paper"'''
new = '''        l.add(new Theme("bi:entropy", "熵光", 0xFF05070A, 0xFF080C11,
                0xFF122A33, 0xFFE6FBFF, 0xFF5E8A94, 0xFF35F0D0));
        l.add(new Theme("bi:paper"'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ---------- MainActivity ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.19";', 'private static final String VER = "0.20";')

# 对话框底
s = s.replace('''        box.setBackgroundColor(Ui.PANEL);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        return box;''', '''        box.setBackground(Ui.dialogSurface(this, dp(16)));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        return box;''')

# 设置页加「风格」切换
old = '''        TextView tl = text("主题 · 点一下就用", 11, Ui.DIM, false);
        tl.setPadding(0, dp(14), 0, dp(4));
        box.addView(tl);'''
new = '''        TextView sl = text("风格", 11, Ui.DIM, false);
        sl.setPadding(0, dp(14), 0, dp(6));
        box.addView(sl);

        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(srow);
        srow.addView(styleChip(d, "极简", Ui.STYLE_MINIMAL, Ui.style(this)));
        srow.addView(styleChip(d, "熵光矩阵", Ui.STYLE_GLOW, Ui.style(this)));

        TextView tl = text("主题 · 点一下就用", 11, Ui.DIM, false);
        tl.setPadding(0, dp(14), 0, dp(4));
        box.addView(tl);'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private View themeRow(final Dialog d, final Theme t) {'''
new = '''    private View styleChip(final Dialog d, final String name, final int style, int cur) {
        final boolean on = style == cur;
        TextView t = text(name, 13, on ? Ui.ACCENT : Ui.MUTED, on);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        t.setBackground(Ui.roundStroke(0, on ? Ui.ACCENT : Ui.LINE, dp(10), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.setStyle(MainActivity.this, style);
                ThemeStore.setActive(MainActivity.this, style == Ui.STYLE_GLOW
                        ? "bi:entropy"
                        : (Ui.systemDark(MainActivity.this) ? "bi:black" : "bi:paper"));
                d.dismiss();
                recreate();
            }
        });
        return t;
    }

    private View themeRow(final Dialog d, final Theme t) {'''
assert old in s
s = s.replace(old, new, 1)

# 根布局背景
old = '''        root.setBackgroundColor(Ui.BG);'''
new = '''        android.graphics.drawable.Drawable screen = Ui.screenSurface(this);
        if (screen != null) root.setBackground(screen);
        else root.setBackgroundColor(Ui.BG);'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ---------- 编辑页对话框 ----------
p = 'src/com/xiaojing/jingge/LyricEditorActivity.java'
s = rd(p)
s = s.replace('''        box.setBackgroundColor(Ui.PANEL);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));''', '''        box.setBackground(Ui.dialogSurface(this, dp(16)));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));''')
wr(p, s)

# ---------- 版本 ----------
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="19"', 'android:versionCode="20"').replace('android:versionName="0.19"', 'android:versionName="0.20"')
wr(p, s)
print('ok')
