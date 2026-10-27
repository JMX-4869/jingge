package com.xiaojing.jingge;

import android.content.Context;

import org.json.JSONObject;

/** 用一句话造一套主题：模型只回 JSON，颜色在这里兜底校验。 */
final class AiTheme {
    private AiTheme() {
    }

    static void generate(Context ctx, String desc, String prevJson, String feedback,
                         Ai.Callback cb) {
        String ask = "给一个音乐播放器设计配色。\n"
                + "用户描述：" + desc + "\n"
                + (prevJson == null ? "" : "上一版的配色（JSON）：" + prevJson + "\n")
                + (feedback == null || feedback.trim().length() == 0
                ? "" : "用户的修改意见：" + feedback + "\n")
                + "只输出一个 JSON 对象，字段固定：\n"
                + "{\"name\":\"两三个字的名字\",\"bg\":\"#RRGGBB\",\"panel\":\"#RRGGBB\","
                + "\"line\":\"#RRGGBB\",\"text\":\"#RRGGBB\",\"muted\":\"#RRGGBB\","
                + "\"accent\":\"#RRGGBB\"}\n"
                + "硬要求：text 和 bg 的亮度差要大（正文必须看得清）；accent 只有一个颜色，"
                + "要能当\"正在播放\"用；panel 和 bg 只差一点点；不要输出解释。";
        Ai.ask(ctx, "你是 UI 配色师，只输出 JSON。", ask, 0.8, cb);
    }

    /** 从模型的话里抠出 JSON，做成 Theme；拿不到就 null。 */
    static Theme parse(String text) {
        if (text == null) return null;
        int a = text.indexOf('{');
        int b = text.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            JSONObject o = new JSONObject(text.substring(a, b + 1));
            String name = o.optString("name", "AI 主题");
            int bg = color(o, "bg", 0xFF0A0A0A);
            int panel = color(o, "panel", 0xFF0F0F0F);
            int line = color(o, "line", 0xFF1C1C1C);
            int tx = color(o, "text", 0xFFEDEDED);
            int muted = color(o, "muted", 0xFF6E6E6E);
            int accent = color(o, "accent", 0xFF58E0C8);
            if (name.length() > 8) name = name.substring(0, 8);
            return new Theme("c:ai-" + System.currentTimeMillis(), name,
                    bg, panel, line, tx, muted, accent);
        } catch (Throwable t) {
            return null;
        }
    }

    static String json(Theme t) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", t.name);
            o.put("bg", ThemeStore.hex(t.bg));
            o.put("panel", ThemeStore.hex(t.panel));
            o.put("line", ThemeStore.hex(t.line));
            o.put("text", ThemeStore.hex(t.text));
            o.put("muted", ThemeStore.hex(t.muted));
            o.put("accent", ThemeStore.hex(t.accent));
            return o.toString();
        } catch (Throwable e) {
            return "{}";
        }
    }

    private static int color(JSONObject o, String k, int def) {
        String v = o.optString(k, "");
        if (v.length() == 0) return def;
        if (!v.startsWith("#")) v = "#" + v;
        return ThemeStore.parseColor(v, def);
    }
}
