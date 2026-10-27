# -*- coding: utf-8 -*-
import io

def rd(p):
    return io.open(p, encoding='utf-8').read()

def wr(p, s):
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- 编辑页 ----------
p = 'src/com/xiaojing/jingge/LyricEditorActivity.java'
s = rd(p)
s = s.replace("import android.app.Activity;",
              "import android.app.Activity;\nimport android.app.Dialog;\n"
              "import android.graphics.Color;\nimport android.graphics.drawable.ColorDrawable;")
s = s.replace("import android.view.Gravity;",
              "import android.view.Gravity;\nimport android.view.Window;")

old = '        TextView punch = text("打点", 13, Ui.ACCENT, true);'
new = '''        TextView ai = text("AI", 13, Ui.MUTED, true);
        ai.setGravity(Gravity.CENTER);
        ai.setPadding(dp(14), dp(8), dp(14), dp(8));
        ai.setBackground(Ui.roundStroke(0x00000000, Ui.LINE, dp(10), dp(1)));
        ai.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAiDialog();
            }
        });
        LinearLayout.LayoutParams aip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aip.setMargins(0, 0, dp(8), 0);
        ctrl.addView(ai, aip);

        TextView punch = text("打点", 13, Ui.ACCENT, true);'''
assert old in s
s = s.replace(old, new, 1)

methods = io.open('tools/ai_methods.txt', encoding='utf-8').read()
anchor = '    private void punchNext() {'
assert anchor in s
s = s.replace(anchor, methods + anchor, 1)
wr(p, s)

# ---------- 主页面 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.9";',
              'private static final String VER = "0.10";')
s = s.replace('        root.addView(diag, dlp);\n\n        return root;',
              '        root.addView(diag, dlp);\n        diag.setVisibility(View.GONE);\n\n        return root;')
s = s.replace('        TextView brand = text("鲸歌 " + VER, 13, Ui.MUTED, false);',
              '        TextView brand = text("鲸歌 " + VER, 13, Ui.MUTED, false);\n'
              '        brand.setOnLongClickListener(new View.OnLongClickListener() {\n'
              '            @Override\n'
              '            public boolean onLongClick(View v) {\n'
              '                if (diag == null) return true;\n'
              '                diag.setVisibility(diag.getVisibility() == View.VISIBLE\n'
              '                        ? View.GONE : View.VISIBLE);\n'
              '                if (diag.getVisibility() == View.VISIBLE) updateDiag();\n'
              '                return true;\n'
              '            }\n'
              '        });')
old = '''    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (!has || diag == null) return;'''
new = '''    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (!has || diag == null) return;
        updateDiag();
    }

    private void updateDiag() {
        if (diag == null || diagTop == null) return;'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# manifest
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="9"', 'android:versionCode="10"')
s = s.replace('android:versionName="0.9"', 'android:versionName="0.10"')
wr(p, s)
print('patched')
