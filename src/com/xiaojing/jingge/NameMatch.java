package com.xiaojing.jingge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 一键校名：拿每首歌现有的名字去网上搜，挑最像的那条存下来。 */
final class NameMatch {
    interface Listener {
        void onTick(int done, int total, int matched);

        void onDone(int total, int matched, boolean cancelled);
    }

    private final Context app;
    private final List<Song> songs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final AtomicInteger done = new AtomicInteger(0);
    private final AtomicInteger matched = new AtomicInteger(0);
    private final AtomicInteger alive;
    private final int total;
    private final int workers;
    private volatile boolean cancelled;
    private volatile boolean finished;

    NameMatch(Context c, List<Song> list, Listener l) {
        app = c.getApplicationContext();
        songs = list;
        listener = l;
        total = list.size();
        workers = Math.min(3, Math.max(1, total));
        alive = new AtomicInteger(workers);
    }

    void start() {
        if (total == 0) {
            listener.onDone(0, 0, false);
            return;
        }
        tick();
        for (int i = 0; i < workers; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "name-match").start();
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
        if (alive.decrementAndGet() == 0 && !finished) {
            finished = true;
            main.post(new Runnable() {
                @Override
                public void run() {
                    listener.onDone(total, matched.get(), cancelled);
                }
            });
        }
    }

    private void one(Song s) {
        if (MetaStore.has(app, s.id)) return;
        String[] m = MetaFinder.find(s);
        if (m == null) return;
        if (same(m[0], s.title) && same(m[1], s.artist) && same(m[2], s.album)) return;
        MetaStore.put(app, s.id, m[0], m[1], m[2]);
        matched.incrementAndGet();
    }

    private static boolean same(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.trim().equals(b.trim());
    }

    private void tick() {
        if (!finished) listener.onTick(done.get(), total, matched.get());
    }
}
