package com.xiaojing.jingge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * 拿歌名去几个站搜一遍：QQ 音乐、网易云、酷狗、iTunes。
 * 每家都挑最像的一条，最后取分最高的；分够了就不再往下问。
 */
final class MetaFinder {
    private static final class Hit {
        String title;
        String artist;
        String album;
        int score;
    }

    private MetaFinder() {
    }

    /** 返回 {歌名, 歌手, 专辑}，找不到就 null。 */
    static String[] find(Song s) {
        Hit best = null;
        best = better(best, qq(s));
        if (best == null || best.score < 5) best = better(best, netease(s));
        if (best == null || best.score < 5) best = better(best, kugou(s));
        if (best == null || best.score < 5) best = better(best, itunes(s));
        if (best == null) return null;
        return new String[]{best.title, best.artist, best.album};
    }

    private static Hit better(Hit a, Hit b) {
        if (b == null) return a;
        if (a == null || b.score > a.score) return b;
        return a;
    }

    private static Hit hit(String title, String artist, String album, int durSec, Song s) {
        if (title == null || title.trim().length() == 0) return null;
        Hit h = new Hit();
        h.title = title.trim();
        h.artist = artist;
        h.album = album;
        h.score = Match.score(h.title, artist, durSec, s);
        return h.score >= 2 ? h : null;
    }

    // ───────────── QQ 音乐 ─────────────

    private static Hit qq(Song s) {
        try {
            String body = Http.text("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w="
                    + enc(s.title + " " + nz(s.artist)) + "&format=json&n=8&p=1",
                    "https://y.qq.com/", 5000, 8000);
            if (body == null) return null;
            JSONObject data = new JSONObject(body).optJSONObject("data");
            JSONObject song = data == null ? null : data.optJSONObject("song");
            JSONArray list = song == null ? null : song.optJSONArray("list");
            if (list == null) return null;
            Hit best = null;
            for (int i = 0; i < list.length(); i++) {
                JSONObject it = list.optJSONObject(i);
                if (it == null) continue;
                String singer = "";
                JSONArray arr = it.optJSONArray("singer");
                if (arr != null && arr.length() > 0) {
                    JSONObject a = arr.optJSONObject(0);
                    if (a != null) singer = a.optString("name", "");
                }
                best = better(best, hit(it.optString("songname", ""), singer,
                        it.optString("albumname", ""), it.optInt("interval", 0), s));
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    // ───────────── 网易云 ─────────────

    private static Hit netease(Song s) {
        try {
            String body = Http.text("https://music.163.com/api/search/get?s="
                    + enc(s.title + " " + nz(s.artist)) + "&type=1&limit=8&offset=0",
                    "https://music.163.com/", 5000, 8000);
            if (body == null) return null;
            JSONObject result = new JSONObject(body).optJSONObject("result");
            JSONArray songs = result == null ? null : result.optJSONArray("songs");
            if (songs == null) return null;
            Hit best = null;
            for (int i = 0; i < songs.length(); i++) {
                JSONObject it = songs.optJSONObject(i);
                if (it == null) continue;
                String singer = "";
                JSONArray arr = it.optJSONArray("artists");
                if (arr != null && arr.length() > 0) {
                    JSONObject a = arr.optJSONObject(0);
                    if (a != null) singer = a.optString("name", "");
                }
                String album = "";
                JSONObject al = it.optJSONObject("album");
                if (al != null) album = al.optString("name", "");
                best = better(best, hit(it.optString("name", ""), singer, album,
                        it.optInt("duration", 0) / 1000, s));
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    // ───────────── 酷狗 ─────────────

    private static Hit kugou(Song s) {
        try {
            String body = Http.text("https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword="
                    + enc(s.title + " " + nz(s.artist)) + "&page=1&pagesize=8",
                    "https://m.kugou.com/", 5000, 8000);
            if (body == null) return null;
            JSONObject data = new JSONObject(body).optJSONObject("data");
            JSONArray info = data == null ? null : data.optJSONArray("info");
            if (info == null) return null;
            Hit best = null;
            for (int i = 0; i < info.length(); i++) {
                JSONObject it = info.optJSONObject(i);
                if (it == null) continue;
                best = better(best, hit(it.optString("songname", ""),
                        it.optString("singername", ""), it.optString("album_name", ""),
                        it.optInt("duration", 0), s));
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    // ───────────── iTunes ─────────────

    private static Hit itunes(Song s) {
        try {
            String body = Http.text("https://itunes.apple.com/search?term="
                    + enc(s.title + " " + nz(s.artist)) + "&entity=song&limit=8",
                    null, 5000, 8000);
            if (body == null) return null;
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null) return null;
            Hit best = null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject it = results.optJSONObject(i);
                if (it == null) continue;
                best = better(best, hit(it.optString("trackName", ""),
                        it.optString("artistName", ""), it.optString("collectionName", ""),
                        it.optInt("trackTimeMillis", 0) / 1000, s));
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
