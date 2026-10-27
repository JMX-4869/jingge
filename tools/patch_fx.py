# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- Ui：动效档位 ----------
p = 'src/com/xiaojing/jingge/Ui.java'
s = rd(p)
if 'static int FX' not in s:
    s = s.replace('''    static int STYLE = STYLE_MINIMAL;''',
'''    static int STYLE = STYLE_MINIMAL;

    /** 动效：0 关，1 克制，2 华丽 */
    static int FX = 2;''')
    s = s.replace('''        STYLE = style(c);''',
'''        STYLE = style(c);
        FX = c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getInt("ui_fx", 2);''')
    s = s.replace('''    static void setStyle(Context c, int s) {''',
'''    static void setFx(Context c, int v) {
        c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()
                .putInt("ui_fx", v).apply();
    }

    static void setStyle(Context c, int s) {''')
    s = s.replace('''    static android.graphics.drawable.Drawable dialogSurface(Context c, float radius) {
        if (STYLE == STYLE_GLOW) return new GlowDrawable(PANEL, ACCENT, radius, 0f, true);
        return roundStroke(PANEL, LINE_SOFT, radius, 1);
    }''',
'''    static android.graphics.drawable.Drawable dialogSurface(Context c, float radius) {
        if (STYLE == STYLE_GLOW) {
            return new GlowDrawable(PANEL, ACCENT, radius, 0f, FX > 0);
        }
        return roundStroke(PANEL, LINE_SOFT, radius, 1);
    }''')
    wr(p, s)

# ---------- StarfieldView：按档位减量 ----------
p = 'src/com/xiaojing/jingge/StarfieldView.java'
s = rd(p)
if 'Ui.FX' not in s:
    s = s.replace('''        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now''', '''        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || Ui.FX == 0) return;

        long now''')
    s = s.replace('''        p.setColor(Ui.ACCENT);
        for (int i = 0; i < N; i++) {''', '''        p.setColor(Ui.ACCENT);
        int limit = Ui.FX >= 2 ? N : FAR + MID;
        float dim = Ui.FX >= 2 ? 1f : 0.55f;
        for (int i = 0; i < limit; i++) {''')
    s = s.replace('''            int a = (int) (al[i] * tw * 255);''',
                  '''            int a = (int) (al[i] * tw * dim * 255);''')
    s = s.replace('''        if (now > nextMeteor) {''',
                  '''        if (Ui.FX < 2) return;
        if (now > nextMeteor) {''')
    wr(p, s)

# ---------- MainActivity：播放页恢复不透明 + 自己带一层星 + 动效设置 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.29";', 'private static final String VER = "0.30";')
s = s.replace('''        if (Ui.STYLE != Ui.STYLE_GLOW) p.setBackgroundColor(Ui.BG);''',
'''        p.setBackgroundColor(Ui.BG);''')

# 播放页包一层：星在底下，内容在上面
old = '''        playerPage = buildPlayer();
        root.addView(playerPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));'''
new = '''        View playerContent = buildPlayer();
        if (Ui.STYLE == Ui.STYLE_GLOW && Ui.FX > 0) {
            FrameLayout wrap = new FrameLayout(this);
            wrap.addView(new StarfieldView(this), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            wrap.addView(playerContent, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            playerPage = wrap;
        } else {
            playerPage = playerContent;
        }
        root.addView(playerPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));'''
assert old in s
s = s.replace(old, new, 1)

# 设置里加动效三档
old = '''        srow.addView(styleChip(d, "群星", Ui.STYLE_GLOW, Ui.style(this)));'''
new = '''        srow.addView(styleChip(d, "群星", Ui.STYLE_GLOW, Ui.style(this)));

        TextView fl = text("动效", 11, Ui.DIM, false);
        fl.setPadding(0, dp(12), 0, dp(6));
        box.addView(fl);

        LinearLayout frow = new LinearLayout(this);
        frow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(frow);
        frow.addView(fxChip(d, "华丽", 2));
        frow.addView(fxChip(d, "克制", 1));
        frow.addView(fxChip(d, "关", 0));'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private View styleChip(final Dialog d, final String name, final int style, int cur) {'''
new = '''    private View fxChip(final Dialog d, final String name, final int level) {
        final boolean on = Ui.FX == level;
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
                Ui.setFx(MainActivity.this, level);
                d.dismiss();
                recreate();
            }
        });
        return t;
    }

    private View styleChip(final Dialog d, final String name, final int style, int cur) {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="29"', 'android:versionCode="30"').replace('android:versionName="0.29"', 'android:versionName="0.30"')
wr(p, s)
print('ok')
