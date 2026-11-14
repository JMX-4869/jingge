# -*- coding: utf-8 -*-
"""0.74: 补全/校名 加自选 —— 选补什么(歌词/封面)、补哪些(全库/当前列表)"""
import io

# ── 1. BatchMatch 支持只做一项 ──
p = 'src/com/xiaojing/jingge/BatchMatch.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('''    private final boolean cancelled;''', '''    private final boolean wantLyric, wantCover;
    private final boolean cancelled;''', 1) if 'wantLyric' not in s else s

old = '''    BatchMatch(Context c, List<Song> list, Listener l) {
        app = c.getApplicationContext();
        songs = list;
        listener = l;'''
new = '''    BatchMatch(Context c, List<Song> list, Listener l) {
        this(c, list, true, true, l);
    }

    BatchMatch(Context c, List<Song> list, boolean wantLyric, boolean wantCover, Listener l) {
        app = c.getApplicationContext();
        songs = list;
        listener = l;
        this.wantLyric = wantLyric;
        this.wantCover = wantCover;'''
assert old in s
s = s.replace(old, new, 1)

old2 = '''    private void one(Song s) {
        boolean needLyric = !LyricStore.has(app, s.id)'''
new2 = '''    private void one(Song s) {
        boolean needLyric = wantLyric && !LyricStore.has(app, s.id)'''
assert old2 in s
s = s.replace(old2, new2, 1)
s = s.replace('''        if (!CoverStore.hasCache(app, s.id) && !CoverStore.hasUser(app, s.id)) {''',
              '''        if (wantCover && !CoverStore.hasCache(app, s.id) && !CoverStore.hasUser(app, s.id)) {''', 1)

# 字段声明补齐（放在 listener 后面即可）
s = s.replace('''    private final Listener listener;''',
              '''    private final Listener listener;
    private final boolean wantLyric;
    private final boolean wantCover;''', 1)
# 构造里那句 this.wantLyric 赋值保留；删掉上面误加的那行
s = s.replace('''    private final boolean wantLyric, wantCover;
''', '', 1) if 'wantLyric, wantCover;' in s else s
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('batchmatch ok')

# ── 2. MainActivity：配置框 ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
t = io.open(p, encoding='utf-8').read()
t = t.replace('private static final String VER = "0.73";', 'private static final String VER = "0.74";', 1)

# startBatch 改成先选
old3 = '''    private void startBatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (batchMatch != null) return;
'''
new3 = '''    /** 勾选用的标签：亮/暗两种状态 */
    private void pickState(TextView t, boolean on) {
        t.setTextColor(on ? Ui.ACCENT : Ui.MUTED);
        t.setBackground(Ui.roundStroke(0, on ? Ui.ACCENT : Ui.LINE, dp(10), dp(1)));
    }

    private void startBatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (batchMatch != null) {
            Toast.makeText(this, "正在补，等它跑完或者先停掉", Toast.LENGTH_SHORT).show();
            return;
        }
        showBatchConfig();
    }

    /** 补全前先问：补什么、补哪些 */
    private void showBatchConfig() {
        final Dialog cfg = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("补全", 15, Ui.TEXT, true));

        TextView tip = text("只补缺的，已有的不动", 11, Ui.MUTED, false);
        tip.setPadding(0, dp(6), 0, dp(12));
        box.addView(tip);

        box.addView(text("补什么", 11, Ui.DIM, false));
        LinearLayout what = new LinearLayout(this);
        what.setOrientation(LinearLayout.HORIZONTAL);
        what.setPadding(0, dp(6), 0, dp(12));
        box.addView(what);

        final boolean[] on = {true, true, true};   // 歌词 / 封面 / 全库
        final TextView cLyric = text("歌词", 13, Ui.ACCENT, false);
        cLyric.setPadding(dp(14), dp(8), dp(14), dp(8));
        pickState(cLyric, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        cLyric.setLayoutParams(lp);
        what.addView(cLyric);

        final TextView cCover = text("封面", 13, Ui.ACCENT, false);
        cCover.setPadding(dp(14), dp(8), dp(14), dp(8));
        pickState(cCover, true);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, dp(8), 0);
        cCover.setLayoutParams(lp2);
        what.addView(cCover);

        cLyric.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                on[0] = !on[0];
                pickState(cLyric, on[0]);
            }
        });
        cCover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                on[1] = !on[1];
                pickState(cCover, on[1]);
            }
        });

        box.addView(text("补哪些", 11, Ui.DIM, false));
        LinearLayout scope = new LinearLayout(this);
        scope.setOrientation(LinearLayout.HORIZONTAL);
        scope.setPadding(0, dp(6), 0, dp(14));
        box.addView(scope);

        final TextView sAll = text("全库 " + all.size() + " 首", 13, Ui.ACCENT, false);
        sAll.setPadding(dp(14), dp(8), dp(14), dp(8));
        pickState(sAll, true);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp3.setMargins(0, 0, dp(8), 0);
        sAll.setLayoutParams(lp3);
        scope.addView(sAll);

        final TextView sNow = text("当前列表 " + display.size() + " 首", 13, Ui.MUTED, false);
        sNow.setPadding(dp(14), dp(8), dp(14), dp(8));
        pickState(sNow, false);
        scope.addView(sNow);

        sAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                on[2] = true;
                pickState(sAll, true);
                pickState(sNow, false);
            }
        });
        sNow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                on[2] = false;
                pickState(sAll, false);
                pickState(sNow, true);
            }
        });

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cfg.dismiss();
            }
        });
        row.addView(cancel);

        TextView go = text("开始", 13, Ui.ACCENT, true);
        go.setPadding(dp(12), dp(8), dp(12), dp(8));
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cfg.dismiss();
                runBatch(on[0], on[1], on[2]);
            }
        });
        row.addView(go);

        cfg.setContentView(box);
        dialogWindow(cfg);
        cfg.show();
    }

    private void runBatch(boolean wantLyric, boolean wantCover, boolean wholeLibrary) {
        if (!wantLyric && !wantCover) {
            Toast.makeText(this, "两项都没勾，什么也不做", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Song> target;
        if (wholeLibrary) {
            target = new ArrayList<Song>(all);
        } else {
            target = new ArrayList<Song>(display);
            if (target.isEmpty()) target = new ArrayList<Song>(all);
        }
        if (target.isEmpty()) {
            Toast.makeText(this, "这个范围里没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
'''
assert old3 in t
t = t.replace(old3, new3, 1)

# 原 startBatch 里 new BatchMatch(... all ...) 换成 target + 开关
t = t.replace('''        batchMatch = new BatchMatch(this, new ArrayList<Song>(all), new BatchMatch.Listener() {''',
              '''        final String what = (wantLyric && wantCover) ? "歌词和封面"
                : wantLyric ? "歌词" : "封面";
        batchMatch = new BatchMatch(this, target, wantLyric, wantCover, new BatchMatch.Listener() {''', 1)
t = t.replace('''                        (cancelled ? "停下了：" : "补全完了：")
                                + "歌词 " + lyrics + " 首 · 封面 " + covers + " 张",''',
              '''                        (cancelled ? "停下了（" : "补全完了（")
                                + what + "）：歌词 " + lyrics + " 首 · 封面 " + covers + " 张",''', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('activity part1 ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a[:60]
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('AndroidManifest.xml',
   [('android:versionCode="73"', 'android:versionCode="74"'),
    ('android:versionName="0.73"', 'android:versionName="0.74"')])
print('version ok')
