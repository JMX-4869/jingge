package com.xiaojing.jingge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 找歌词：本地手写 → QQ 音乐 → 网易云 → LRCLIB。
 * 三个网络源都按"标题 + 歌手 + 时长"打分挑最像的那条，避免拿到翻唱。
 */
final class LyricFinder {
    interface Callback {
        void onLyric(Lyric l, String source);
    }

    private static final Map<Long, Lyric> CACHE = new HashMap<Long, Lyric>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LyricFinder() {
    }

    static void find(final Context ctx, final Song s, final Callback cb) {
        synchronized (CACHE) {
            if (CACHE.containsKey(s.id)) {
                deliver(cb, CACHE.get(s.id), "cache");
                return;
            }
        }
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] src = new String[1];
                Lyric l = fetchNow(app, s, src);
                deliver(cb, l, src[0]);
            }
        }, "lyric").start();
    }

    /** 同步版，给一键补全用。 */
    static Lyric fetchNow(Context ctx, Song s, String[] srcOut) {
        Context app = ctx.getApplicationContext();
        Lyric l = null;
        String src = null;

        String local = LyricStore.read(app, s.id);
        if (local != null) {
            l = Lyric.parse(local);
            if (l != null) src = "本地";
        }
        if (l == null) {
            String auto = LyricStore.readAuto(app, s.id);
            if (auto != null) {
                l = Lyric.parse(auto);
                if (l != null) src = "已缓存";
            }
        }
        if (l == null) {
            try {
                l = fromQQ(s);
                if (l != null) src = "QQ音乐";
            } catch (Throwable ignored) {
            }
        }
        if (l == null) {
            try {
                l = fromNetease(s);
                if (l != null) src = "网易云";
            } catch (Throwable ignored) {
            }
        }
        if (l == null) {
            try {
                l = fromLrclib(s);
                if (l != null) src = "LRCLIB";
            } catch (Throwable ignored) {
            }
        }
        if (l != null) {
            synchronized (CACHE) {
                if (CACHE.size() > 60) CACHE.clear();
                CACHE.put(s.id, l);
            }
        }
        if (srcOut != null) srcOut[0] = src;
        return l;
    }

    static void invalidate(long id) {
        synchronized (CACHE) {
            CACHE.remove(id);
        }
    }

    static Lyric peek(long id) {
        synchronized (CACHE) {
            return CACHE.get(id);
        }
    }

    private static void deliver(final Callback cb, final Lyric l, final String src) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                cb.onLyric(l, src);
            }
        });
    }

    // ───────────── QQ 音乐 ─────────────

    private static Lyric fromQQ(Song s) {
        String q = enc(s.title + " " + nz(s.artist));
        String body = Http.text("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=" + q
                + "&format=json&n=8&p=1", "https://y.qq.com/", 5000, 8000);
        if (body == null) return null;
        JSONObject o = json(body);
        JSONObject data = o == null ? null : o.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        if (list == null) return null;

        String mid = null;
        int best = -1;
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it == null) continue;
            String singer = "";
            JSONArray arr = it.optJSONArray("singer");
            if (arr != null && arr.length() > 0) {
                JSONObject a = arr.optJSONObject(0);
                if (a != null) singer = a.optString("name", "");
            }
            int sc = Match.score(it.optString("songname", ""), singer,
                    it.optInt("interval", 0), s);
            if (sc > best) {
                best = sc;
                mid = it.optString("songmid", null);
            }
        }
        if (mid == null || best < 2) return null;

        String lj = Http.text("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                + mid + "&format=json&nobase64=1", "https://y.qq.com/", 5000, 8000);
        if (lj == null) return null;
        JSONObject jo = json(lj);
        if (jo == null) return null;
        return Lyric.parse(jo.optString("lyric", ""), jo.optString("trans", ""));
    }

    // ───────────── 网易云 ─────────────

    private static Lyric fromNetease(Song s) {
        String q = enc(s.title + " " + nz(s.artist));
        String body = Http.text("https://music.163.com/api/search/get?s=" + q
                + "&type=1&limit=8&offset=0", "https://music.163.com/", 5000, 8000);
        if (body == null) return null;
        JSONObject o = json(body);
        JSONObject result = o == null ? null : o.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        if (songs == null) return null;

        long id = -1;
        int best = -1;
        for (int i = 0; i < songs.length(); i++) {
            JSONObject it = songs.optJSONObject(i);
            if (it == null) continue;
            String singer = "";
            JSONArray arr = it.optJSONArray("artists");
            if (arr != null && arr.length() > 0) {
                JSONObject a = arr.optJSONObject(0);
                if (a != null) singer = a.optString("name", "");
            }
            int sc = Match.score(it.optString("name", ""), singer,
                    it.optInt("duration", 0) / 1000, s);
            if (sc > best) {
                best = sc;
                id = it.optLong("id", -1);
            }
        }
        if (id <= 0 || best < 2) return null;

        String lj = Http.text("https://music.163.com/api/song/lyric?id=" + id
                + "&lv=1&kv=1&tv=-1", "https://music.163.com/", 5000, 8000);
        if (lj == null) return null;
        JSONObject jo = json(lj);
        JSONObject lrc = jo == null ? null : jo.optJSONObject("lrc");
        if (lrc == null) return null;
        JSONObject tl = jo.optJSONObject("tlyric");
        String trans = tl == null ? "" : tl.optString("lyric", "");
        return Lyric.parse(lrc.optString("lyric", ""), trans);
    }

    // ───────────── LRCLIB ─────────────

    private static Lyric fromLrclib(Song s) {
        String q = enc(s.title + " " + nz(s.artist));
        String body = Http.text("https://lrclib.net/api/search?q=" + q, null, 4000, 4000);
        if (body == null) return null;
        JSONArray arr = arr(body);
        if (arr == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;
            if (Match.score(it.optString("trackName", ""), it.optString("artistName", ""),
                    it.optInt("duration", 0), s) < 2) continue;
            String synced = it.optString("syncedLyrics", "");
            if (synced.length() > 0) {
                Lyric l = Lyric.parse(synced);
                if (l != null) return l;
            }
            String plain = it.optString("plainLyrics", "");
            if (plain.length() > 0) {
                Lyric l = Lyric.parse(plain);
                if (l != null) return l;
            }
        }
        return null;
    }

    // ───────────── 工具 ─────────────

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static JSONObject json(String s) {
        try {
            return new JSONObject(s);
        } catch (Throwable t) {
            return null;
        }
    }

    private static JSONArray arr(String s) {
        try {
            return new JSONArray(s);
        } catch (Throwable t) {
            return null;
        }
    }
}
