package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;

/** 封面落盘：用户选的放 files/covers/，联网找到的放 cache/covers/。 */
final class CoverStore {
    private CoverStore() {
    }

    private static File dir(Context c, boolean user) {
        File d = new File(c.getFilesDir(), user ? "covers" : "covers_cache");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, long id, boolean user) {
        return new File(dir(c, user), (user ? "u_" : "c_") + id + ".jpg");
    }

    static boolean hasCache(Context c, long id) {
        return file(c, id, false).exists();
    }

    static boolean hasUser(Context c, long id) {
        return file(c, id, true).exists();
    }

    static void deleteUser(Context c, long id) {
        File f = file(c, id, true);
        if (f.exists()) f.delete();
    }

    static void deleteCache(Context c, long id) {
        File f = file(c, id, false);
        if (f.exists()) f.delete();
    }

    static boolean saveUser(Context c, long id, Bitmap bm) {
        return save(c, id, bm, true);
    }

    static boolean saveCache(Context c, long id, Bitmap bm) {
        return save(c, id, bm, false);
    }

    private static boolean save(Context c, long id, Bitmap bm, boolean user) {
        FileOutputStream out = null;
        try {
            File f = file(c, id, user);
            out = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) try {
                out.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static Bitmap loadUser(Context c, long id, int px) {
        return load(file(c, id, true), px);
    }

    static Bitmap loadCache(Context c, long id, int px) {
        return load(file(c, id, false), px);
    }

    static Bitmap load(File f, int px) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth > 0 && Math.max(o.outWidth, o.outHeight) / (sample * 2) >= px) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }
}
