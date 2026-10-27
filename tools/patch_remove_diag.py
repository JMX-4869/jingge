# -*- coding: utf-8 -*-
import io

def rd(p):
    return io.open(p, encoding='utf-8').read()

def wr(p, s):
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)

old = '''        brand.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (diag == null) return true;
                diag.setVisibility(diag.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
                if (diag.getVisibility() == View.VISIBLE) updateDiag();
                return true;
            }
        });
'''
assert old in s
s = s.replace(old, '', 1)

old = '''        diag = new TextView(this);
        diag.setTextSize(10);
        diag.setTextColor(0xFFFF6B4A);
        diag.setBackgroundColor(0x99000000);
        diag.setPadding(dp(8), dp(3), dp(8), dp(3));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.gravity = Gravity.BOTTOM;
        root.addView(diag, dlp);
        diag.setVisibility(View.GONE);

'''
assert old in s
s = s.replace(old, '', 1)

old = '''    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (!has || diag == null) return;
        updateDiag();
    }

    private void updateDiag() {
        if (diag == null || diagTop == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("v").append(VER)
                .append(" root=").append(rootView.getHeight())
                .append(" page=").append(diagPage.getHeight())
                .append(" top=").append(diagTop.getHeight())
                .append("(p").append(diagTop.getLayoutParams().height).append(")");
        for (int i = 0; i < diagTop.getChildCount(); i++) {
            View c = diagTop.getChildAt(i);
            sb.append(" c").append(i).append('=').append(c.getHeight())
                    .append("(p").append(c.getLayoutParams().height).append(")");
        }
        sb.append(" list=").append(diagList.getHeight())
                .append("(p").append(diagList.getLayoutParams().height).append(")");
        diag.setText(sb);
    }

'''
assert old in s
s = s.replace(old, '', 1)

old = '''    private FrameLayout rootView;
    private View diagPage, diagList;
    private LinearLayout diagTop;
    private TextView diag;
'''
assert old in s
s = s.replace(old, '', 1)

for line in ('        rootView = root;\n', '        diagPage = page;\n',
             '        diagTop = top;\n', '        diagList = listBox;\n'):
    assert line in s, line
    s = s.replace(line, '', 1)

s = s.replace('private static final String VER = "0.10";',
              'private static final String VER = "0.11";')
wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="10"', 'android:versionCode="11"')
s = s.replace('android:versionName="0.10"', 'android:versionName="0.11"')
wr(p, s)
print('ok')
