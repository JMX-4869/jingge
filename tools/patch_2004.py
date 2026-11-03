# -*- coding: utf-8 -*-
"""0.67: 2004 = 服务端算不出指纹（音轨基本是静音）。加音量自检 + 换录音源"""
import io

# ── MusicService：录音源 + 能量检测 ──
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
assert old in s
s = s.replace(old, new, 1)

# 读完之后算音量，太安静就别浪费一次请求
old2 = '''                if (got <= 0) {
                    finishRec(null, "没听到声音（麦克风被别的应用占着？）", wasPlaying);
                    return;
                }'''
new2 = '''                if (got <= 0) {
                    finishRec(null, "没听到声音（麦克风被别的应用占着？）", wasPlaying);
                    return;
                }
                long sum = 0;
                int n2 = got / 2;
                for (int i = 0; i < n2; i++) {
                    short v = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
                    sum += (long) v * v;
                }
                int rms = n2 == 0 ? 0 : (int) Math.sqrt((double) sum / n2);
                if (rms < 60) {
                    finishRec(null, "录到的几乎是静音（音量 " + rms
                            + "）—— 检查麦克风权限，或者别贴太近", wasPlaying);
                    return;
                }'''
assert old2 in s
s = s.replace(old2, new2, 1)

# 服务端返回码补中文
old3 = '''            case 3001:
                return "这段音频算不出指纹（太安静或太短，靠近点再录）";'''
if old3 in s:
    s = s.replace(old3, '''            case 2004:
                return "服务端算不出指纹：这段几乎是静音（音量太低或麦克风没采到声）";
            case 2000:
                return "录音出错（麦克风权限或被别的应用占着）";
            case 3001:
                return "这段音频算不出指纹（太安静或太短，靠近点再录）";''', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 版本号
def rw(path, pairs):
    t = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in t, a
        t = t.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(t)

rw('src/com/xiaojing/jingge/MainActivity.java',
   [('private static final String VER = "0.66";', 'private static final String VER = "0.67";')])
rw('AndroidManifest.xml',
   [('android:versionCode="66"', 'android:versionCode="67"'),
    ('android:versionName="0.66"', 'android:versionName="0.67"')])
print('patched 0.67')
