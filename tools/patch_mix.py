# -*- coding: utf-8 -*-
"""0.69: 内外同时录 —— 麦克风 + 内录混成一路送识别，失败自动退回麦克风再试"""
import io

p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

start = s.index('    /** 点通知栏/悬浮球：后台录 8 秒去认，不弹任何界面 */')
end = s.index('    private void finishRec(')
old = s[start:end]
assert 'startBgRec' in old

new = '''    /** 录一路 8 秒，inner=true 走内录，false 走麦克风 */
    private byte[] recordOne(boolean inner, int ms) {
        final int sr = 44100;
        int total = sr * ms / 1000;
        byte[] pcm = new byte[total * 2];
        int got = 0;
        android.media.AudioRecord r = null;
        try {
            if (inner) {
                r = Capture.open(sr);
            } else {
                int min = android.media.AudioRecord.getMinBufferSize(sr,
                        android.media.AudioFormat.CHANNEL_IN_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT);
                android.media.AudioRecord tmp = null;
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
            }
            if (r == null) return null;
            r.startRecording();
            while (got < pcm.length) {
                int n = r.read(pcm, got, Math.min(4096, pcm.length - got));
                if (n <= 0) break;
                got += n;
            }
        } catch (Throwable t) {
            lastErr = t.getMessage();
            got = 0;
        } finally {
            try {
                if (r != null) {
                    r.stop();
                    r.release();
                }
            } catch (Throwable ignored) {
            }
        }
        if (got <= 0) return null;
        byte[] out = new byte[got];
        System.arraycopy(pcm, 0, out, 0, got);
        return out;
    }

    /** 两路叠一起：短的补零，逐样本相加，防削波 */
    private byte[] mix(byte[] a, byte[] b) {
        if (a == null || a.length == 0) return b;
        if (b == null || b.length == 0) return a;
        int len = Math.max(a.length, b.length);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i += 2) {
            int va = i + 1 < a.length ? (short) ((a[i] & 0xFF) | (a[i + 1] << 8)) : 0;
            int vb = i + 1 < b.length ? (short) ((b[i] & 0xFF) | (b[i + 1] << 8)) : 0;
            int v = va + vb;
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            out[i] = (byte) (v & 0xFF);
            if (i + 1 < len) out[i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }

    private int rmsOf(byte[] pcm) {
        if (pcm == null) return 0;
        int n = pcm.length / 2;
        if (n == 0) return 0;
        long sum = 0;
        for (int i = 0; i < n; i++) {
            short v = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            sum += (long) v * v;
        }
        return (int) Math.sqrt((double) sum / n);
    }

    /** 点通知栏/悬浮球：后台录 8 秒去认，不弹任何界面 */
    private void startBgRec() {
        if (recBusy) {
            toastMain("还在听，等一下");
            return;
        }
        final String src = Ai.prefs(this).getString("rec_src", "mic");
        boolean capOn = "capture".equals(src) || "mix".equals(src);
        final boolean micOn = !"capture".equals(src);
        if (capOn && !Capture.ready()) {
            if (!micOn) {
                toastMain("内录还没授权：设置 → 听音识曲 → 授权内录");
                return;
            }
            capOn = false;   // 想内外一起但没授权，就先用麦克风
            toastMain("内录没授权，这次只听麦克风；要到设置里点一下授权");
        }
        if (micOn && Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toastMain("没给麦克风权限：设置 → 应用 → 鲸歌 → 权限 → 麦克风");
            return;
        }
        final boolean useCap = capOn;
        final boolean useMic = micOn;
        recBusy = true;
        final boolean wasPlaying = playing;
        if (playing) pause();
        recStatus = "正在听… 0s";
        if (useCap) promoteCaptureForeground();
        else promoteMicForeground();
        updateNotification();
        showRecCard();
        toastMain("开始听，8 秒别停（" + (useMic && useCap ? "内外一起"
                : useCap ? "内录" : "麦克风") + "）");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final byte[][] capBuf = new byte[1][];
                Thread capThread = null;
                if (useCap) {
                    capThread = new Thread(new Runnable() {
                        public void run() {
                            capBuf[0] = recordOne(true, 8000);
                        }
                    }, "cap-rec");
                    capThread.start();
                }
                byte[] micBuf = null;
                if (useMic) micBuf = recordOne(false, 8000);
                if (capThread != null) {
                    try {
                        capThread.join(9000);
                    } catch (Throwable ignored) {
                    }
                }
                final byte[] mic = micBuf;
                byte[] clip = useCap && useMic ? mix(micBuf, capBuf[0])
                        : useCap ? capBuf[0] : micBuf;

                int rms = rmsOf(clip);
                if (clip == null || clip.length == 0 || rms < 60) {
                    // 混合失败时，还有一路有声音的话就单独再用那一路
                    if (rmsOf(mic) >= 60) {
                        clip = mic;
                        rms = rmsOf(mic);
                    } else if (rmsOf(capBuf[0]) >= 60) {
                        clip = capBuf[0];
                        rms = rmsOf(clip);
                    } else {
                        finishRec(null, "两路都是静音（音量 " + rms
                                + "）—— 检查麦克风权限，或那个 App 不允许被录", wasPlaying);
                        return;
                    }
                }
                final byte[] use = clip;
                Recognize.identify(MusicService.this, use, 44100, new Recognize.Cb() {
                    @Override
                    public void onTrack(Recognize.Track t, String error) {
                        // 内外混过还是没认出来，再拿麦克风那一路单独试一次
                        if (t == null && useCap && useMic && mic != null && rmsOf(mic) >= 60) {
                            Recognize.identify(MusicService.this, mic, 44100, new Recognize.Cb() {
                                @Override
                                public void onTrack(Recognize.Track t2, String e2) {
                                    finishRec(t2, e2, wasPlaying);
                                }
                            });
                            return;
                        }
                        finishRec(t, error, wasPlaying);
                    }
                });
            }
        }, "bg-rec").start();
    }

'''
s = s[:start] + new + s[end:]
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('service rewritten')

