# -*- coding: utf-8 -*-
"""0.72: 播放页 ≡ 打开播放队列 / 进播放页收键盘 / 列表页加「当前播放」跳转"""
import io

# ── 1. MusicService 暴露队列内容 ──
p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()
old = '''    public void playAt(int index) {'''
new = '''    public Song queueSongAt(int i) {
        return i >= 0 && i < queue.size() ? queue.get(i) : null;
    }

    public int queueIndex() {
        return queue.index();
    }

    public void playAt(int index) {'''
assert old in s
s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('service ok')

# ── 2. MainActivity ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
t = io.open(p, encoding='utf-8').read()

t = t.replace('private static final String VER = "0.71";', 'private static final String VER = "0.72";', 1)

# 2a. 播放页 ≡ → 队列
old2 = '''        Icons listIcon = new Icons(this).icon(Icons.LIST).color(Ui.DIM).ratio(0.44f);
        listIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePlayer();
            }
        });'''
new2 = '''        Icons listIcon = new Icons(this).icon(Icons.LIST).color(Ui.DIM).ratio(0.44f);
        listIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQueue();
            }
        });'''
assert old2 in t
t = t.replace(old2, new2, 1)

# 2b. 开关播放页时收键盘
old3 = '''    private void openPlayer() {
        if (svc == null || svc.currentSong() == null) return;'''
new3 = '''    /** 进播放页先把键盘收掉，搜索框也别留着焦点 */
    private void hideKeyboard() {
        try {
            if (search != null) {
                search.clearFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
            }
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        } catch (Throwable ignored) {
        }
    }

    private void openPlayer() {
        if (svc == null || svc.currentSong() == null) return;
        hideKeyboard();'''
assert old3 in t
t = t.replace(old3, new3, 1)

# 2c. 队列弹窗 + 跳转当前
old4 = '''    // ───────────────────────── 列表 ─────────────────────────'''
new4 = '''    /** 播放页那个 ≡：把当前播放队列摊开，点哪首放哪首 */
    private void showQueue() {
        if (svc == null || svc.size() <= 0) {
            Toast.makeText(this, "队列是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        final LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        box.addView(wrap);

        TextView title = text("播放队列 · " + svc.size() + " 首", 15, Ui.TEXT, true);
        title.setPadding(0, 0, 0, dp(10));
        wrap.addView(title);

        final android.widget.ScrollView sv = new android.widget.ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        int h = Math.min(dp(420), dp(52) * Math.min(svc.size(), 8));
        wrap.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));

        final int cur = svc.queueIndex();
        for (int i = 0; i < svc.size(); i++) {
            final int idx = i;
            Song sg = svc.queueSongAt(i);
            if (sg == null) continue;
            boolean on = i == cur;
            TextView row = text((i + 1) + ". " + sg.title, 13,
                    on ? Ui.ACCENT : Ui.TEXT, on);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setPadding(dp(4), dp(11), dp(4), dp(11));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    svc.playAt(idx);
                    d.dismiss();
                }
            });
            list.addView(row);
        }

        LinearLayout frow = new LinearLayout(this);
        frow.setGravity(Gravity.END);
        wrap.addView(frow);
        TextView close = text("关闭", 13, Ui.MUTED, false);
        close.setPadding(dp(12), dp(10), dp(12), dp(4));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        frow.addView(close);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
        // 滚到正在放的那首
        if (cur > 0) {
            sv.post(new Runnable() {
                public void run() {
                    sv.scrollTo(0, cur * dp(52) - dp(52) * 2);
                }
            });
        }
    }

    /** 列表里直接滚到正在放的那首 */
    private void jumpToPlaying() {
        if (svc == null || svc.currentSong() == null) {
            Toast.makeText(this, "还没有在放的歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mode != MODE_SONG) {
            setMode(MODE_SONG);
            if (search != null) search.setText("");
            applyQuery("");
        }
        final long id = svc.currentSong().id;
        int hit = -1;
        for (int i = 0; i < display.size(); i++) {
            if (display.get(i).id == id) {
                hit = i;
                break;
            }
        }
        if (hit < 0) {
            Toast.makeText(this, "当前这首歌不在这个列表里", Toast.LENGTH_SHORT).show();
            return;
        }
        final int pos = hit;
        listView.post(new Runnable() {
            public void run() {
                listView.setSelectionFromTop(pos, dp(60));
            }
        });
    }

    // ───────────────────────── 列表 ─────────────────────────'''
assert old4 in t
t = t.replace(old4, new4, 1)

# 2d. 顶栏加「当前播放」图标
old5 = '''        TextView batchBtn = text("补全", 11, Ui.ACCENT, false);'''
new5 = '''        Icons now = new Icons(this).icon(Icons.PLAY).color(Ui.ACCENT).ratio(0.42f);
        now.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpToPlaying();
            }
        });
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(30), dp(30));
        nlp.setMargins(0, 0, dp(4), 0);
        header.addView(now, nlp);

        TextView batchBtn = text("补全", 11, Ui.ACCENT, false);'''
assert old5 in t
t = t.replace(old5, new5, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('activity ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('AndroidManifest.xml',
   [('android:versionCode="71"', 'android:versionCode="72"'),
    ('android:versionName="0.71"', 'android:versionName="0.72"')])
print('version ok')
