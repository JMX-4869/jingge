# -*- coding: utf-8 -*-
import io

def rd(p):
    return io.open(p, encoding='utf-8').read()

def wr(p, s):
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- Ui：主题三档 ----------
p = 'src/com/xiaojing/jingge/Ui.java'
s = rd(p)
old = '''    /** 用户手动切过就按他的，没切过就跟系统 */
    static boolean storedDark(Context c) {
        android.content.SharedPreferences sp =
                c.getSharedPreferences("jingge", Context.MODE_PRIVATE);
        return sp.contains("dark") ? sp.getBoolean("dark", true) : systemDark(c);
    }'''
new = '''    /** theme = auto / dark / light；没设过就用老开关，再没有就跟系统 */
    static boolean storedDark(Context c) {
        android.content.SharedPreferences sp =
                c.getSharedPreferences("jingge", Context.MODE_PRIVATE);
        String m = sp.getString("theme", "");
        if ("dark".equals(m)) return true;
        if ("light".equals(m)) return false;
        if ("auto".equals(m)) return systemDark(c);
        return sp.contains("dark") ? sp.getBoolean("dark", true) : systemDark(c);
    }'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ---------- MainActivity ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)

s = s.replace('import android.text.Editable;',
              'import android.app.Dialog;\nimport android.graphics.Color;\n'
              'import android.graphics.drawable.ColorDrawable;\nimport android.text.Editable;')
s = s.replace('import android.view.WindowManager;',
              'import android.view.Window;\nimport android.view.WindowManager;')

s = s.replace('private static final String VER = "0.11";',
              'private static final String VER = "0.12";')

# 字段
old = '''    private LinearLayout tabs, backRow;'''
new = '''    private LinearLayout tabs, backRow;
    private FrameLayout drawerScrim;
    private LinearLayout drawer;
    private int drawerW;
    private boolean drawerOpen;
    private final TextView[] navItems = new TextView[4];'''
assert old in s
s = s.replace(old, new, 1)

# 顶栏：汉堡 + 去掉主题按钮
old = '''        TextView brand = text("鲸歌 " + VER, 13, Ui.MUTED, false);'''
new = '''        Icons menu = new Icons(this).icon(Icons.LIST).color(Ui.MUTED).ratio(0.5f);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(36), dp(36));
        mlp.setMargins(0, 0, dp(6), 0);
        header.addView(menu, mlp);

        TextView brand = text("鲸歌 " + VER, 13, Ui.MUTED, false);'''
assert old in s
s = s.replace(old, new, 1)

old = '''        Icons themeBtn = new Icons(this).icon(Icons.THEME).color(Ui.MUTED).ratio(0.5f);
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTheme();
            }
        });
        header.addView(themeBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));

'''
assert old in s
s = s.replace(old, '', 1)

# 去掉 tabs
old = '''        // 分类：歌曲 / 专辑 / 艺术家 / 文件夹
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        tlp.setMargins(dp(16), dp(2), dp(16), dp(2));
        top.addView(tabs, tlp);
        String[] names = {"歌曲", "专辑", "艺术家", "文件夹"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = text(names[i], 13, Ui.MUTED, false);
            t.setPadding(dp(12), dp(8), dp(12), dp(8));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setMode(idx);
                }
            });
            tabs.addView(t);
            tabViews[i] = t;
        }

'''
assert old in s
s = s.replace(old, '', 1)

# 侧边栏：加在 playerPage 之前
old = '''        playerPage = buildPlayer();'''
new = '''        // 侧边栏
        drawerW = (int) (getResources().getDisplayMetrics().widthPixels * 0.76f);
        drawerScrim = new FrameLayout(this);
        drawerScrim.setBackgroundColor(0x99000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        root.addView(drawerScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Ui.BG);
        drawer.setPadding(0, dp(28), 0, 0);
        FrameLayout.LayoutParams drp = new FrameLayout.LayoutParams(
                drawerW, ViewGroup.LayoutParams.MATCH_PARENT);
        drp.gravity = Gravity.START;
        root.addView(drawer, drp);
        drawer.setTranslationX(-drawerW);

        TextView dtitle = text("鲸歌 " + VER, 15, Ui.TEXT, false);
        dtitle.setPadding(dp(24), dp(8), dp(24), dp(18));
        drawer.addView(dtitle);

        String[] cats = {"歌曲", "专辑", "艺术家", "文件夹"};
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
        }

        View dline = new View(this);
        dline.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp2.setMargins(dp(24), dp(14), dp(24), dp(8));
        drawer.addView(dline, dlp2);

        TextView settingsItem = text("设置", 15, Ui.MUTED, false);
        settingsItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        settingsItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                showSettings();
            }
        });
        drawer.addView(settingsItem);

        TextView aboutItem = text("关于", 15, Ui.MUTED, false);
        aboutItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        aboutItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                showAbout();
            }
        });
        drawer.addView(aboutItem);

        playerPage = buildPlayer();'''
assert old in s
s = s.replace(old, new, 1)

# onCreate 里 updateTabs -> updateNav
s = s.replace('        updateTabs();', '        updateNav();')

# setMode / openGroup / backToTabs 去掉 tabs 可见性
s = s.replace('''        currentGroup = null;
        tabs.setVisibility(View.VISIBLE);
        backRow.setVisibility(View.GONE);
        updateTabs();''', '''        currentGroup = null;
        backRow.setVisibility(View.GONE);
        updateNav();''')

s = s.replace('''        mode = MODE_GROUP;
        tabs.setVisibility(View.GONE);
        backRow.setVisibility(View.VISIBLE);''', '''        mode = MODE_GROUP;
        backRow.setVisibility(View.VISIBLE);''')

s = s.replace('''        currentGroup = null;
        tabs.setVisibility(View.VISIBLE);
        backRow.setVisibility(View.GONE);
        updateTabs();
        search.setText("");''', '''        currentGroup = null;
        backRow.setVisibility(View.GONE);
        updateNav();
        search.setText("");''')

# updateTabs -> updateNav
old = '''    private void updateTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            if (tabViews[i] == null) continue;
            boolean on = mode == i;
            tabViews[i].setTextColor(on ? Ui.TEXT : Ui.MUTED);
            tabViews[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }'''
new = '''    private void updateNav() {
        for (int i = 0; i < navItems.length; i++) {
            if (navItems[i] == null) continue;
            boolean on = mode == i;
            navItems[i].setTextColor(on ? Ui.TEXT : Ui.MUTED);
            navItems[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        drawerScrim.setVisibility(View.VISIBLE);
        drawerScrim.setAlpha(0f);
        drawerScrim.animate().alpha(1f).setDuration(160).start();
        drawer.animate().translationX(0f).setDuration(200).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-drawerW).setDuration(180).start();
        drawerScrim.animate().alpha(0f).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        drawerScrim.setVisibility(View.GONE);
                    }
                }).start();
    }'''
assert old in s
s = s.replace(old, new, 1)

# 返回键：先关抽屉
old = '''    public void onBackPressed() {
        if (playerOpen) {'''
new = '''    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (playerOpen) {'''
assert old in s
s = s.replace(old, new, 1)

# 设置 / 关于 / 对话框工具
methods = io.open('tools/drawer_methods.txt', encoding='utf-8').read()
old = '''    private View buildUi() {'''
assert old in s
s = s.replace(old, methods + old, 1)

wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="11"', 'android:versionCode="12"')
s = s.replace('android:versionName="0.11"', 'android:versionName="0.12"')
wr(p, s)
print('ok')
