# -*- coding: utf-8 -*-
"""0.74 补：校名也先选范围（全库 / 当前列表）"""
import io

p = 'src/com/xiaojing/jingge/MainActivity.java'
t = io.open(p, encoding='utf-8').read()

old = '''    private void startNameMatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nameMatch != null) return;
'''
new = '''    private void startNameMatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nameMatch != null) {
            Toast.makeText(this, "正在校名，等它跑完或者先停掉", Toast.LENGTH_SHORT).show();
            return;
        }
        showNameScope();
    }

    /** 校名先问范围：全库还是当前列表 */
    private void showNameScope() {
        final Dialog cfg = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("匹配歌名", 15, Ui.TEXT, true));

        TextView tip = text("拿现有的名字去网上搜，挑最像的那条。看着像文件名的才会改。",
                11, Ui.MUTED, false);
        tip.setPadding(0, dp(6), 0, dp(12));
        tip.setLineSpacing(dp(3), 1f);
        box.addView(tip);

        box.addView(text("校哪些", 11, Ui.DIM, false));
        LinearLayout scope = new LinearLayout(this);
        scope.setOrientation(LinearLayout.HORIZONTAL);
        scope.setPadding(0, dp(6), 0, dp(14));
        box.addView(scope);

        final boolean[] whole = {true};
        final TextView sAll = text("全库 " + all.size() + " 首", 13, Ui.ACCENT, false);
        sAll.setPadding(dp(14), dp(8), dp(14), dp(8));
        pickState(sAll, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        sAll.setLayoutParams(lp);
        scope.addView(sAll);

        final TextView sNow = text("当前列表 " + display.size() + " 首", 13, Ui.MUTED, false);
        sNow.setPadding(dp(14), dp(8), dp(14), dp(8));
        pickState(sNow, false);
        scope.addView(sNow);

        sAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                whole[0] = true;
                pickState(sAll, true);
                pickState(sNow, false);
            }
        });
        sNow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                whole[0] = false;
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
                runNameMatch(whole[0]);
            }
        });
        row.addView(go);

        cfg.setContentView(box);
        dialogWindow(cfg);
        cfg.show();
    }

    private void runNameMatch(boolean wholeLibrary) {
        List<Song> target = wholeLibrary ? new ArrayList<Song>(all)
                : new ArrayList<Song>(display);
        if (target.isEmpty()) target = new ArrayList<Song>(all);
        if (target.isEmpty()) {
            Toast.makeText(this, "这个范围里没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
'''
assert old in t
t = t.replace(old, new, 1)

t = t.replace('''        nameMatch = new NameMatch(this, new ArrayList<Song>(all), new NameMatch.Listener() {''',
              '''        nameMatch = new NameMatch(this, target, new NameMatch.Listener() {''', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('ok')
