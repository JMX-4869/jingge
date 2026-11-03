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

    interface LinesCb {
        void onLines(String[] lines, String error);
    }

    interface ModelsCb {
        void onModels(java.util.List<String> models, String error);
    }

    static final String DEFAULT_BASE = "https://api.deepseek.com/v1";
    static final String DEFAULT_MODEL = "deepseek-chat";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ai() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jingge", Context.MODE_PRIVATE);
    }

    /** GET {base}/models，拉服务端实际的模型名。 */
    static void listModels(final Context ctx, final ModelsCb cb) {
        final String k = key(ctx);
        if (k == null || k.trim().length() < 8) {
            cb.onModels(null, "还没填 API Key");
            return;
        }
        final String url = base(ctx) + "/models";
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                java.util.List<String> out = null;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("Authorization", "Bearer " + k);
                    c.setRequestProperty("Accept", "application/json");
                    int code = c.getResponseCode();
                    InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                        in.close();
                    }
                    String body = new String(bos.toByteArray(), "UTF-8");
                    if (code < 200 || code >= 300) {
                        err = "HTTP " + code;
                    } else {
                        JSONArray arr = new JSONObject(body).optJSONArray("data");
                        if (arr == null) {
                            err = "返回里没有 data";
                        } else {
                            out = new java.util.ArrayList<String>();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.optJSONObject(i);
                                String id = o == null ? "" : o.optString("id", "");
                                if (id.length() > 0) out.add(id);
                            }
                        }
                    }
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String fe = err;
                final java.util.List<String> fo = out;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onModels(fo, fe);
                    }
                });
            }
        }).start();
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

    static String prompt(String title, String artist, String hint, boolean withTrans) {
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
        if (withTrans) {
            sb.append("4. 每行固定格式：原文|||这一行的中文翻译（分隔符是三个竖线）；\n");
            sb.append("5. 只输出这些行，不要标题、不要解释、不要时间戳、不要序号。");
        } else {
            if (withTrans) {
            sb.append("4. 每行固定格式：原文|||这一行的中文翻译（分隔符是三个竖线）；\n");
            sb.append("5. 只输出这些行，不要标题、不要解释、不要时间戳、不要序号。");
        } else {
            sb.append("4. 只输出歌词正文：不要标题、不要解释、不要时间戳、不要序号。");
        }
        }
        return sb.toString();
    }

    /** 通用一问一答，给"AI 造主题"用。 */
    static void ask(final Context ctx, final String system, final String user,
                    final double temp, final Callback cb) {
        final String k = key(ctx);
        if (k.length() < 12) {
            cb.onText(null, "还没填 API Key");
            return;
        }
        final String url = base(ctx) + "/chat/completions";
        final String m = model(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = null;
                String err = null;
                HttpURLConnection c = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", m);
                    body.put("temperature", temp);
                    body.put("max_tokens", 500);
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content", system));
                    msgs.put(new JSONObject().put("role", "user").put("content", user));
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
                        out = o.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content");
                        if (out == null || out.trim().length() == 0) err = "模型回了空内容";
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

    static void generate(final Context ctx, final String title, final String artist,
                         final String hint, final boolean withTrans, final Callback cb) {
        final String k = key(ctx);
        if (k.length() < 12) {
            cb.onText(null, "还没填 API Key");
            return;
        }
        final String url = base(ctx) + "/chat/completions";
        final String m = model(ctx);
        final String ask = prompt(title, artist, hint, withTrans);
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

    /** 逐行翻译；返回的数组长度和输入一致。 */
    static void translate(final Context ctx, final String lang, final String[] lines,
                          final LinesCb cb) {
        if (lines == null || lines.length == 0) {
            cb.onLines(null, "这首歌没有可翻的歌词行");
            return;
        }
        final String k = key(ctx);
        if (k.length() < 12) {
            cb.onLines(null, "还没填 API Key（设置 → AI）");
            return;
        }
        final String url = base(ctx) + "/chat/completions";
        final String m = model(ctx);
        final int n = lines.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i + 1).append(". ").append(lines[i]).append('\n');
        final String ask = "把下面每一行歌词翻成" + lang + "：\n" + sb.toString()
                + "\n只输出一个 JSON 字符串数组，元素数量必须正好 " + n
                + " 个，顺序与上面一一对应；不要行号、不要引号以外的说明、不要解释。";

        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] out = null;
                String err = null;
                HttpURLConnection c = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", m);
                    body.put("temperature", 0.2);
                    body.put("max_tokens", Math.min(4000, 300 + n * 90));
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content",
                            "你是歌词翻译。只做翻译：保留语气和情绪，句子短，不合并不拆行，不加解释。"));
                    msgs.put(new JSONObject().put("role", "user").put("content", ask));
                    body.put("messages", msgs);

                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(90000);
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
                        int it;
                        while ((it = in.read(buf)) != -1) bos.write(buf, 0, it);
                        in.close();
                    }
                    String resp = new String(bos.toByteArray(), "UTF-8");
                    if (code != 200) {
                        err = "接口 " + code + "：" + shorten(resp);
                    } else {
                        JSONObject o = new JSONObject(resp);
                        String content = o.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content");
                        String[] got = splitArray(content);
                        if (got == null) {
                            err = "模型没按格式回";
                        } else if (got.length != n) {
                            err = "行数对不上（要 " + n + " 行，回了 " + got.length + " 行）";
                        } else {
                            out = got;
                        }
                    }
                } catch (Throwable t) {
                    err = "连不上：" + t.getMessage();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String[] fOut = out;
                final String fErr = err;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onLines(fOut, fErr);
                    }
                });
            }
        }).start();
    }

    /** 从模型回复里抠出字符串数组：优先 JSON，其次按行拆。 */
    static String[] splitArray(String content) {
        if (content == null) return null;
        String s = content.trim();
        int a = s.indexOf('[');
        int b = s.lastIndexOf(']');
        if (a >= 0 && b > a) {
            String body = s.substring(a, b + 1);
            try {
                JSONArray arr = new JSONArray(body);
                String[] out = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) out[i] = arr.optString(i, "");
                return out;
            } catch (Throwable ignored) {
            }
            // 纯文本数组、每条一行
            String[] parts = body.substring(1, body.length() - 1).split("\n");
            java.util.List<String> list = new java.util.ArrayList<String>();
            for (String p : parts) {
                String v = p.trim().replaceAll("^[,]+|[,]+$", "").trim();
                v = v.replaceAll("^\"|\"$", "").trim();
                v = v.replaceAll("^\\\"|\\\"$", "").trim();
                if (v.length() > 0) list.add(v);
            }
            if (list.size() > 0) return list.toArray(new String[0]);
        }
        String[] lines = s.replace("\r", "").split("\n");
        java.util.List<String> list = new java.util.ArrayList<String>();
        for (String l : lines) {
            String v = l.trim();
            if (v.length() == 0) continue;
            v = v.replaceAll("^[-*\\d]+[.、)]\\s*", "").trim();
            v = v.replaceAll("^\"|\"$", "").trim();
            if (v.length() > 0) list.add(v);
        }
        return list.isEmpty() ? null : list.toArray(new String[0]);
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
