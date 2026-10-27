# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
if 'startBatch' in s:
    print('already'); raise SystemExit

s = s.replace('private static final String VER = "0.21";', 'private static final String VER = "0.22";')
s = s.replace('    private boolean showTrans = true;',
              '    private boolean showTrans = true;\n'
              '    private BatchMatch batchMatch;\n'
              '    private Dialog batchDialog;')

# 顶栏加「补全」按钮
old = '''        TextView rescan = text("刷新", 11, Ui.MUTED, false);'''
new = '''        TextView batchBtn = text("补全", 11, Ui.ACCENT, false);
        batchBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        batchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBatch();
            }
        });
        header.addView(batchBtn);

        TextView rescan = text("刷新", 11, Ui.MUTED, false);'''
assert old in s
s = s.replace(old, new, 1)

# 一键补全
old = '''    private void toggleTrans() {'''
new = '''    private void startBatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (batchMatch != null) return;

        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("补全歌词和封面", 15, Ui.TEXT, true));
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
                if (batchMatch != null) batchMatch.cancel();
                d.dismiss();
            }
        });
        row.addView(stop);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
        batchDialog = d;

        batchMatch = new BatchMatch(this, new ArrayList<Song>(all), new BatchMatch.Listener() {
            @Override
            public void onTick(int done, int total, int lyrics, int covers) {
                line.setText(done + " / " + total + "\\n歌词 " + lyrics + " 首 · 封面 " + covers + " 张");
            }

            @Override
            public void onDone(int total, int lyrics, int covers, boolean cancelled) {
                if (batchDialog != null && batchDialog.isShowing()) batchDialog.dismiss();
                batchDialog = null;
                batchMatch = null;
                Toast.makeText(MainActivity.this,
                        (cancelled ? "停下了：" : "补全完了：")
                                + "歌词 " + lyrics + " 首 · 封面 " + covers + " 张",
                        Toast.LENGTH_LONG).show();
                if (songAdapter != null) songAdapter.notifyDataSetChanged();
                if (albumAdapter != null) albumAdapter.notifyDataSetChanged();
                if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
            }
        });
        batchMatch.start();
    }

    private void toggleTrans() {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="21"', 'android:versionCode="22"').replace('android:versionName="0.21"', 'android:versionName="0.22"')
wr(p, s)
print('ok')
