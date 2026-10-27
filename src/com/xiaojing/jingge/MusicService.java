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

    private static final String CHANNEL = "playback";
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

    public void cycleMode() {
        queue.modeCycle();
        updateSession();
        notifyChanged();
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
        String sub = s == null ? "选一首开始" : s.subtitle();

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
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return b.build();
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
            if (session != null) session.setActive(true);
        } catch (Throwable t) {
            foreground = false;
        }
    }

    private void updateNotification() {
        if (!foreground || nm == null) return;
        try {
            nm.notify(NOTIF_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }
}
