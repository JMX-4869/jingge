# -*- coding: utf-8 -*-
import io, sys

def rd(p):
    return io.open(p, encoding='utf-8').read()

def wr(p, s):
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ── Icons：加 THEME ──────────────────────────────────────────────
p = 'src/com/xiaojing/jingge/Icons.java'
s = rd(p)
s = s.replace("CLOSE = 11, EQ = 12, NOTE = 13, LIST = 14, CHEVRON_LEFT = 15, FOLDER = 16;",
              "CLOSE = 11, EQ = 12, NOTE = 13, LIST = 14, CHEVRON_LEFT = 15, FOLDER = 16,\n            THEME = 17;")
old = """            case FOLDER:"""
new = """            case THEME:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.7f);
                c.drawCircle(12, 12, 7.4f, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.addArc(rect.set(4.6f, 4.6f, 19.4f, 19.4f), -90, 180);
                path.close();
                c.drawPath(path, p);
                break;
            case FOLDER:"""
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ── VinylView：占位颜色跟随主题 ─────────────────────────────────
p = 'src/com/xiaojing/jingge/VinylView.java'
s = rd(p)
s = s.replace("p.setColor(0xFF14181F);", "p.setColor(Ui.PANEL_UP);")
s = s.replace("p.setColor(0x59FFFFFF);", "p.setColor((Ui.TEXT & 0x00FFFFFF) | 0x59000000);")
wr(p, s)

# ── LyricEditorActivity：应用主题 ───────────────────────────────
p = 'src/com/xiaojing/jingge/LyricEditorActivity.java'
s = rd(p)
old = """        super.onCreate(b);
        try {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
        } catch (Throwable ignored) {
        }"""
new = """        super.onCreate(b);
        Ui.apply(getSharedPreferences("jingge", MODE_PRIVATE).getBoolean("dark", true));
        try {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
            if (!Ui.dark) {
                int f = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    f |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                getWindow().getDecorView().setSystemUiVisibility(f);
            }
        } catch (Throwable ignored) {
        }"""
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ── MainActivity ────────────────────────────────────────────────
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)

# imports
s = s.replace("import android.widget.ListView;\nimport android.widget.TextView;",
              "import android.widget.ListView;\nimport android.widget.RelativeLayout;\nimport android.widget.TextView;")

# onCreate：主题 + 去掉 setLayout
old = """        try {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
        } catch (Throwable ignored) {
        }
        restoreId = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("lastId", -1);
        try {
            getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        } catch (Throwable ignored) {
        }"""
new = """        Ui.apply(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("dark", true));
        try {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
            if (!Ui.dark) {
                int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                getWindow().getDecorView().setSystemUiVisibility(f);
            }
        } catch (Throwable ignored) {
        }
        restoreId = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("lastId", -1);"""
assert old in s
s = s.replace(old, new, 1)

# 布局骨架：LinearLayout + weight → RelativeLayout（不用 weight）
old = """        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        plp.gravity = Gravity.TOP | Gravity.START;
        root.addView(page, plp);"""
new = """        final RelativeLayout page = new RelativeLayout(this);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部整块固定从 0 开始，列表用相对规则填中间，不靠 weight
        final LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setId(View.generateViewId());
        RelativeLayout.LayoutParams topLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        page.addView(top, topLp);"""
assert old in s
s = s.replace(old, new, 1)

# header 等挂到 top
s = s.replace("page.addView(header, new LinearLayout.LayoutParams(",
              "top.addView(header, new LinearLayout.LayoutParams(")
s = s.replace("page.addView(nowBox, new LinearLayout.LayoutParams(",
              "top.addView(nowBox, new LinearLayout.LayoutParams(")
s = s.replace("page.addView(searchWrap, swp);", "top.addView(searchWrap, swp);")
s = s.replace("page.addView(tabs, tlp);", "top.addView(tabs, tlp);")
s = s.replace("page.addView(backRow, new LinearLayout.LayoutParams(",
              "top.addView(backRow, new LinearLayout.LayoutParams(")

# listBox + miniBar 重排
old = """        // 列表 + 空状态
        FrameLayout listBox = new FrameLayout(this);
        page.addView(listBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));"""
new = """        // 迷你条先建，列表要"在它上面"
        miniBar = buildMini();
        miniBar.setId(View.generateViewId());
        miniBar.setMinimumHeight(dp(60));
        RelativeLayout.LayoutParams mbLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        page.addView(miniBar, mbLp);

        // 列表 + 空状态
        FrameLayout listBox = new FrameLayout(this);
        RelativeLayout.LayoutParams lbLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lbLp.addRule(RelativeLayout.BELOW, top.getId());
        lbLp.addRule(RelativeLayout.ABOVE, miniBar.getId());
        page.addView(listBox, lbLp);"""
assert old in s
s = s.replace(old, new, 1)

# 删掉旧的 miniBar 添加
old = """        miniBar = buildMini();
        miniBar.setMinimumHeight(dp(60));
        page.addView(miniBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playerPage = buildPlayer();"""
new = """        playerPage = buildPlayer();"""
assert old in s
s = s.replace(old, new, 1)

# selector 跟随主题
s = s.replace("listView.setSelector(new ColorDrawable(0x14FFFFFF));",
              "listView.setSelector(new ColorDrawable(Ui.RIPPLE));")

# 封面占位颜色跟随主题
s = s.replace("""                p.setColor(0xFF14181F);
                c.drawRoundRect(r, radius, radius, p);
                int m = Math.min(w, h);
                // 没有封面时用歌名首字 + 一颗青点，这是鲸歌自己的占位
                if (placeholder.length() > 0) {
                    p.setColor(0x59FFFFFF);""",
"""                p.setColor(Ui.PANEL_UP);
                c.drawRoundRect(r, radius, radius, p);
                int m = Math.min(w, h);
                // 没有封面时用歌名首字 + 一颗青点，这是鲸歌自己的占位
                if (placeholder.length() > 0) {
                    p.setColor((Ui.TEXT & 0x00FFFFFF) | 0x59000000);""")

# 标题栏加主题切换
old = """        header.addView(rescan);"""
new = """        header.addView(rescan);

        Icons themeBtn = new Icons(this).icon(Icons.THEME).color(Ui.MUTED).ratio(0.5f);
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTheme();
            }
        });
        header.addView(themeBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));"""
assert old in s
s = s.replace(old, new, 1)

# toggleTheme
old = """    private View buildUi() {"""
new = """    private void toggleTheme() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("dark", !Ui.dark).apply();
        recreate();
    }

    private View buildUi() {"""
assert old in s
s = s.replace(old, new, 1)

wr(p, s)
print('patched')
