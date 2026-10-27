package com.xiaojing.jingge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** 拿歌名去 QQ 音乐搜一遍，挑最像的那条。 */
final class MetaFinder {
    private MetaFinder() {
    }

    /** 返回 {歌名, 歌手, 专辑}，找不到就 null。 */
    static String[] find(Song s) {
        try {
            String q = URLEncoder.encode(s.title + " " + (s.artist == null ? "" : s.artist), "UTF-8");
            String body = Http.text("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=" + q
                    + "&format=json&n=8&p=1", "https://y.qq.com/", 5000, 8000);
            if (body == null) return null;
            JSONObject o = new JSONObject(body);
            JSONObject data = o.optJSONObject("data");
            JSONObject song = data == null ? null : data.optJSONObject("song");
            JSONArray list = song == null ? null : song.optJSONArray("list");
            if (list == null) return null;

            String bestTitle = null;
            String bestArtist = null;
            String bestAlbum = null;
            int best = 1;
            for (int i = 0; i < list.length(); i++) {
                JSONObject it = list.optJSONObject(i);
                if (it == null) continue;
                String name = it.optString("songname", "");
                String singer = "";
                JSONArray arr = it.optJSONArray("singer");
                if (arr != null && arr.length() > 0) {
                    JSONObject a = arr.optJSONObject(0);
                    if (a != null) singer = a.optString("name", "");
                }
                int sc = Match.score(name, singer, it.optInt("interval", 0), s);
                if (sc > best) {
                    best = sc;
                    bestTitle = name;
                    bestArtist = singer;
                    bestAlbum = it.optString("albumname", "");
                }
            }
            if (bestTitle == null || bestTitle.trim().length() == 0) return null;
            return new String[]{bestTitle.trim(), bestArtist, bestAlbum};
        } catch (Throwable t) {
            return null;
        }
    }
}
