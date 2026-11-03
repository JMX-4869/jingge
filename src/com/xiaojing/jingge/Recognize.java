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
 * 服务商：ACRCloud —— host / access key / secret（额度看官网）。
 * "服务端听指纹"，本地不算指纹，所以必须联网 + 必须自己的 key。
 */
final class Recognize {
    static final String P_ACR = "acr";

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

    /** 服务端返回码留着，设置里的「测一下」要按码说话 */
    static final class ApiErr extends Exception {
        final int code;

        ApiErr(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Recognize() {
    }

    // ───────────────── 配置 ─────────────────

    static String acrHost(Context c) {
        return Ai.prefs(c).getString("acr_host", "");
    }

    static String acrKey(Context c) {
        return Ai.prefs(c).getString("acr_key", "");
    }

    static String acrSecret(Context c) {
        return Ai.prefs(c).getString("acr_secret", "");
    }

    static void saveAcr(Context c, String host, String key, String secret) {
        Ai.prefs(c).edit()
                .putString("acr_host", host == null ? "" : host.trim())
                .putString("acr_key", key == null ? "" : key.trim())
                .putString("acr_secret", secret == null ? "" : secret.trim())
                .apply();
    }

    static boolean configured(Context c) {
        return acrHost(c).length() > 3 && acrKey(c).length() > 3 && acrSecret(c).length() > 3;
    }

    // ───────────────── 入口 ─────────────────

    static void identify(final Context ctx, final byte[] pcm, final int sampleRate, final Cb cb) {
        if (!configured(ctx)) {
            cb.onTrack(null, "还没填 ACRCloud 的 host / key / secret（设置 → 听音识曲）");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Track t = null;
                String err = null;
                try {
                    t = acr(ctx, pcm, sampleRate);
                    if (t == null && err == null) err = "没听出来（可能环境太吵，或者这段太短）";
                } catch (ApiErr e) {
                    err = describe(e.code, e.getMessage());
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
        String host = normalizeHost(acrHost(ctx));
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
            throw new ApiErr(code, msg);
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

    /** 用户粘什么的都有：带 https 的、带 /v1/identify 的、只写一半的，统一成裸 host */
    static String normalizeHost(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.startsWith("https://")) h = h.substring(8);
        else if (h.startsWith("http://")) h = h.substring(7);
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        int cut = h.indexOf("/v1/identify");
        if (cut > 0) h = h.substring(0, cut);
        if (h.indexOf('.') < 0) {
            if (h.startsWith("identify-cn") || h.startsWith("api-cn") || h.endsWith("-cn-north-1")) {
                h = h + ".acrcloud.cn";
            } else {
                h = h + ".acrcloud.com";
            }
        }
        return h;
    }

    private static String hmacBase64(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA1"));
        return android.util.Base64.encodeToString(
                mac.doFinal(data.getBytes("UTF-8")), android.util.Base64.NO_WRAP);
    }

    // ───────────────── 工具 ─────────────────

    static String describe(int code, String msg) {
        switch (code) {
            case 1001:
                return "Access Key 不对（控制台里再抄一遍）";
            case 1002:
                return "Access Secret 不对";
            case 1003:
                return "签名没通过 —— Host 和 Key 可能不是同一个区的";
            case 2000:
                return "本机时间偏差太大，签名会失效";
            case 3001:
                return "这段音频算不出指纹（太安静或太短，靠近点再录）";
            case 3003:
                return "没听出来（服务端说没有匹配的歌）";
            case 3013:
                return "这段太短了，录够 5 秒再试";
            default:
                return "识别服务报错（返回码 " + code + "）";
        }
    }

    /** 设置里的「测一下」：拿 1 秒静音打一次，只要签名过了就算配对了 */
    static void testKeys(final Context ctx, final Cb cb) {
        if (!configured(ctx)) {
            cb.onTrack(null, "还没填全（Host / Access Key / Access Secret 三样都要）");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    Track t = acr(ctx, new byte[SR_TEST * 2], SR_TEST);
                    if (t != null) {
                        err = null;
                    }
                } catch (ApiErr e) {
                    // 3001 算不出指纹、3003 没匹配 —— 都说明签名和 key 已经过了（错 key 会先报 100x）
                    err = (e.code == 3001 || e.code == 3003)
                            ? null
                            : describe(e.code, e.getMessage());
                } catch (Throwable e) {
                    err = "连不上：" + e.getMessage();
                }
                final String fe = err;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onTrack(fe == null ? new Track() : null,
                                fe == null ? "配对成功：签名和 key 都过了（没听出歌是正常的，测的是静音）" : fe);
                    }
                });
            }
        }, "rec-test").start();
    }

    private static final int SR_TEST = 44100;

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
