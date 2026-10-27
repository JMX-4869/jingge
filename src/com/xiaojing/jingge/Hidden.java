package com.xiaojing.jingge;

import android.content.Context;

/** 从播放器里藏掉的歌（不动文件，随时能放回来）。 */
final class Hidden {
    private Hidden() {
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jingge_hidden", Context.MODE_PRIVATE);
    }

    static boolean has(Context c, long id) {
        return prefs(c).getBoolean(Long.toString(id), false);
    }

    static void hide(Context c, long id) {
        prefs(c).edit().putBoolean(Long.toString(id), true).apply();
    }

    static void show(Context c, long id) {
        prefs(c).edit().remove(Long.toString(id)).apply();
    }

    static int count(Context c) {
        return prefs(c).getAll().size();
    }
}
