# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- MusicRepo：读歌时套用匹配结果 ----------
p = 'src/com/xiaojing/jingge/MusicRepo.java'
s = rd(p)
if 'MetaStore' not in s:
    old = '''                if (looksLikeRecording(title) || looksLikeRecording(file)) continue;'''
    new = '''                if (looksLikeRecording(title) || looksLikeRecording(file)) continue;
                String[] meta = MetaStore.get(ctx, id);
                if (meta != null) {
                    if (meta[0] != null) title = meta[0];
                    artist = meta[1];
                    album = meta[2];
                }'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)

# ---------- MainActivity：加「校名」按钮 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
if 'startNameMatch' not in s:
    s = s.replace('private static final String VER = "0.22";', 'private static final String VER = "0.23";')
    s = s.replace('    private BatchMatch batchMatch;',
                  '    private BatchMatch batchMatch;\n    private NameMatch nameMatch;')

    old = '''        TextView rescan = text("刷新", 11, Ui.MUTED, false);'''
    new = '''        TextView nameBtn = text("校名", 11, Ui.MUTED, false);
        nameBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        nameBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNameMatch();
            }
        });
        header.addView(nameBtn);

        TextView rescan = text("刷新", 11, Ui.MUTED, false);'''
    assert old in s
    s = s.replace(old, new, 1)

    old = '''    private void startBatch() {'''
    new = '''    private void startNameMatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nameMatch != null) return;

        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("匹配歌名", 15, Ui.TEXT, true));
        final TextView line = text("准备中…", 12, Ui.MUTED, false);
        line.setPadding(0, dp(8), 0, dp(12));
        box.addView(line);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        box.addView(row);
        TextView stop = text("停止", 13, Ui.MUTED, false);
        stop.setPadding(dp(12), dp(8), dp(12), dp(8));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nameMatch != null) nameMatch.cancel();
                d.dismiss();
            }
        });
        row.addView(stop);

        d.setContentView(box);
        dialogWindow(d);
        d.show();

        nameMatch = new NameMatch(this, new ArrayList<Song>(all), new NameMatch.Listener() {
            @Override
            public void onTick(int done, int total, int matched) {
                line.setText(done + " / " + total + "\\n改成新名字 " + matched + " 首");
            }

            @Override
            public void onDone(int total, int matched, boolean cancelled) {
                nameMatch = null;
                if (d.isShowing()) d.dismiss();
                Toast.makeText(MainActivity.this,
                        (cancelled ? "停下了：" : "匹配完了：")
                                + "改名 " + matched + " 首（共 " + total + "）",
                        Toast.LENGTH_LONG).show();
                if (matched > 0) scan();
            }
        });
        nameMatch.start();
    }

    private void startBatch() {'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="22"', 'android:versionCode="23"').replace('android:versionName="0.22"', 'android:versionName="0.23"')
wr(p, s)
print('ok')
