# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- Ui：加一条更淡的分隔线 ----------
p = 'src/com/xiaojing/jingge/Ui.java'
s = rd(p)
s = s.replace("    static int RIPPLE = 0x14FFFFFF;",
              "    static int RIPPLE = 0x14FFFFFF;\n    /** 行与行之间的头发丝线 */\n    static int LINE_SOFT = 0x22FFFFFF;")
s = s.replace("        RIPPLE = (t.text & 0x00FFFFFF) | 0x14000000;",
              "        RIPPLE = (t.text & 0x00FFFFFF) | 0x14000000;\n"
              "        LINE_SOFT = blend(t.line, t.bg, 0.45f);")
wr(p, s)

# ---------- 主题：浅色提高对比，深色也稍微提亮 ----------
p = 'src/com/xiaojing/jingge/ThemeStore.java'
s = rd(p)
s = s.replace('''        l.add(new Theme("bi:paper", "纸白", 0xFFFAFAFA, 0xFFFFFFFF,
                0xFFE2E2E6, 0xFF141416, 0xFF6E6E74, 0xFF0E9D8A));''',
'''        l.add(new Theme("bi:paper", "纸白", 0xFFFFFFFF, 0xFFFFFFFF,
                0xFFE8E8EC, 0xFF0F0F12, 0xFF55555C, 0xFF0B8B7A));''')
s = s.replace('''        l.add(new Theme("bi:black", "极简黑", 0xFF0A0A0A, 0xFF0F0F0F,
                0xFF1C1C1C, 0xFFEDEDED, 0xFF6E6E6E, 0xFF58E0C8));''',
'''        l.add(new Theme("bi:black", "极简黑", 0xFF0A0A0A, 0xFF0F0F0F,
                0xFF1F1F22, 0xFFF3F3F5, 0xFF7C7C84, 0xFF5FE3CB));''')
wr(p, s)

# ---------- 列表行：加头发丝分隔线 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.13";',
              'private static final String VER = "0.14";')

old = '''        View build() {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(8), dp(20), dp(8));
            row.setMinimumHeight(dp(60));

            cover = new CoverView(MainActivity.this);'''
new = '''        View build() {
            LinearLayout outer = new LinearLayout(MainActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(8), dp(20), dp(8));
            row.setMinimumHeight(dp(60));
            outer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            cover = new CoverView(MainActivity.this);'''
assert old in s
s = s.replace(old, new, 1)

# 行尾补线
old = '''            dur = text("", 11, Ui.DIM, false);
            row.addView(dur);
            return row;
        }
    }'''
new = '''            dur = text("", 11, Ui.DIM, false);
            row.addView(dur);

            View hair = new View(MainActivity.this);
            hair.setBackgroundColor(Ui.LINE_SOFT);
            outer.addView(hair, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            return outer;
        }
    }'''
assert old in s
s = s.replace(old, new, 1)

# 封面描边
old = '''                p.setColor(0x6658E0C8);
                c.drawCircle(w * 0.8f, h * 0.2f, m * 0.05f, p);
            }'''
new = '''                p.setColor(0x6658E0C8);
                c.drawCircle(w * 0.8f, h * 0.2f, m * 0.05f, p);
            }
            // 头发丝描边：让封面不和背景糊在一起
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, dp(0.6f)));
            p.setColor(Ui.LINE_SOFT);
            c.drawRoundRect(r, radius, radius, p);
            p.setStyle(Paint.Style.FILL);'''
assert old in s
s = s.replace(old, new, 1)

# nowBox 加淡色底 + 底线
old = '''        nowLyric.setPadding(0, dp(4), 0, 0);
        nowBox.addView(nowLyric);'''
new = '''        nowLyric.setPadding(0, dp(4), 0, 0);
        nowBox.addView(nowLyric);

        View nowHair = new View(this);
        nowHair.setBackgroundColor(Ui.LINE_SOFT);
        LinearLayout.LayoutParams nhp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        nhp.setMargins(0, dp(8), 0, 0);
        nowBox.addView(nowHair, nhp);
        nowBox.setBackgroundColor(Ui.blend(Ui.BG, Ui.ACCENT, 0.045f));'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# manifest
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="13"', 'android:versionCode="14"')
s = s.replace('android:versionName="0.13"', 'android:versionName="0.14"')
wr(p, s)
print('ok')
