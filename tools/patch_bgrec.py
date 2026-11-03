# -*- coding: utf-8 -*-
"""0.58：通知栏/悬浮球的「听音识曲」改成后台跑（8 秒录音 + 结果走通知和 toast），不弹界面"""
import io

# ─────────── Manifest ───────────
p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
pairs = [
('android:versionCode="57"', 'android:versionCode="58"'),
('android:versionName="0.57"', 'android:versionName="0.58"'),
('''    <uses-permission android:name="android.permission.RECORD_AUDIO" />''',
 '''    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />'''),
('''            android:foregroundServiceType="mediaPlayback" />''',
 '''            android:foregroundServiceType="mediaPlayback|microphone" />'''),
]
for o, n in pairs:
    assert o in s, o[:60]
    s = s.replace(o, n, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ─────────── MusicService ───────────
p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()
pairs = [
('''    public static final String ACT_STOP = "com.xiaojing.jingge.STOP";''',
 '''    public static final String ACT_STOP = "com.xiaojing.jingge.STOP";
    public static final String ACT_REC = "com.xiaojing.jingge.RECOGNIZE";'''),

('''    static boolean fg = false;''',
 '''    static boolean fg = false;
    private volatile boolean recBusy = false;
    private volatile String recStatus = null;'''),

('''            else if (ACT_STOP.equals(a)) stopAll();''',
 '''            else if (ACT_STOP.equals(a)) stopAll();
            else if (ACT_REC.equals(a)) startBgRec();'''),

# 通知：副标题显示识曲状态，action 改成后台
('''        String sub = s == null ? "选一首开始" : s.subtitle();''',
 '''        String sub = recStatus != null ? recStatus
                : (s == null ? "选一首开始" : s.subtitle());'''),

('''        Intent rec = new Intent(this, RecognizeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent recPi = PendingIntent.getActivity(this, 6, rec, piFlags());
        Notification.Action aRec = new Notification.Action.Builder(
                R.drawable.stat_music, "听音识曲", recPi).build();''',
 '''        Notification.Action aRec = new Notification.Action.Builder(
                R.drawable.stat_music, recBusy ? "正在听…" : "听音识曲",
                servicePi(ACT_REC, 6)).build();'''),

# 后台识曲实现
('''    private void restart() {''',
 '''    private void toastMain(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /** 录音要有 mic 类型的前台服务（Android 11+ 才认这个类型） */
    private void promoteMicForeground() {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            startForeground(NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } catch (Throwable t) {
            lastErr = "mic 前台：" + t.getMessage();
        }
    }

    /** 点通知栏/悬浮球：后台录 8 秒去认，不弹任何界面 */
    private void startBgRec() {
        if (recBusy) {
            toastMain("还在听，等一下");
            return;
        }
        if (checkSelfPermission("android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toastMain("没给麦克风权限：设置 → 应用 → 鲸歌 → 权限 → 麦克风");
            return;
        }
        recBusy = true;
        final boolean wasPlaying = playing;
        if (playing) pause();
        recStatus = "正在听… 0s";
        promoteMicForeground();
        updateNotification();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int sr = 44100;
                final int total = sr * 8;
                byte[] pcm = new byte[total * 2];
                int got = 0;
                android.media.AudioRecord r = null;
                try {
                    int min = android.media.AudioRecord.getMinBufferSize(sr,
                            android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT);
                    r = new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC,
                            sr, android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(min, 4096));
                    r.startRecording();
                    while (got < total * 2) {
                        int n = r.read(pcm, got, Math.min(4096, total * 2 - got));
                        if (n <= 0) break;
                        got += n;
                        final int secs = Math.min(7, got / 2 / sr);
                        main.post(new Runnable() {
                            public void run() {
                                if (!recBusy) return;
                                recStatus = "正在听… " + secs + "s";
                                updateNotification();
                            }
                        });
                    }
                } catch (Throwable t) {
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
                if (got <= 0) {
                    finishRec(null, "录不到声音（麦克风被别的应用占着？）", wasPlaying);
                    return;
                }
                byte[] clip = new byte[got];
                System.arraycopy(pcm, 0, clip, 0, got);
                try {
                    Recognize.identify(MusicService.this, clip, sr, new Recognize.Cb() {
                        @Override
                        public void onTrack(Recognize.Track t, String error) {
                            finishRec(t, error, wasPlaying);
                        }
                    });
                } catch (Throwable t) {
                    finishRec(null, t.getMessage(), wasPlaying);
                }
            }
        }, "bg-rec").start();
    }

    private void finishRec(Recognize.Track t, String err, final boolean resume) {
        recBusy = false;
        if (t != null) {
            String title = t.title == null || t.title.length() == 0 ? "识别到一首歌" : t.title;
            recStatus = t.artist == null || t.artist.length() == 0
                    ? "识别到：" + title
                    : "识别到：" + title + " · " + t.artist;
        } else {
            recStatus = err == null ? "没听出来" : ("没听出来：" + err);
        }
        final String say = recStatus;
        main.post(new Runnable() {
            public void run() {
                toastMain(say);
                updateNotification();
                if (resume && !playing) play();
                main.postDelayed(new Runnable() {
                    public void run() {
                        recStatus = null;
                        updateNotification();
                        if (!playing && queue.current() == null) stopSelf();
                    }
                }, 8000);
            }
        });
    }

    private void restart() {'''),
]
for o, n in pairs:
    assert o in s, 'missing:\n' + o[:80]
    s = s.replace(o, n, 1)

# 需要的 import
if 'import android.widget.Toast;' not in s:
    s = s.replace('import android.os.Handler;', 'import android.os.Handler;\nimport android.widget.Toast;', 1)
if 'import android.content.pm.ServiceInfo;' not in s:
    s = s.replace('import android.content.pm', 'import android.content.pm.ServiceInfo;\nimport android.content.pm', 1) \
        if 'import android.content.pm' in s else s.replace(
            'import android.content.Intent;',
            'import android.content.Intent;\nimport android.content.pm.ServiceInfo;', 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ─────────── BallService：点球也走后台 ───────────
p = 'src/com/xiaojing/jingge/BallService.java'
s = io.open(p, encoding='utf-8').read()
import re
m = re.search(r'    private void openRecognize\(\) \{.*?\n    \}\n', s, re.S)
assert m, 'openRecognize not found'
new = '''    private void openRecognize() {
        // 后台认，不弹界面：交给播放服务去录，结果走通知和 toast
        try {
            Intent i = new Intent(this, MusicService.class).setAction(MusicService.ACT_REC);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(this, RecognizeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {
            }
        }
    }
'''
s = s[:m.start()] + new + s[m.end():]
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ─────────── 版本号（Java 里那个） ───────────
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()
assert 'private static final String VER = "0.57";' in s
s = s.replace('private static final String VER = "0.57";', 'private static final String VER = "0.58";', 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('patched 0.58')
