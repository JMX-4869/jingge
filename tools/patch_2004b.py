# -*- coding: utf-8 -*-
"""0.67 补：录音源换 VOICE_RECOGNITION / 返回码补 2004·2000 / 版本号"""
import io

p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

old = '''                    r = new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC,
                            sr, android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(min, 4096));
                    r.startRecording();'''
new = '''                    android.media.AudioRecord tmp = null;
                    try {
                        tmp = new android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                                sr, android.media.AudioFormat.CHANNEL_IN_MONO,
                                android.media.AudioFormat.ENCODING_PCM_16BIT,
                                Math.max(min, 4096));
                        if (tmp.getState() != android.media.AudioRecord.STATE_INITIALIZED) {
                            try {
                                tmp.release();
                            } catch (Throwable ignored) {
                            }
                            tmp = null;
                        }
                    } catch (Throwable ignored) {
                        tmp = null;
                    }
                    if (tmp == null) {
                        tmp = new android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.MIC,
                                sr, android.media.AudioFormat.CHANNEL_IN_MONO,
                                android.media.AudioFormat.ENCODING_PCM_16BIT,
                                Math.max(min, 4096));
                    }
                    r = tmp;
                    r.startRecording();'''
assert old in s, 'audio source block missing'
s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('1/3 audio source ok')

p2 = 'src/com/xiaojing/jingge/Recognize.java'
t = io.open(p2, encoding='utf-8').read()
old2 = '''            case 3001:
                return "\u8fd9\u6bb5\u97f3\u9891\u7b97\u4e0d\u51fa\u6307\u7eb9\uff08\u592a\u5b89\u9759\u6216\u592a\u77ed\uff0c\u9760\u8fd1\u70b9\u518d\u5f55\uff09";'''
new2 = '''            case 2000:
                return "\u5f55\u97f3\u51fa\u9519\uff08\u9ea6\u514b\u98ce\u6743\u9650\u6ca1\u7ed9\uff0c\u6216\u8005\u88ab\u522b\u7684\u5e94\u7528\u5360\u7740\uff09";
            case 2004:
                return "\u670d\u52a1\u7aef\u7b97\u4e0d\u51fa\u6307\u7eb9\uff1a\u8fd9\u6bb5\u51e0\u4e4e\u662f\u9759\u97f3\uff08\u5f55\u5230\u7684\u97f3\u91cf\u592a\u4f4e\uff09";
            case 3001:
                return "\u8fd9\u6bb5\u97f3\u9891\u7b97\u4e0d\u51fa\u6307\u7eb9\uff08\u592a\u5b89\u9759\u6216\u592a\u77ed\uff0c\u9760\u8fd1\u70b9\u518d\u5f55\uff09";'''
assert old2 in t, 'describe 3001 missing'
t = t.replace(old2, new2, 1)
io.open(p2, 'w', encoding='utf-8', newline='\n').write(t)
print('2/3 describe ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('src/com/xiaojing/jingge/MainActivity.java',
   [('private static final String VER = "0.66";', 'private static final String VER = "0.67";')])
rw('AndroidManifest.xml',
   [('android:versionCode="66"', 'android:versionCode="67"'),
    ('android:versionName="0.66"', 'android:versionName="0.67"')])
print('3/3 version ok')
