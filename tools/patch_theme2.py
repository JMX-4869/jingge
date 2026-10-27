# -*- coding: utf-8 -*-
import io

def rd(p):
    return io.open(p, encoding='utf-8').read()

def wr(p, s):
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- MainActivity ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)

s = s.replace('import android.widget.RelativeLayout;',
              'import android.widget.RelativeLayout;\nimport android.widget.ScrollView;')

s = s.replace('private static final String VER = "0.12";',
              'private static final String VER = "0.13";')

s = s.replace('Ui.apply(Ui.storedDark(this));', 'Ui.apply(this);')
s = s.replace('if (!Ui.dark) {', 'if (!Ui.isDark()) {')

old = '''    private void toggleTheme() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("dark", !Ui.dark).apply();
        recreate();
    }

'''
assert old in s
s = s.replace(old, '', 1)

i = s.find('    private void showSettings() {')
j = s.find('    private void showAbout() {')
assert i > 0 and j > i, (i, j)
new = io.open('tools/theme_methods.txt', encoding='utf-8').read()
s = s[:i] + new + s[j + len('    private void showAbout() {'):]

wr(p, s)

# ---------- 编辑页 ----------
p = 'src/com/xiaojing/jingge/LyricEditorActivity.java'
s = rd(p)
s = s.replace('Ui.apply(Ui.storedDark(this));', 'Ui.apply(this);')
s = s.replace('if (!Ui.dark) {', 'if (!Ui.isDark()) {')
wr(p, s)

# ---------- manifest ----------
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="12"', 'android:versionCode="13"')
s = s.replace('android:versionName="0.12"', 'android:versionName="0.13"')
wr(p, s)
print('ok')
