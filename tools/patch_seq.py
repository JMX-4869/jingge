# -*- coding: utf-8 -*-
"""0.71: 两路改成「依次试」——先内录 6 秒，没结果再麦克风 6 秒（并发开两个录音在很多机器上直接拿不到数据）"""
import io

p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

# 1) 加一个同步跑一次的辅助
anchor = '''    /** 点通知栏/悬浮球：后台录 8 秒去认，不弹任何界面 */'''
helper = '''    /** 录一段就认，同步等结果；没声音或没认出来都返回 null */
    private Recognize.Track tryOnce(boolean inner, int ms) {
        byte[] pcm = recordOne(inner, ms);
        if (pcm == null || rmsOf(pcm) < 60) return null;
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        final Recognize.Track[] out = new Recognize.Track[1];
        Recognize.identify(this, pcm, 44100, new Recognize.Cb() {
            @Override
            public void onTrack(Recognize.Track t, String error) {
                out[0] = t;
                latch.countDown();
            }
        });
        try {
            latch.await(25, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
        return out[0];
    }

    /** 点通知栏/悬浮球：后台录一段去认，不弹任何界面 */'''
assert anchor in s
s = s.replace(anchor, helper, 1)

# 2) 把并发的录音线程换成顺序两次
start = s.index('        new Thread(new Runnable() {', s.index('private void startBgRec() {'))
end = s.index('"}, "bg-rec").start();', start) + len('"}, "bg-rec").start();')

new_thread = '''        new Thread(new Runnable() {
            @Override
            public void run() {
                Recognize.Track t = null;
                // 先内录（耳机/别的 App 在放），没结果再用麦克风（外放/有人在唱）
                if (useCap) {
                    main.post(new Runnable() {
                        public void run() {
                            recStatus = "正在听（内录）…";
                            updateNotification();
                            showRecCard();
                        }
                    });
                    t = tryOnce(true, 6000);
                }
                if (t == null && useMic) {
                    main.post(new Runnable() {
                        public void run() {
                            recStatus = "正在听（外放）…";
                            updateNotification();
                            showRecCard();
                        }
                    });
                    t = tryOnce(false, 6000);
                }
                finishRec(t, t == null ? "没听出来（内录和外放都试过了）" : null, wasPlaying);
            }
        }, "bg-rec").start();'''
s = s[:start] + new_thread + s[end:]

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('service ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('src/com/xiaojing/jingge/MainActivity.java',
   [('private static final String VER = "0.70";', 'private static final String VER = "0.71";')])
rw('AndroidManifest.xml',
   [('android:versionCode="70"', 'android:versionCode="71"'),
    ('android:versionName="0.70"', 'android:versionName="0.71"')])
print('version ok')
