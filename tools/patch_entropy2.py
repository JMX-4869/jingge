# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- 主题：按截图取色 ----------
p = 'src/com/xiaojing/jingge/ThemeStore.java'
s = rd(p)
s = s.replace('''        l.add(new Theme("bi:entropy", "熵光", 0xFF05070A, 0xFF080C11,
                0xFF122A33, 0xFFE6FBFF, 0xFF5E8A94, 0xFF35F0D0));''',
'''        l.add(new Theme("bi:entropy", "熵光", 0xFF0A0E11, 0xFF0E1418,
                0xFF1B2429, 0xFFE9EFF2, 0xFF7C8B93, 0xFF2FE0C8));''')
wr(p, s)

# ---------- Ui：熵光下界面是平的，只对话框带一点边光 ----------
p = 'src/com/xiaojing/jingge/Ui.java'
s = rd(p)
s = s.replace('''    static android.graphics.drawable.Drawable dialogSurface(Context c, float radius) {
        if (STYLE == STYLE_GLOW) return new GlowDrawable(PANEL, ACCENT, radius, dp(c, 22), true);
        return roundStroke(PANEL, LINE_SOFT, radius, 1);
    }''',
'''    static android.graphics.drawable.Drawable dialogSurface(Context c, float radius) {
        if (STYLE == STYLE_GLOW) return new GlowDrawable(PANEL, ACCENT, radius, 0f, true);
        return roundStroke(PANEL, LINE_SOFT, radius, 1);
    }''')
s = s.replace('''    static android.graphics.drawable.Drawable screenSurface(Context c) {
        if (STYLE == STYLE_GLOW) return new GlowDrawable(BG, ACCENT, 0f, dp(c, 26), false);
        return null;
    }''',
'''    static android.graphics.drawable.Drawable screenSurface(Context c) {
        return null;
    }''')
wr(p, s)

# ---------- 发光弱一点：参考那套是细边 + 极淡的一圈 ----------
p = 'src/com/xiaojing/jingge/GlowDrawable.java'
s = rd(p)
s = s.replace('p.setColor(alpha(accent, 8 + i * 5));', 'p.setColor(alpha(accent, 3 + i * 4));')
s = s.replace('p.setColor(alpha(accent, 150));', 'p.setColor(alpha(accent, 90));')
wr(p, s)

# ---------- 侧边栏：选中项左边一条竖线 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.20";', 'private static final String VER = "0.21";')
s = s.replace('    private final TextView[] navItems = new TextView[4];',
              '    private final TextView[] navItems = new TextView[4];\n'
              '    private final View[] navBars = new View[4];')

old = '''        String[] cats = {"歌曲", "专辑", "艺术家", "文件夹"};
        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            TextView item = text(cats[i], 15, Ui.MUTED, false);
            item.setPadding(dp(24), dp(14), dp(24), dp(14));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawer();
                    setMode(idx);
                }
            });
            drawer.addView(item);
            navItems[i] = item;
        }'''
new = '''        String[] cats = {"歌曲", "专辑", "艺术家", "文件夹"};
        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            LinearLayout navRow = new LinearLayout(this);
            navRow.setOrientation(LinearLayout.HORIZONTAL);
            navRow.setGravity(Gravity.CENTER_VERTICAL);

            View bar = new View(this);
            bar.setBackgroundColor(Ui.ACCENT);
            bar.setVisibility(View.INVISIBLE);
            navRow.addView(bar, new LinearLayout.LayoutParams(dp(2), dp(18)));

            TextView item = text(cats[i], 15, Ui.MUTED, false);
            item.setPadding(dp(22), dp(13), dp(24), dp(13));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawer();
                    setMode(idx);
                }
            });
            navRow.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            drawer.addView(navRow);
            navItems[i] = item;
            navBars[i] = bar;
        }'''
assert old in s
s = s.replace(old, new, 1)

old = '''            navItems[i].setTextColor(on ? Ui.TEXT : Ui.MUTED);
            navItems[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);'''
new = '''            navItems[i].setTextColor(on ? Ui.TEXT : Ui.MUTED);
            navItems[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (navBars[i] != null) {
                navBars[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            }'''
assert old in s
s = s.replace(old, new, 1)

# 品牌下面加一行小字（参考那边是英文细字）
old = '''        TextView dtitle = text("鲸歌 " + VER, 15, Ui.TEXT, false);
        dtitle.setPadding(dp(24), dp(8), dp(24), dp(18));
        drawer.addView(dtitle);'''
new = '''        TextView dtitle = text("鲸歌 " + VER, 15, Ui.TEXT, false);
        dtitle.setPadding(dp(24), dp(8), dp(24), dp(2));
        drawer.addView(dtitle);

        TextView dsub = text("JINGGE · LOCAL · PLAYER", 9, Ui.DIM, false);
        dsub.setLetterSpacing(0.28f);
        dsub.setPadding(dp(24), 0, dp(24), dp(18));
        drawer.addView(dsub);'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ---------- 版本 ----------
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="20"', 'android:versionCode="21"').replace('android:versionName="0.20"', 'android:versionName="0.21"')
wr(p, s)
print('ok')
