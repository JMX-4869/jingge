package com.xiaojing.jingge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 听音识曲：录一段 PCM 丢给识别服务。
 * 两个服务商，都在设置里填，别的不需要：
 *   ACRCloud —— host / access key / secret（每家免费额度看官网）
 *   AudD     —— api token
 * 两家都是"服务端听指纹"，本地不算指纹，所以必须联网 + 必须自己的 key。
 */
final class Recognize {
    static final String P_ACR = "acr";
    static final String P_AUDD = "audd";

    static final class Track {
        String title;
        String artist;
        String album;
        String cover;
        String via;
    }

    interface Cb {
        void onTrack(Track t, String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Recognize() {
    }

    // ───────────────── 配置 ─────────────────

    static String provider(Context c) {
        return Ai.prefs(c).getString("rec_provider", P_ACR);
    }

    static void setProvider(Context c, String p) {
        Ai.prefs(c).edit().putString("rec_provider", p).apply();
    }

    static String acrHost(Context c) {
        return Ai.prefs(c).getString("acr_host", "");
    }

    static String acrKey(Context c) {
        return Ai.prefs(c).getString("acr_key", "");
    }

    static String acrSecret(Context c) {
        return Ai.prefs(c).getString("acr_secret", "");
    }

    static String auddToken(Context c) {
        return Ai.prefs(c).getString("audd_token", "");
    }

    static void saveAcr(Context c, String host, String key, String secret) {
        Ai.prefs(c).edit()
                .putString("acr_host", host == null ? "" : host.trim())
                .putString("acr_key", key == null ? "" : key.trim())
                .putString("acr_secret", secret == null ? "" : secret.trim())
                .apply();
    }

    static void saveAudd(Context c, String token) {
        Ai.prefs(c).edit().putString("audd_token", token == null ? "" : token.trim()).apply();
    }

    static boolean configured(Context c) {
        if (P_AUDD.equals(provider(c))) return auddToken(c).length() > 8;
        return acrHost(c).length() > 3 && acrKey(c).length() > 3 && acrSecret(c).length() > 3;
    }

    // ───────────────── 入口 ─────────────────

    static void identify(final Context ctx, final byte[] pcm, final int sampleRate, final Cb cb) {
        final String p = provider(ctx);
        if (!configured(ctx)) {
            cb.onTrack(null, P_AUDD.equals(p)
                    ? "还没填 AudD token（设置 → 听音识曲）"
                    : "还没填 ACRCloud 的 host / key / secret（设置 → 听音识曲）");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Track t = null;
                String err = null;
                try {
                    if (P_AUDD.equals(p)) {
                        t = audd(ctx, wav(pcm, sampleRate));
                    } else {
                        t = acr(ctx, pcm, sampleRate);
                    }
                    if (t == null && err == null) err = "没听出来（可能环境太吵，或者这段太短）";
                } catch (Throwable e) {
                    err = "识别失败：" + e.getMessage();
                }
                final Track ft = t;
                final String fe = err;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onTrack(ft, fe);
                    }
                });
            }
        }, "identify").start();
    }

    // ───────────────── ACRCloud ─────────────────

    private static Track acr(Context ctx, byte[] pcm, int sampleRate) throws Exception {
        String host = acrHost(ctx);
        if (!host.contains(".")) host = host + ".acrcloud.com";
        String access = acrKey(ctx);
        String secret = acrSecret(ctx);
        String uri = "/v1/identify";
        String type = "audio";
        String ver = "1";
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);

        String toSign = "POST\n" + uri + "\n" + access + "\n" + type + "\n" + ver + "\n" + ts;
        String sig = hmacBase64(secret, toSign);

        String boundary = "----jingge" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "access_key", access);
        field(body, boundary, "data_type", type);
        field(body, boundary, "signature_version", ver);
        field(body, boundary, "signature", sig);
        field(body, boundary, "sample_bytes", "2");
        field(body, boundary, "sample_rate", String.valueOf(sampleRate));
        field(body, boundary, "timestamp", ts);
        // 音频文件部分
        body.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        body.write(("Content-Disposition: form-data; name=\"sample\"; filename=\"s.raw\"\r\n")
                .getBytes("UTF-8"));
        body.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
        body.write(pcm);
        body.write("\r\n".getBytes("UTF-8"));
        body.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));

        String resp = post("https://" + host + uri,
                "multipart/form-data; boundary=" + boundary, body.toByteArray());
        JSONObject o = new JSONObject(resp);
        JSONObject st = o.optJSONObject("status");
        int code = st == null ? -1 : st.optInt("code", -1);
        if (code != 0) {
            String msg = st == null ? "返回异常" : st.optString("msg", "code " + code);
            throw new IllegalStateException(msg);
        }
        JSONArray music = o.optJSONObject("metadata") == null ? null
                : o.optJSONObject("metadata").optJSONArray("music");
        if (music == null || music.length() == 0) return null;
        JSONObject m = music.getJSONObject(0);
        Track t = new Track();
        t.via = "ACRCloud";
        t.title = m.optString("title", null);
        JSONArray artists = m.optJSONArray("artists");
        if (artists != null && artists.length() > 0) {
            t.artist = artists.optJSONObject(0) == null ? null
                    : artists.optJSONObject(0).optString("name", null);
        }
        JSONObject album = m.optJSONObject("album");
        if (album != null) t.album = album.optString("name", null);
        JSONObject ext = m.optJSONObject("external_metadata");
        if (ext != null) {
            JSONObject sp = ext.optJSONObject("spotify");
            if (sp != null) {
                JSONObject alb = sp.optJSONObject("album");
                if (alb != null) {
                    JSONArray imgs = alb.optJSONArray("images");
                    if (imgs != null && imgs.length() > 0) {
                        t.cover = imgs.optJSONObject(0) == null ? null
                                : imgs.optJSONObject(0).optString("url", null);
                    }
                }
            }
            if (t.cover == null) {
                JSONObject dz = ext.optJSONObject("deezer");
                if (dz != null) {
                    JSONObject alb = dz.optJSONObject("album");
                    if (alb != null) t.cover = alb.optString("cover_xl", null);
                }
            }
        }
        return t;
    }

    private static void field(ByteArrayOutputStream out, String boundary,
                              String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes("UTF-8"));
        out.write(value.getBytes("UTF-8"));
        out.write("\r\n".getBytes("UTF-8"));
    }

    private static String hmacBase64(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA1"));
        return android.util.Base64.encodeToString(
                mac.doFinal(data.getBytes("UTF-8")), android.util.Base64.NO_WRAP);
    }

    // ───────────────── AudD ─────────────────

    private static Track audd(Context ctx, byte[] wav) throws Exception {
        String boundary = "----jingge" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "api_token", auddToken(ctx));
        field(body, boundary, "return", "spotify");
        body.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"s.wav\"\r\n")
                .getBytes("UTF-8"));
        body.write("Content-Type: audio/wav\r\n\r\n".getBytes("UTF-8"));
        body.write(wav);
        body.write("\r\n".getBytes("UTF-8"));
        body.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));

        String resp = post("https://api.audd.io/",
                "multipart/form-data; boundary=" + boundary, body.toByteArray());
        JSONObject o = new JSONObject(resp);
        if (!"success".equals(o.optString("status"))) {
            throw new IllegalStateException(o.optString("error", "返回异常"));
        }
        JSONObject r = o.optJSONObject("result");
        if (r == null) return null;
        Track t = new Track();
        t.via = "AudD";
        t.title = r.optString("title", null);
        t.artist = r.optString("artist", null);
        t.album = r.optString("album", null);
        JSONObject sp = r.optJSONObject("spotify");
        if (sp != null) {
            JSONObject alb = sp.optJSONObject("album");
            if (alb != null) {
                JSONArray imgs = alb.optJSONArray("images");
                if (imgs != null && imgs.length() > 0) {
                    t.cover = imgs.optJSONObject(0) == null ? null
                            : imgs.optJSONObject(0).optString("url", null);
                }
            }
        }
        return t;
    }

    // ───────────────── 工具 ─────────────────

    private static String post(String url, String contentType, byte[] body) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(45000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            c.setRequestProperty("User-Agent", Http.UA);
            c.setFixedLengthStreamingMode(body.length);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.close();
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                in.close();
            }
            String text = new String(bos.toByteArray(), "UTF-8");
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + "：" + clip(text));
            }
            return text;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String clip(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    /** 给 AudD 用：PCM16 mono → wav */
    static byte[] wav(byte[] pcm, int sampleRate) {
        int ch = 1, bits = 16;
        int dataLen = pcm.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(dataLen + 44);
        try {
            out.write("RIFF".getBytes("UTF-8"));
            writeInt(out, 36 + dataLen);
            out.write("WAVE".getBytes("UTF-8"));
            out.write("fmt ".getBytes("UTF-8"));
            writeInt(out, 16);
            writeShort(out, 1);
            writeShort(out, ch);
            writeInt(out, sampleRate);
            writeInt(out, sampleRate * ch * bits / 8);
            writeShort(out, ch * bits / 8);
            writeShort(out, bits);
            out.write("data".getBytes("UTF-8"));
            writeInt(out, dataLen);
            out.write(pcm);
        } catch (Throwable ignored) {
        }
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >> 8) & 0xff);
        o.write((v >> 16) & 0xff);
        o.write((v >> 24) & 0xff);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >> 8) & 0xff);
    }
}
