package com.xiaojing.jingge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * AI 写词：一套 OpenAI 兼容的 chat/completions。
 * 默认指向 DeepSeek，地址 / Key / 模型都能在设置里改。
 */
final class Ai {
    interface Callback {
        void onText(String text, String error);
    }

    static final String DEFAULT_BASE = "https://api.deepseek.com/v1";
    static final String DEFAULT_MODEL = "deepseek-chat";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ai() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jingge", Context.MODE_PRIVATE);
    }

    static String base(Context c) {
        String b = prefs(c).getString("ai_base", DEFAULT_BASE);
        if (b == null || b.trim().length() == 0) b = DEFAULT_BASE;
        b = b.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    static String key(Context c) {
        String k = prefs(c).getString("ai_key", "");
        return k == null ? "" : k.trim();
    }

    static String model(Context c) {
        String m = prefs(c).getString("ai_model", DEFAULT_MODEL);
        return m == null || m.trim().length() == 0 ? DEFAULT_MODEL : m.trim();
    }

    static boolean configured(Context c) {
        return key(c).length() >= 12;
    }

    static String prompt(String title, String artist, String hint) {
        StringBuilder sb = new StringBuilder();
        sb.append("给这首歌写一版中文歌词。\n");
        sb.append("歌名：").append(title == null || title.length() == 0 ? "未命名" : title).append('\n');
        if (artist != null && artist.trim().length() > 0) {
            sb.append("歌手：").append(artist.trim()).append('\n');
        }
        if (hint != null && hint.trim().length() > 0) {
            sb.append("想要的感觉：").append(hint.trim()).append('\n');
        }
        sb.append("\n要求：\n");
        sb.append("1. 12 到 24 行，一行一句，句子偏短；\n");
        sb.append("2. 要有具体的画面和动作，不要空泛的形容词堆砌；\n");
        sb.append("3. 只说人话，不要口号；\n");
        sb.append("4. 只输出歌词正文：不要标题、不要解释、不要时间戳、不要序号。");
        return sb.toString();
    }

    static void generate(final Context ctx, final String title, final String artist,
                         final String hint, final Callback cb) {
        final String k = key(ctx);
        if (k.length() < 12) {
            cb.onText(null, "还没填 API Key");
            return;
        }
        final String url = base(ctx) + "/chat/completions";
        final String m = model(ctx);
        final String ask = prompt(title, artist, hint);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = null;
                String err = null;
                HttpURLConnection c = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", m);
                    body.put("temperature", 0.9);
                    body.put("max_tokens", 900);
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content",
                            "你是一位中文作词人。写得具体、有画面，句子短，不堆形容词。"));
                    msgs.put(new JSONObject().put("role", "user").put("content", ask));
                    body.put("messages", msgs);

                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(60000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    c.setRequestProperty("Authorization", "Bearer " + k);
                    OutputStream os = c.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();

                    int code = c.getResponseCode();
                    InputStream in = code == 200 ? c.getInputStream() : c.getErrorStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                        in.close();
                    }
                    String resp = new String(bos.toByteArray(), "UTF-8");
                    if (code != 200) {
                        err = "接口 " + code + "：" + shorten(resp);
                    } else {
                        JSONObject o = new JSONObject(resp);
                        out = clean(o.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content"));
                        if (out.length() == 0) err = "模型回了空内容";
                    }
                } catch (Throwable t) {
                    err = "连不上：" + t.getMessage();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String fOut = out;
                final String fErr = err;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onText(fOut, fErr);
                    }
                });
            }
        }).start();
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    /** 去掉时间戳、序号、引号，保留正文行。 */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : s.split("\n")) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            line = line.replaceAll("^\\[?\\d{1,2}:\\d{2}([.:]\\d{1,3})?\\]?\\s*", "");
            line = line.replaceAll("^\\d+[.、)]\\s*", "");
            if (line.startsWith("歌词：") || line.startsWith("歌词:")) line = line.substring(3).trim();
            if (line.startsWith("\"") && line.endsWith("\"") && line.length() > 1) {
                line = line.substring(1, line.length() - 1);
            }
            if (line.length() == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }
}
