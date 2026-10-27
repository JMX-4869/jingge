package com.xiaojing.jingge;

import android.content.Context;
import android.content.SharedPreferences;

/** 联网匹配回来的歌名/歌手/专辑，覆盖系统里读到的那些。 */
final class MetaStore {
    private static final char SEP = '\u0001';

    private MetaStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jingge_meta", Context.MODE_PRIVATE);
    }

    static boolean has(Context c, long id) {
        return prefs(c).contains(Long.toString(id));
    }

    /** {title, artist, album}，没有就 null。 */
    static String[] get(Context c, long id) {
        String v = prefs(c).getString(Long.toString(id), null);
        if (v == null) return null;
        String[] p = v.split("\u0001", -1);
        if (p.length < 3) return null;
        return new String[]{p[0].length() == 0 ? null : p[0],
                p[1].length() == 0 ? null : p[1],
                p[2].length() == 0 ? null : p[2]};
    }

    static void put(Context c, long id, String title, String artist, String album) {
        prefs(c).edit().putString(Long.toString(id),
                (title == null ? "" : title) + SEP
                        + (artist == null ? "" : artist) + SEP
                        + (album == null ? "" : album)).apply();
    }

    static int count(Context c) {
        return prefs(c).getAll().size();
    }

    static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