# ── MainActivity：来源加第三种 ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
t = io.open(p, encoding='utf-8').read()
t = t.replace('private static final String VER = "0.68";', 'private static final String VER = "0.69";', 1)
old2 = '''        srcRow.addView(srcChip(d, "外放（麦克风）", "mic", src));
        srcRow.addView(srcChip(d, "手机内录", "capture", src));'''
new2 = '''        srcRow.addView(srcChip(d, "外放（麦克风）", "mic", src));
        srcRow.addView(srcChip(d, "手机内录", "capture", src));
        srcRow.addView(srcChip(d, "内外一起", "mix", src));'''
assert old2 in t
t = t.replace(old2, new2, 1)

old3 = '''        if ("capture".equals(src)) {'''
new3 = '''        if ("capture".equals(src) || "mix".equals(src)) {'''
assert old3 in t
t = t.replace(old3, new3, 1)

old4 = '''            TextView capNote = text("系统会弹「开始录制或投放」——同意后，"
                    + "只有允许被录的 App 的声音才抓得到；戴耳机的歌也能认。", 10, Ui.DIM, false);'''
new4 = '''            TextView capNote = text("内外一起 = 麦克风（外放/有人在唱）+ 手机内部声音，两路混在一起送去认；"
                    + "混着认不出来时会自动单独再用麦克风试一次。"
                    + "系统会弹「开始录制或投放」，同意后才抓得到内部声音。", 10, Ui.DIM, false);'''
assert old4 in t
t = t.replace(old4, new4, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('activity ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('AndroidManifest.xml',
   [('android:versionCode="68"', 'android:versionCode="69"'),
    ('android:versionName="0.68"', 'android:versionName="0.69"')])
print('version ok')
