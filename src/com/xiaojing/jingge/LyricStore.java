package com.xiaojing.jingge;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** 用户自己编辑的歌词：files/lyrics/<id>.lrc。 */
final class LyricStore {
    private LyricStore() {
    }

    static File file(Context c, long id) {
        File d = new File(c.getFilesDir(), "lyrics");
        if (!d.exists()) d.mkdirs();
        return new File(d, id + ".lrc");
    }

    /** 一键补全抓下来的缓存（和用户自己编辑的 .lrc 分开） */
    static File fileAuto(Context c, long id) {
        File d = new File(c.getFilesDir(), "lyrics");
        if (!d.exists()) d.mkdirs();
        return new File(d, id + ".auto.lrc");
    }

    static boolean hasAuto(Context c, long id) {
        return fileAuto(c, id).exists();
    }

    static String readAuto(Context c, long id) {
        return readFile(fileAuto(c, id));
    }

    static boolean saveAuto(Context c, long id, String text) {
        return writeFile(fileAuto(c, id), text);
    }

    private static String readFile(File f) {
        if (!f.exists()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean writeFile(File f, String text) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(text.getBytes("UTF-8"));
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

    static boolean has(Context c, long id) {
        return file(c, id).exists();
    }

    static long stamp(Context c, long id) {
        File f = file(c, id);
        return f.exists() ? f.lastModified() : 0L;
    }

    static String read(Context c, long id) {
        File f = file(c, id);
        if (!f.exists()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static boolean save(Context c, long id, String text) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file(c, id));
            out.write(text.getBytes("UTF-8"));
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
}
