package com.xiaojing.jingge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 主题的存取：内置几套，自制存在本机。 */
final class ThemeStore {
    private static final String KEY_ID = "theme_id";
    private static final String KEY_LIST = "themes";

    private ThemeStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jingge", Context.MODE_PRIVATE);
    }

    static List<Theme> builtins() {
        List<Theme> l = new ArrayList<Theme>();
        l.add(new Theme("bi:black", "极简黑", 0xFF0A0A0A, 0xFF0F0F0F,
                0xFF1F1F22, 0xFFF3F3F5, 0xFF7C7C84, 0xFF5FE3CB));
        l.add(new Theme("bi:entropy", "熵光", 0xFF05070A, 0xFF080C11,
                0xFF122A33, 0xFFE6FBFF, 0xFF5E8A94, 0xFF35F0D0));
        l.add(new Theme("bi:paper", "纸白", 0xFFFFFFFF, 0xFFFFFFFF,
                0xFFE8E8EC, 0xFF0F0F12, 0xFF55555C, 0xFF0B8B7A));
        l.add(new Theme("bi:ink", "墨绿", 0xFF0B1410, 0xFF10201A,
                0xFF1C2E26, 0xFFE8F2EC, 0xFF6E8A7C, 0xFF6FE0A8));
        l.add(new Theme("bi:night", "夜蓝", 0xFF0A1020, 0xFF0F182C,
                0xFF1C2840, 0xFFE6EDF8, 0xFF6E7E96, 0xFF5AA8FF));
        l.add(new Theme("bi:amber", "暖棕", 0xFF14100C, 0xFF1E1812,
                0xFF2E261C, 0xFFF0E8DC, 0xFF8A7A66, 0xFFE0A458));
        l.add(new Theme("bi:plum", "青梅", 0xFF11130E, 0xFF191C14,
                0xFF2A2E20, 0xFFEEF2E4, 0xFF7A8268, 0xFFB9D65A));
        return l;
    }

    static List<Theme> custom(Context c) {
        List<Theme> l = new ArrayList<Theme>();
        String raw = prefs(c).getString(KEY_LIST, "");
        if (raw == null || raw.length() == 0) return l;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                Theme t = fromJson(a.getJSONObject(i));
                if (t != null) l.add(t);
            }
        } catch (Throwable ignored) {
        }
        return l;
    }

    static List<Theme> all(Context c) {
        List<Theme> l = builtins();
        l.addAll(custom(c));
        return l;
    }

    static Theme active(Context c) {
        String id = prefs(c).getString(KEY_ID, "");
        for (Theme t : all(c)) {
            if (t.id.equals(id)) return t;
        }
        return builtins().get(Ui.systemDark(c) ? 0 : 1);
    }

    static void setActive(Context c, String id) {
        prefs(c).edit().putString(KEY_ID, id).apply();
    }

    static void save(Context c, Theme t) {
        List<Theme> list = custom(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(t.id)) {
                list.set(i, t);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(t);
        JSONArray a = new JSONArray();
        for (Theme x : list) a.put(toJson(x));
        prefs(c).edit().putString(KEY_LIST, a.toString()).apply();
    }

    static void delete(Context c, String id) {
        List<Theme> list = custom(c);
        JSONArray a = new JSONArray();
        for (Theme x : list) {
            if (!x.id.equals(id)) a.put(toJson(x));
        }
        prefs(c).edit().putString(KEY_LIST, a.toString()).apply();
        if (id.equals(prefs(c).getString(KEY_ID, ""))) {
            prefs(c).edit().remove(KEY_ID).apply();
        }
    }

    static JSONObject toJson(Theme t) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", t.name);
            o.put("bg", hex(t.bg));
            o.put("panel", hex(t.panel));
            o.put("line", hex(t.line));
            o.put("text", hex(t.text));
            o.put("muted", hex(t.muted));
            o.put("accent", hex(t.accent));
        } catch (Throwable ignored) {
        }
        return o;
    }

    static Theme fromJson(JSONObject o) {
        try {
            String name = o.optString("name", "自制");
            int bg = parseColor(o.optString("bg", "#0A0A0A"), 0xFF0A0A0A);
            int text = parseColor(o.optString("text", "#EDEDED"), 0xFFEDEDED);
            int muted = parseColor(o.optString("muted", "#6E6E6E"), 0xFF6E6E6E);
            int line = parseColor(o.optString("line", "#1C1C1C"), 0xFF1C1C1C);
            int accent = parseColor(o.optString("accent", "#58E0C8"), 0xFF58E0C8);
            int panel = parseColor(o.optString("panel", ""), -1);
            if (panel == -1) panel = Ui.blend(bg, text, 0.06f);
            return new Theme("c:" + name, name, bg, panel, line, text, muted, accent);
        } catch (Throwable t) {
            return null;
        }
    }

    static int parseColor(String s, int def) {
        if (s == null) return def;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) {
            try {
                return 0xFF000000 | Integer.parseInt(s, 16);
            } catch (Throwable ignored) {
            }
        }
        if (s.length() == 8) {
            try {
                return (int) Long.parseLong(s, 16);
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    static String hex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }
}
