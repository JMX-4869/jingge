package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * 全网找封面：QQ 音乐搜索拿 albummid，再下 500×500 的专辑图。
 * 下载结果写进 cache，下次直接读。
 */
final class CoverFinder {
    interface Callback {
        void onCover(Bitmap bm);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CoverFinder() {
    }

    static void find(final Context ctx, final Song s, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = fetchNow(app, s);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onCover(bm);
                    }
                });
            }
        }, "cover-find").start();
    }

    /** 同步版，给一键补全用。 */
    static Bitmap fetchNow(Context ctx, Song s) {
        Context app = ctx.getApplicationContext();
        Bitmap bm = null;
        try {
            String mid = albumMid(s);
            if (mid != null) {
                bm = Http.image("https://y.gtimg.cn/music/photo_new/T002R500x500M000"
                        + mid + ".jpg", "https://y.qq.com/", 640);
            }
        } catch (Throwable ignored) {
        }
        if (bm != null) CoverStore.saveCache(app, s.id, bm);
        return bm;
    }

    private static String albumMid(Song s) throws Exception {
        String q = URLEncoder.encode(s.title + " " + (s.artist == null ? "" : s.artist), "UTF-8");
        String body = Http.text("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=" + q
                + "&format=json&n=8&p=1", "https://y.qq.com/", 5000, 8000);
        if (body == null) return null;
        JSONObject o = new JSONObject(body);
        JSONObject data = o.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        if (list == null) return null;

        String bestMid = null;
        int best = -1;
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it == null) continue;
            String mid = it.optString("albummid", "");
            if (mid.length() == 0) continue;
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
                bestMid = mid;
            }
        }
        return best >= 2 ? bestMid : null;
    }
}
