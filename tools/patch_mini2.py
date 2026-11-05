# -*- coding: utf-8 -*-
"""0.73: 歌单页也带底部播放条"""
import io

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('private static final String VER = "0.72";', 'private static final String VER = "0.73";', 1)

# 1) 字段
old = '''    private CoverView miniCover;'''
new = '''    private CoverView miniCover;
    private View miniBar2;
    private CoverView miniCover2;
    private TextView miniTitle2, miniArtist2;
    private Icons miniPlay2;'''
assert old in s
s = s.replace(old, new, 1)

# 2) buildMini 支持第二份
old2 = '''    private View buildMini() {
        FrameLayout wrap = new FrameLayout(this);'''
new2 = '''    private View buildMini() {
        return buildMini(false);
    }

    private View buildMini(final boolean second) {
        FrameLayout wrap = new FrameLayout(this);
        if (second) miniBar2 = wrap;'''
assert old2 in s
s = s.replace(old2, new2, 1)

for a, b in [
    ('        miniCover = new CoverView(this);\n        bar.addView(miniCover,',
     '        CoverView cv = new CoverView(this);\n'
     '        if (second) miniCover2 = cv; else miniCover = cv;\n        bar.addView(cv,'),
    ('        miniTitle = text("", 13, Ui.TEXT, false);\n        miniTitle.setSingleLine(true);\n        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);\n        texts.addView(miniTitle);',
     '        TextView tt = text("", 13, Ui.TEXT, false);\n        tt.setSingleLine(true);\n'
     '        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);\n'
     '        if (second) miniTitle2 = tt; else miniTitle = tt;\n        texts.addView(tt);'),
    ('        miniArtist = text("", 11, Ui.MUTED, false);\n        miniArtist.setSingleLine(true);\n        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);\n        miniArtist.setPadding(0, dp(2), 0, 0);\n        texts.addView(miniArtist);',
     '        TextView ar = text("", 11, Ui.MUTED, false);\n        ar.setSingleLine(true);\n'
     '        ar.setEllipsize(android.text.TextUtils.TruncateAt.END);\n'
     '        ar.setPadding(0, dp(2), 0, 0);\n'
     '        if (second) miniArtist2 = ar; else miniArtist = ar;\n        texts.addView(ar);'),
    ('        miniPlay = new Icons(this).icon(Icons.PLAY).color(Ui.TEXT).ratio(0.46f);\n        miniPlay.setOnClickListener(',
     '        final Icons mp = new Icons(this).icon(Icons.PLAY).color(Ui.TEXT).ratio(0.46f);\n'
     '        if (second) miniPlay2 = mp; else miniPlay = mp;\n        mp.setOnClickListener('),
    ('        bar.addView(miniPlay, new LinearLayout.LayoutParams(dp(44), dp(44)));',
     '        bar.addView(mp, new LinearLayout.LayoutParams(dp(44), dp(44)));'),
]:
    assert a in s, a[:50]
    s = s.replace(a, b, 1)

# 3) 歌单页底部挂上
old3 = '''        page.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }'''
new3 = '''        page.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View mb = buildMini(true);
        mb.setVisibility(View.GONE);
        page.addView(mb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return page;
    }'''
assert old3 in s
s = s.replace(old3, new3, 1)

# 4) syncNow 同步第二份
old4 = '''        miniBar.setVisibility(has ? View.VISIBLE : View.GONE);
        nowBox.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            miniTitle.setText(cur.title);
            miniArtist.setText(cur.subtitle());
            nowTitle.setText(cur.title);
            miniCover.setPlaceholder(cur.title);
            vinyl.setPlaceholder(cur.title);
        }
        miniPlay.icon(playing ? Icons.PAUSE : Icons.PLAY);'''
new4 = '''        miniBar.setVisibility(has ? View.VISIBLE : View.GONE);
        if (miniBar2 != null) miniBar2.setVisibility(has ? View.VISIBLE : View.GONE);
        nowBox.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            miniTitle.setText(cur.title);
            miniArtist.setText(cur.subtitle());
            nowTitle.setText(cur.title);
            miniCover.setPlaceholder(cur.title);
            vinyl.setPlaceholder(cur.title);
            if (miniTitle2 != null) miniTitle2.setText(cur.title);
            if (miniArtist2 != null) miniArtist2.setText(cur.subtitle());
            if (miniCover2 != null) miniCover2.setPlaceholder(cur.title);
        }
        miniPlay.icon(playing ? Icons.PAUSE : Icons.PLAY);
        if (miniPlay2 != null) miniPlay2.icon(playing ? Icons.PAUSE : Icons.PLAY);'''
assert old4 in s
s = s.replace(old4, new4, 1)

# 5) 封面也同步
s = s.replace('''            coverId = -1;
            vinyl.setCover(null);
            miniCover.setBmp(null);
            return;''','''            coverId = -1;
            vinyl.setCover(null);
            miniCover.setBmp(null);
            if (miniCover2 != null) miniCover2.setBmp(null);
            return;''', 1)
s = s.replace('''        coverId = s.id;
        vinyl.setCover(null);
        miniCover.setBmp(null);''','''        coverId = s.id;
        vinyl.setCover(null);
        miniCover.setBmp(null);
        if (miniCover2 != null) miniCover2.setBmp(null);''', 1)
s = s.replace('''                        vinyl.setCover(local);
                        miniCover.setBmp(local);''','''                        vinyl.setCover(local);
                        miniCover.setBmp(local);
                        if (miniCover2 != null) miniCover2.setBmp(local);''', 1)
s = s.replace('''                vinyl.setCover(bm);
                miniCover.setBmp(bm);''','''                vinyl.setCover(bm);
                miniCover.setBmp(bm);
                if (miniCover2 != null) miniCover2.setBmp(bm);''', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('activity ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('AndroidManifest.xml',
   [('android:versionCode="72"', 'android:versionCode="73"'),
    ('android:versionName="0.72"', 'android:versionName="0.73"')])
print('version ok')
