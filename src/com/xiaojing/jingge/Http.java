package com.xiaojing.jingge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 极简 HTTP：只要 GET 文本和图片，不引 OkHttp。 */
final class Http {
    static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private Http() {
    }

    static byte[] bytes(String url, String referer, int connectMs, int readMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            if (referer != null) c.setRequestProperty("Referer", referer);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String text(String url, String referer, int connectMs, int readMs) {
        byte[] b = bytes(url, referer, connectMs, readMs);
        if (b == null) return null;
        try {
            return new String(b, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 下载并解码，按 maxPx 缩到合适大小。 */
    static Bitmap image(String url, String referer, int maxPx) {
        byte[] b = bytes(url, referer, 5000, 8000);
        if (b == null || b.length == 0) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(b, 0, b.length, o);
            int sample = 1;
            while (o.outWidth > 0 && Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, o);
            if (bm == null) return null;
            int max = Math.max(bm.getWidth(), bm.getHeight());
            if (max > maxPx) {
                float sc = (float) maxPx / max;
                Bitmap small = Bitmap.createScaledBitmap(bm,
                        Math.max(1, Math.round(bm.getWidth() * sc)),
                        Math.max(1, Math.round(bm.getHeight() * sc)), true);
                if (small != bm) bm.recycle();
                bm = small;
            }
            return bm;
        } catch (Throwable t) {
            return null;
        }
    }
}
