# -*- coding: utf-8 -*-
"""0.65: 识曲也挂到 MediaSession custom action（HyperOS 卡片只认这个）"""
import io

p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

# 1) 在歌词 custom action 之前插识曲
old = '''            PlaybackState.CustomAction ca = new PlaybackState.CustomAction.Builder(
                    ACT_WORD,'''
new = '''            b.addCustomAction(new PlaybackState.CustomAction.Builder(
                    ACT_REC, "识曲", R.drawable.ic_scan).build());
            PlaybackState.CustomAction ca = new PlaybackState.CustomAction.Builder(
                    ACT_WORD,'''
assert old in s
s = s.replace(old, new, 1)

# 2) 分发
old2 = '''                } else if (ACT_WORD.equals(action)) {'''
if old2 not in s:
    old2 = '''                if (ACT_WORD.equals(action)) {'''
    new2 = '''                if (ACT_REC.equals(action)) {
                    main.post(new Runnable() {
                        public void run() {
                            startBgRec();
                        }
                    });
                    return;
                }
                if (ACT_WORD.equals(action)) {'''
else:
    new2 = '''                } else if (ACT_REC.equals(action)) {
                    main.post(new Runnable() {
                        public void run() {
                            startBgRec();
                        }
                    });
                } else if (ACT_WORD.equals(action)) {'''
s = s.replace(old2, new2, 1)

# 3) 体检里加一行：识曲入口
anchor = '        sb.append("\uFF0E\u524D\u53F0\u670D\u52A1\uFF1A")'
if anchor in s:
    entry = ('        sb.append("\uFF0E\u8BC6\u66F2\u5165\u53E3\uFF1A")\n'
             '                .append(MainActivity.recEntry(c)).append(\'\\n\');\n')
    s = s.replace(anchor, entry + anchor, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 版本号
def rw(path, pairs):
    t = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in t, a
        t = t.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(t)

rw('src/com/xiaojing/jingge/MainActivity.java',
   [('private static final String VER = "0.64";', 'private static final String VER = "0.65";')])
rw('AndroidManifest.xml',
   [('android:versionCode="64"', 'android:versionCode="65"'),
    ('android:versionName="0.64"', 'android:versionName="0.65"')])
print('patched 0.65')
