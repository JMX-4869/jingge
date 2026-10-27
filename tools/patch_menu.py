# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- 扫描时跳过藏起来的 ----------
p = 'src/com/xiaojing/jingge/MusicRepo.java'
s = rd(p)
if 'Hidden.has' not in s:
    old = '''                if (looksLikeRecording(title) || looksLikeRecording(file)) continue;'''
    new = '''                if (looksLikeRecording(title) || looksLikeRecording(file)) continue;
                if (Hidden.has(ctx, id)) continue;'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)

# ---------- 长按菜单重做 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
if 'private void showSongMenu(final Song s)' in s and 'menuRow(' not in s:
    s = s.replace('private static final String VER = "0.30";',
                  'private static final String VER = "0.31";')

    start = s.index('    private void showSongMenu(final Song s) {')
    end = s.index('    private void resetCoverIfCurrent(long id) {')
    new_menu = '''    private int danger() {
        return Ui.isDark() ? 0xFFFF6B6B : 0xFFC62828;
    }

    private View menuRow(String label, int color, boolean bold, final Runnable act,
                         final Dialog d) {
        TextView t = text(label, 14, color, bold);
        t.setPadding(dp(20), dp(13), dp(20), dp(13));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                act.run();
            }
        });
        return t;
    }

    private View hair() {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE_SOFT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(20), dp(6), dp(20), dp(6));
        v.setLayoutParams(lp);
        return v;
    }

    private void showSongMenu(final Song s) {
        if (s == null) return;
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.setPadding(0, dp(16), 0, dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), 0, dp(20), dp(12));
        TextView title = text(s.title == null ? "未命名" : s.title, 16, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(title);
        TextView sub = text(s.subtitle(), 12, Ui.MUTED, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(5), 0, 0);
        head.addView(sub);
        box.addView(head);

        box.addView(hair());

        box.addView(menuRow("编辑歌曲信息", Ui.TEXT, false, new Runnable() {
            public void run() {
                editMeta(s);
            }
        }, d));
        box.addView(menuRow("恢复原来的名字", Ui.MUTED, false, new Runnable() {
            public void run() {
                MetaStore.remove(MainActivity.this, s.id);
                scan();
            }
        }, d));
        box.addView(menuRow("换封面", Ui.TEXT, false, new Runnable() {
            public void run() {
                pickCover(s);
            }
        }, d));
        box.addView(menuRow("清除自定义封面", Ui.MUTED, false, new Runnable() {
            public void run() {
                CoverStore.deleteUser(MainActivity.this, s.id);
                resetCoverIfCurrent(s.id);
            }
        }, d));
        box.addView(menuRow("重新找封面", Ui.MUTED, false, new Runnable() {
            public void run() {
                CoverStore.deleteCache(MainActivity.this, s.id);
                resetCoverIfCurrent(s.id);
            }
        }, d));
        box.addView(menuRow("编辑歌词", Ui.TEXT, false, new Runnable() {
            public void run() {
                editLyric(s);
            }
        }, d));

        box.addView(hair());

        box.addView(menuRow("从播放器移除", Ui.MUTED, false, new Runnable() {
            public void run() {
                Hidden.hide(MainActivity.this, s.id);
                Toast.makeText(MainActivity.this, "已从播放器移除，文件还在",
                        Toast.LENGTH_SHORT).show();
                scan();
            }
        }, d));
        box.addView(menuRow("删除这首歌和文件", danger(), true, new Runnable() {
            public void run() {
                confirmDelete(s);
            }
        }, d));

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void confirmDelete(final Song s) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("删除这个文件？", 15, danger(), true));
        TextView msg = text(s.title + "\\n会从手机存储里真的删掉，删了找不回来。",
                12, Ui.MUTED, false);
        msg.setPadding(0, dp(8), 0, dp(14));
        box.addView(msg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("删除", 13, danger(), true);
        ok.setPadding(dp(12), dp(8), dp(12), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                deleteSong(s);
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void deleteSong(final Song s) {
        try {
            int n = getContentResolver().delete(android.net.Uri.parse(s.uri), null, null);
            if (n > 0) {
                MetaStore.remove(this, s.id);
                Hidden.hide(this, s.id);
                Toast.makeText(this, "删掉了：" + s.title, Toast.LENGTH_SHORT).show();
                scan();
            } else {
                Toast.makeText(this, "系统没删（可能没权限）", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this,
                    "系统不允许第三方直接删文件，请用系统「文件」或「相册」删",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "删不掉：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

'''
    s = s[:start] + new_menu + s[end:]
    wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="30"', 'android:versionCode="31"').replace('android:versionName="0.30"', 'android:versionName="0.31"')
wr(p, s)
print('ok')
