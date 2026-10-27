package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 一键补全：把缺的歌词和封面都去网上找一遍，找到的存本地。 */
final class BatchMatch {
    interface Listener {
        void onTick(int done, int total, int lyrics, int covers);

        void onDone(int total, int lyrics, int covers, boolean cancelled);
    }

    private final Context app;
    private final List<Song> songs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final AtomicInteger done = new AtomicInteger(0);
    private final AtomicInteger lyrics = new AtomicInteger(0);
    private final AtomicInteger covers = new AtomicInteger(0);
    private final AtomicInteger alive;
    private final int total;
    private final int workers;
    private volatile boolean cancelled;
    private volatile boolean finished;

    BatchMatch(Context c, List<Song> list, Listener l) {
        app = c.getApplicationContext();
        songs = list;
        listener = l;
        total = list.size();
        workers = Math.min(3, Math.max(1, total));
        alive = new AtomicInteger(workers);
    }

    void start() {
        if (total == 0) {
            listener.onDone(0, 0, 0, false);
            return;
        }
        tick();
        for (int i = 0; i < workers; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "batch-match").start();
        }
    }

    void cancel() {
        cancelled = true;
    }

    private void loop() {
        while (!cancelled) {
            int i = cursor.getAndIncrement();
            if (i >= total) break;
            Song s = songs.get(i);
            try {
                one(s);
            } catch (Throwable ignored) {
            }
            done.incrementAndGet();
            main.post(new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            });
        }
        if (alive.decrementAndGet() == 0) {
            if (!finished) {
                finished = true;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onDone(total, lyrics.get(), covers.get(), cancelled);
                    }
                });
            }
        }
    }

    private void one(Song s) {
        boolean needLyric = !LyricStore.has(app, s.id)
                && !LyricStore.hasAuto(app, s.id)
                && LyricFinder.peek(s.id) == null;
        if (needLyric) {
            Lyric l = LyricFinder.fetchNow(app, s, null);
            if (l != null && l.lines != null && l.lines.size() > 0) {
                LyricStore.saveAuto(app, s.id, l.toLrc());
                lyrics.incrementAndGet();
            }
        }
        if (!CoverStore.hasCache(app, s.id) && !CoverStore.hasUser(app, s.id)) {
            Bitmap bm = CoverFinder.fetchNow(app, s);
            if (bm != null) covers.incrementAndGet();
        }
    }

    private void tick() {
        if (!finished) listener.onTick(done.get(), total, lyrics.get(), covers.get());
    }
}
