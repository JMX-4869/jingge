package com.xiaojing.jingge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.widget.Toast;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.List;

/**
 * 播放核心：MediaPlayer + MediaSession + 前台通知。
 * 界面只通过 bind 拿引用；锁屏、耳机、通知都走 MediaSession。
 */
public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    public interface Listener {
        void onPlaybackChanged();
    }

    public static final String ACT_TOGGLE = "com.xiaojing.jingge.TOGGLE";
    public static final String ACT_NEXT = "com.xiaojing.jingge.NEXT";
    public static final String ACT_PREV = "com.xiaojing.jingge.PREV";
    public static final String ACT_STOP = "com.xiaojing.jingge.STOP";
    public static final String ACT_REC = "com.xiaojing.jingge.RECOGNIZE";
    public static final String ACT_WORD = "com.xiaojing.jingge.LYRIC";

    private static final String CHANNEL = "playback";
    static boolean fg = false;
    static volatile boolean notifBlocked = false;   // 系统把通知关了
    private volatile boolean recBusy = false;
    private volatile String recStatus = null;
    private Lyric lyric = null;
    private long lyricId = -1;
    private int lyricIdx = -2;
    private String lyricLine = null;
    static String lastErr = null;
    private static final int NOTIF_ID = 1001;

    public class LocalBinder extends Binder {
        public MusicService service() {
            return MusicService.this;
        }
    }

    private final LocalBinder binder = new LocalBinder();
    private final PlayQueue queue = new PlayQueue();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaPlayer mp;
    private MediaSession session;
    private NotificationManager nm;
    private AudioManager am;
    private AudioFocusRequest focusReq;
    private AudioAttributes attrs;
    private Listener listener;

    private boolean playing;
    private boolean prepared;
    private boolean preparing;
    private boolean pendingPlay;
    private boolean resumeOnFocus;
    private boolean foreground;
    private int errorRun;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (playing) pause();
        }
    };

    // ───────────────────────── 生命周期 ─────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        createChannel();

        // 默认播放模式：设置在启动时生效
        queue.setMode(Ai.prefs(this).getInt("play_mode", PlayQueue.MODE_LIST));

        session = new MediaSession(this, "JingGe");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                main.post(new Runnable() {
                    public void run() {
                        play();
                    }
                });
            }

            @Override
            public void onPause() {
                main.post(new Runnable() {
                    public void run() {
                        pause();
                    }
                });
            }

            @Override
            public void onSkipToNext() {
                main.post(new Runnable() {
                    public void run() {
                        next(false);
                    }
                });
            }

            @Override
            public void onSkipToPrevious() {
                main.post(new Runnable() {
                    public void run() {
                        prev();
                    }
                });
            }

            @Override
            public void onSeekTo(final long pos) {
                main.post(new Runnable() {
                    public void run() {
                        seekTo((int) pos);
                    }
                });
            }

            @Override
            public void onStop() {
                main.post(new Runnable() {
                    public void run() {
                        pause();
                    }
                });
            }
        });
        session.setActive(true);

        IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(noisyReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(noisyReceiver, f);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (a != null) {
            if (!ACT_STOP.equals(a) && !foreground) ensureForeground();
            if (ACT_TOGGLE.equals(a)) toggle();
            else if (ACT_NEXT.equals(a)) next(false);
            else if (ACT_PREV.equals(a)) prev();
            else if (ACT_STOP.equals(a)) stopAll();
            else if (ACT_REC.equals(a)) startBgRec();
            else if (ACT_WORD.equals(a)) cycleLyricMode();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        try {
            unregisterReceiver(noisyReceiver);
        } catch (Throwable ignored) {
        }
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        abandonFocus();
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        if (nm != null) nm.cancel(NOTIF_ID);
        super.onDestroy();
    }

    // ───────────────────────── 给界面用的接口 ─────────────────────────

    public void setListener(Listener l) {
        listener = l;
        notifyChanged();
    }

    public Song currentSong() {
        return queue.current();
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean hasQueue() {
        return !queue.isEmpty();
    }

    public int queueSize() {
        return queue.size();
    }

    public int position() {
        if (mp != null && prepared) {
            try {
                return mp.getCurrentPosition();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    public int duration() {
        if (mp != null && prepared) {
            try {
                int d = mp.getDuration();
                if (d > 0) return d;
            } catch (Throwable ignored) {
            }
        }
        Song s = queue.current();
        return s == null ? 0 : (int) s.durationMs;
    }

    public int mode() {
        return queue.mode();
    }

    /**
     * 扫描结果交给服务。重建队列时尽量保住当前这首歌（按 id），
     * 播放不中断；界面点哪首也按 id 找，不依赖两边索引对齐。
     */
    public void setQueue(List<Song> songs, long restoreId) {
        if (songs == null) return;
        Song cur = queue.current();
        int keep = cur != null ? indexOf(songs, cur.id) : -1;
        if (keep < 0) keep = indexOf(songs, restoreId);
        queue.setSongs(songs, keep < 0 ? 0 : keep);
        updateSession();
        notifyChanged();
    }

    private static int indexOf(List<Song> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) return i;
        }
        return -1;
    }

    public void playById(long id) {
        for (int i = 0; i < queue.size(); i++) {
            Song s = queue.get(i);
            if (s != null && s.id == id) {
                queue.select(i);
                openCurrent(true);
                return;
            }
        }
    }

    public void playAt(int index) {
        if (!queue.select(index)) return;
        openCurrent(true);
    }

    public void play() {
        if (queue.isEmpty()) return;
        if (preparing) {
            pendingPlay = true;
            return;
        }
        if (mp == null || !prepared) {
            openCurrent(true);
            return;
        }
        try {
            mp.start();
            playing = true;
            requestFocus();
            ensureForeground();
            startLyricTick();
            updateAll();
        } catch (Throwable t) {
            playing = false;
            notifyChanged();
        }
    }

    public void pause() {
        pauseInternal(true);
    }

    private void pauseInternal(boolean byUser) {
        playing = false;
        if (byUser) resumeOnFocus = false;
        if (mp != null && prepared) {
            try {
                mp.pause();
            } catch (Throwable ignored) {
            }
        }
        updateAll();
    }

    public void toggle() {
        if (playing) pause();
        else play();
    }

    public void next(boolean autoAdvance) {
        if (queue.isEmpty()) return;
        int cur = queue.index();
        if (autoAdvance && queue.mode() == PlayQueue.MODE_ONE && mp != null && prepared) {
            restart();
            return;
        }
        int idx = queue.next(autoAdvance);
        if (idx < 0) return;
        if (idx == cur && queue.mode() == PlayQueue.MODE_ONE) {
            restart();
            return;
        }
        openCurrent(true);
    }

    public void prev() {
        if (queue.isEmpty()) return;
        if (mp != null && prepared && position() > 3000) {
            seekTo(0);
            return;
        }
        int idx = queue.prev();
        if (idx >= 0) openCurrent(true);
    }

    public void seekTo(int ms) {
        if (mp != null && prepared) {
            int d = duration();
            if (d > 0) ms = Math.max(0, Math.min(ms, d));
            try {
                mp.seekTo(ms);
            } catch (Throwable ignored) {
            }
            updateSession();
        }
    }

    public void setMode(int m) {
        queue.setMode(m);
        updateSession();
        notifyChanged();
    }

    public void cycleMode() {
        queue.modeCycle();
        updateSession();
        notifyChanged();
    }

    /** 通知栏歌词三态：0 关 / 1 跟着滚 / 2 锁定当前这句 */
    private int lyricMode() {
        return Ai.prefs(this).getInt("notif_lyric", 1);
    }

    private void cycleLyricMode() {
        int m = (lyricMode() + 1) % 3;
        Ai.prefs(this).edit().putInt("notif_lyric", m).apply();
        if (m == 0) {
            lyricLine = null;
        } else if (m == 1) {
            lyricIdx = -2;
            syncLyricLine();
        }
        updateNotification();
        toastMain(m == 0 ? "通知栏歌词：已关掉"
                : m == 1 ? "通知栏歌词：跟着播放走"
                : "通知栏歌词：锁定这一句（再点关掉）");
    }

    private final Runnable lyricTick = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            syncLyricLine();
            main.postDelayed(this, 700);
        }
    };

    private void startLyricTick() {
        main.removeCallbacks(lyricTick);
        main.post(lyricTick);
    }

    private void syncLyricLine() {
        Song s = queue.current();
        if (s == null) return;
        if (lyricId != s.id) {
            lyricId = s.id;
            lyricIdx = -2;
            String raw = LyricStore.read(this, s.id);
            if (raw == null) raw = LyricStore.readAuto(this, s.id);
            lyric = raw == null ? null : Lyric.parse(raw);
            if (lyric != null && !lyric.synced) lyric = null;
        }
        int mode = lyricMode();
        if (mode == 0) {
            if (lyricLine != null) {
                lyricLine = null;
                updateNotification();
            }
            return;
        }
        if (lyric == null) {
            if (lyricLine != null) {
                lyricLine = null;
                updateNotification();
            }
            return;
        }
        if (mode == 2 && lyricLine != null) return;   // 锁住了，别动
        int pos = 0;
        try {
            if (mp != null && prepared) pos = mp.getCurrentPosition();
        } catch (Throwable ignored) {
        }
        int idx = -1;
        for (int i = 0; i < lyric.lines.size(); i++) {
            if (lyric.lines.get(i).timeMs <= pos) idx = i;
            else break;
        }
        if (idx == lyricIdx) return;
        lyricIdx = idx;
        if (idx < 0) {
            lyricLine = null;
        } else {
            Lyric.Line l = lyric.lines.get(idx);
            String t = l.text == null ? "" : l.text.trim();
            if (l.trans != null && l.trans.trim().length() > 0) {
                t = t.length() == 0 ? l.trans.trim() : t + " / " + l.trans.trim();
            }
            lyricLine = t.length() == 0 ? null : t;
        }
        updateNotification();
    }

    private void toastMain(String msg) {
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
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("android.permission.RECORD_AUDIO")
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

    private void restart() {
        if (mp == null) return;
        try {
            mp.seekTo(0);
            mp.start();
            playing = true;
            ensureForeground();
            updateAll();
        } catch (Throwable ignored) {
        }
    }

    private void stopAll() {
        playing = false;
        releasePlayer();
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        if (nm != null) nm.cancel(NOTIF_ID);
        updateSession();
        notifyChanged();
    }

    // ───────────────────────── 播放器 ─────────────────────────

    private void openCurrent(boolean autoplay) {
        Song s = queue.current();
        if (s == null) return;
        releasePlayer();
        // 换歌了，先把上一首的歌词行清掉，等 tick 重新载入
        lyricId = -1;
        lyricIdx = -2;
        lyricLine = null;
        preparing = true;
        pendingPlay = autoplay;
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setOnPreparedListener(this);
            mp.setOnCompletionListener(this);
            mp.setOnErrorListener(this);
            mp.setDataSource(this, Uri.parse(s.uri));
            mp.prepareAsync();
        } catch (Throwable t) {
            releasePlayer();
            handleError();
            return;
        }
        updateSession();
        notifyChanged();
    }

    @Override
    public void onPrepared(MediaPlayer player) {
        preparing = false;
        prepared = true;
        errorRun = 0;
        if (pendingPlay) {
            pendingPlay = false;
            requestFocus();
            try {
                player.start();
                playing = true;
            } catch (Throwable t) {
                playing = false;
            }
            ensureForeground();
        }
        updateAll();
    }

    @Override
    public void onCompletion(MediaPlayer player) {
        main.post(new Runnable() {
            public void run() {
                if (queue.isEmpty() || !playing) return;
                int cur = queue.index();
                int idx = queue.next(true);
                if (idx < 0) return;
                if (idx == cur) {
                    restart();
                } else {
                    openCurrent(true);
                }
            }
        });
    }

    @Override
    public boolean onError(MediaPlayer player, int what, int extra) {
        preparing = false;
        prepared = false;
        main.post(new Runnable() {
            public void run() {
                handleError();
            }
        });
        return true;
    }

    private void handleError() {
        errorRun++;
        int cur = queue.index();
        int idx = errorRun > 3 ? -1 : queue.next(false);
        if (idx < 0 || idx == cur) {
            playing = false;
            releasePlayer();
            if (foreground) {
                stopForeground(true);
                foreground = false;
            }
            notifyChanged();
            return;
        }
        openCurrent(true);
    }

    private void releasePlayer() {
        preparing = false;
        pendingPlay = false;
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
    }

    // ───────────────────────── 音频焦点 ─────────────────────────

    private void requestFocus() {
        if (am == null) return;
        int r;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusReq == null) {
                focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }
            r = am.requestAudioFocus(focusReq);
        } else {
            r = am.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        // 短暂失焦时这行会在 onAudioFocusChange 里被置位
        if (r != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) resumeOnFocus = false;
    }

    private void abandonFocus() {
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && focusReq != null) {
                am.abandonAudioFocusRequest(focusReq);
            } else {
                am.abandonAudioFocus(this);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAudioFocusChange(final int change) {
        main.post(new Runnable() {
            public void run() {
                if (mp == null) return;
                switch (change) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        resumeOnFocus = false;
                        pauseInternal(false);
                        abandonFocus();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        resumeOnFocus = playing;
                        pauseInternal(false);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        try {
                            mp.setVolume(0.25f, 0.25f);
                        } catch (Throwable ignored) {
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        try {
                            mp.setVolume(1f, 1f);
                        } catch (Throwable ignored) {
                        }
                        if (resumeOnFocus && !playing) {
                            resumeOnFocus = false;
                            play();
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    // ───────────────────────── 状态同步 ─────────────────────────

    private void updateAll() {
        updateSession();
        updateNotification();
        notifyChanged();
    }

    private void updateSession() {
        if (session == null) return;
        Song s = queue.current();
        long pos = position();
        int state = playing ? PlaybackState.STATE_PLAYING
                : (s == null ? PlaybackState.STATE_NONE : PlaybackState.STATE_PAUSED);
        PlaybackState.Builder b = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO)
                .setState(state, pos, playing ? 1f : 0f);
        session.setPlaybackState(b.build());

        if (s != null) {
            MediaMetadata.Builder m = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, s.subtitle())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
            if (s.album != null) {
                m.putString(MediaMetadata.METADATA_KEY_ALBUM, s.album);
            }
            session.setMetadata(m.build());
        }
    }

    private void notifyChanged() {
        if (listener == null) return;
        listener.onPlaybackChanged();
    }

    // ───────────────────────── 通知 ─────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "播放控制",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("锁屏与通知栏的播放控制");
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    private int piFlags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }

    private PendingIntent servicePi(String action, int code) {
        Intent i = new Intent(this, MusicService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(this, code, i, piFlags());
        }
        return PendingIntent.getService(this, code, i, piFlags());
    }

    private Notification buildNotification() {
        Song s = queue.current();
        String title = s == null ? "鲸歌" : s.title;
        String sub = recStatus != null ? recStatus
                : (lyricLine != null ? lyricLine
                : (s == null ? "选一首开始" : s.subtitle()));

        Intent open = new Intent(this, MainActivity.class)
                .setAction("open")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags());

        Notification.Action aPrev = new Notification.Action.Builder(
                R.drawable.ic_prev, "上一首", servicePi(ACT_PREV, 1)).build();
        Notification.Action aPlay = new Notification.Action.Builder(
                playing ? R.drawable.ic_pause : R.drawable.ic_play,
                playing ? "暂停" : "播放", servicePi(ACT_TOGGLE, 2)).build();
        Notification.Action aNext = new Notification.Action.Builder(
                R.drawable.ic_next, "下一首", servicePi(ACT_NEXT, 3)).build();
        boolean withRec = !"ball".equals(MainActivity.recEntry(this));
        Notification.Action aRec = new Notification.Action.Builder(
                R.drawable.stat_music, recBusy ? "正在听…" : "听音识曲",
                servicePi(ACT_REC, 6)).build();

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(R.drawable.stat_music)
                .setContentTitle(title)
                .setContentText(sub)
                .setContentIntent(openPi)
                .setDeleteIntent(servicePi(ACT_STOP, 4))
                .setOngoing(playing)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Ui.ACCENT)
                .addAction(aPrev)
                .addAction(aPlay)
                .addAction(aNext);
        if (Build.VERSION.SDK_INT >= 24 && s != null) b.setSubText(s.subtitle());
        if (withRec) b.addAction(aRec);
        int lm = lyricMode();
        String wTitle = lm == 0 ? "歌词·关" : lm == 2 ? "歌词·锁" : "歌词";
        Notification.Action aWord = new Notification.Action.Builder(
                R.drawable.ic_lyric, wTitle, servicePi(ACT_WORD, 7)).build();
        b.addAction(aWord);
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    /** 通知为什么不出来 —— 让 App 自己说，别让用户猜 */
    static String selfCheck(Context c) {
        StringBuilder sb = new StringBuilder();
        NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (m == null) return "取不到通知服务";
        boolean allowed = Build.VERSION.SDK_INT < 24 || m.areNotificationsEnabled();
        sb.append("．通知总开关：").append(allowed ? "开" : "关着（系统设置 → 通知 → 鲸歌）").append('\n');
        if (Build.VERSION.SDK_INT >= 33) {
            boolean p = c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            sb.append("．通知权限：").append(p ? "已给" : "没给（设置里点一下会再问）").append('\n');
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = m.getNotificationChannel(CHANNEL);
            if (ch == null) {
                sb.append("．频道「播放控制」：还没建\n");
            } else {
                int im = ch.getImportance();
                String s = im == NotificationManager.IMPORTANCE_NONE ? "被你关掉了"
                        : im >= NotificationManager.IMPORTANCE_DEFAULT ? "正常"
                        : "低（会缩成一行，展开才看得见按钮）";
                sb.append("．频道「播放控制」：").append(s).append('\n');
            }
        }
        sb.append("．前台服务：").append(fg ? "在跑" : "没起（先放一首歌）").append('\n');
        if (lastErr != null) sb.append("．上次起通知失败：").append(lastErr).append('\n');
        return sb.toString();
    }

    private void ensureForeground() {
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
            foreground = true;
            fg = true;
            lastErr = null;
            if (Build.VERSION.SDK_INT >= 24 && nm != null) {
                notifBlocked = !nm.areNotificationsEnabled();
            }
            if (session != null) session.setActive(true);
        } catch (Throwable t) {
            foreground = false;
            fg = false;
            lastErr = t.getClass().getSimpleName() + "：" + t.getMessage();
            final String why = lastErr;
            main.post(new Runnable() {
                public void run() {
                    Toast.makeText(MusicService.this, "通知起不来：" + why,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /** 设置里改了通知栏入口，叫一声就重刷 */
    public void refreshNotification() {
        updateNotification();
    }

    private void updateNotification() {
        if (nm == null) return;
        // 通知被系统清掉过（或者启动时失败过）就自己补回来，别等人发现
        if (!foreground) {
            if (playing || queue.current() != null) ensureForeground();
            return;
        }
        try {
            nm.notify(NOTIF_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }
}
