# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- Lyric：只有一行时退化成纯文本（恢复旧行为） ----------
p = 'src/com/xiaojing/jingge/Lyric.java'
s = rd(p)
old = '''        if (transLrc != null && transLrc.trim().length() > 0) {'''
new = '''        if (clean.size() < 2) {
            List<String> plain = new ArrayList<String>();
            for (String raw : lrc.replace("\\r", "").split("\\n")) {
                String line = STAMP.matcher(raw).replaceAll("").trim();
                if (line.length() > 0) plain.add(line);
            }
            if (plain.isEmpty()) return null;
            return new Lyric(new ArrayList<Line>(), plain, false, false);
        }

        if (transLrc != null && transLrc.trim().length() > 0) {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ---------- LyricView：开关 ----------
p = 'src/com/xiaojing/jingge/LyricView.java'
s = rd(p)
s = s.replace('''    private int lastPos;
    private OnSeek onSeek;''', '''    private int lastPos;
    private OnSeek onSeek;
    private boolean showTrans = true;''')
s = s.replace('''    public void setOnSeek(OnSeek s) {
        onSeek = s;
    }''', '''    public void setOnSeek(OnSeek s) {
        onSeek = s;
    }

    /** 双语歌词开关；关了只显示原文。 */
    public void setShowTrans(boolean on) {
        showTrans = on;
    }

    public boolean showTrans() {
        return showTrans;
    }''')
s = s.replace('        for (Lyric.Line line : l.lines) addRow(line.text, line.trans, line.timeMs);',
              '        for (Lyric.Line line : l.lines)\n'
              '            addRow(line.text, showTrans ? line.trans : null, line.timeMs);')
wr(p, s)

# ---------- MainActivity ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.15";',
              'private static final String VER = "0.16";')

s = s.replace('''    private GridView grid;''', '''    private GridView grid;
    private TextView transBtn;
    private boolean showTrans = true;
    private Lyric curLyric;''')

# onCreate 读设置（在 Ui.apply 之后）
s = s.replace('        Ui.apply(this);', '        Ui.apply(this);\n'
              '        showTrans = Ai.prefs(this).getBoolean("lyric_trans", true);')

# 播放页顶栏右侧加「译」
old = '''        head.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        vinyl = new VinylView(this);'''
new = '''        head.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        transBtn = text("译", 12, showTrans ? Ui.ACCENT : Ui.DIM, true);
        transBtn.setGravity(Gravity.CENTER);
        transBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        transBtn.setBackground(Ui.roundStroke(0, showTrans ? Ui.ACCENT : Ui.LINE, dp(8), dp(1)));
        transBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTrans();
            }
        });
        head.addView(transBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        vinyl = new VinylView(this);'''
assert old in s
s = s.replace(old, new, 1)

# 歌词挂到 lyricView 时记下来
s = s.replace('            lyricView.setLyric(l);', '            curLyric = l;\n            lyricView.setLyric(l);')

# 开关方法
old = '''    private View buildUi() {'''
new = '''    private void toggleTrans() {
        showTrans = !showTrans;
        Ai.prefs(this).edit().putBoolean("lyric_trans", showTrans).apply();
        if (transBtn != null) {
            transBtn.setTextColor(showTrans ? Ui.ACCENT : Ui.DIM);
            transBtn.setBackground(Ui.roundStroke(0,
                    showTrans ? Ui.ACCENT : Ui.LINE, dp(8), dp(1)));
        }
        if (lyricView != null) {
            lyricView.setShowTrans(showTrans);
            if (curLyric != null) lyricView.setLyric(curLyric);
        }
    }

    private View buildUi() {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# manifest
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="15"', 'android:versionCode="16"')
s = s.replace('android:versionName="0.15"', 'android:versionName="0.16"')
wr(p, s)
print('ok')
