# -*- coding: utf-8 -*-
import io

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('private static final String VER = "0.31";', 'private static final String VER = "0.32";')

# 关掉菜单后隔一下再弹确认框，避免两个 Dialog 抢窗口
old = """                d.dismiss();
                act.run();"""
new = """                d.dismiss();
                v.postDelayed(act, 140);"""
if old in s:
    s = s.replace(old, new, 1)

# 确认框：标题更明确，带上歌名和不可撤销的说明
s = s.replace('"删除这个文件？"', '"确定删除文件？"')
old = '''s.title + "\\n会从手机存储里真的删掉，删了找不回来。"'''
new = '''("「" + (s.title == null ? "未命名" : s.title)
                        + "」\\n会把音乐文件从手机存储里真的删掉。\\n这一步不能撤销。")'''
if old in s:
    s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('android:versionCode="31"', 'android:versionCode="32"')
s = s.replace('android:versionName="0.31"', 'android:versionName="0.32"')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('ok')
