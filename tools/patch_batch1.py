# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
def cut(s, a, b):
    i = s.index(a); j = s.index(b, i)
    return i, j

# ---------- Lyric：导出 LRC（幂等） ----------
p = 'src/com/xiaojing/jingge/Lyric.java'
s = rd(p)
if 'public String toLrc()' not in s:
    old = '''    /** 当前时间对应的第几行。 */'''
    new = '''    /** 写回成 LRC（带译文的两行同一时间戳）。 */
    public String toLrc() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            sb.append(stamp(l.timeMs)).append(l.text).append('\\n');
            if (l.trans != null && l.trans.length() > 0) {
                sb.append(stamp(l.timeMs)).append(l.trans).append('\\n');
            }
        }
        return sb.toString();
    }

    private static String stamp(int ms) {
        if (ms < 0) ms = 0;
        int m = ms / 60000;
        int sec = (ms / 1000) % 60;
        int cs = (ms % 1000) / 10;
        return String.format("[%02d:%02d.%02d]", m, sec, cs);
    }

    /** 当前时间对应的第几行。 */'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)

# ---------- LyricStore：自动抓的缓存 ----------
p = 'src/com/xiaojing/jingge/LyricStore.java'
s = rd(p)
if 'hasAuto' not in s:
    old = '''    static boolean has(Context c, long id) {'''
    new = '''    /** 一键补全抓下来的缓存（和用户自己编辑的 .lrc 分开） */
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

    static boolean has(Context c, long id) {'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)

# ---------- LyricFinder：同步版 + 先读缓存 ----------
p = 'src/com/xiaojing/jingge/LyricFinder.java'
s = rd(p)
if 'fetchNow' not in s:
    i, j = cut(s, '    static void find(final Context ctx, final Song s, final Callback cb) {',
               '    static void invalidate(long id) {')
    new_find = '''    static void find(final Context ctx, final Song s, final Callback cb) {
        synchronized (CACHE) {
            if (CACHE.containsKey(s.id)) {
                deliver(cb, CACHE.get(s.id), "cache");
                return;
            }
        }
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] src = new String[1];
                Lyric l = fetchNow(app, s, src);
                deliver(cb, l, src[0]);
            }
        }, "lyric").start();
    }

    /** 同步版，给一键补全用。 */
    static Lyric fetchNow(Context ctx, Song s, String[] srcOut) {
        Context app = ctx.getApplicationContext();
        Lyric l = null;
        String src = null;

        String local = LyricStore.read(app, s.id);
        if (local != null) {
            l = Lyric.parse(local);
            if (l != null) src = "本地";
        }
        if (l == null) {
            String auto = LyricStore.readAuto(app, s.id);
            if (auto != null) {
                l = Lyric.parse(auto);
                if (l != null) src = "已缓存";
            }
        }
        if (l == null) {
            try {
                l = fromQQ(s);
                if (l != null) src = "QQ音乐";
            } catch (Throwable ignored) {
            }
        }
        if (l == null) {
            try {
                l = fromNetease(s);
                if (l != null) src = "网易云";
            } catch (Throwable ignored) {
            }
        }
        if (l == null) {
            try {
                l = fromLrclib(s);
                if (l != null) src = "LRCLIB";
            } catch (Throwable ignored) {
            }
        }
        if (l != null) {
            synchronized (CACHE) {
                if (CACHE.size() > 60) CACHE.clear();
                CACHE.put(s.id, l);
            }
        }
        if (srcOut != null) srcOut[0] = src;
        return l;
    }

'''
    s = s[:i] + new_find + s[j:]
    wr(p, s)

# ---------- CoverFinder：同步版 ----------
p = 'src/com/xiaojing/jingge/CoverFinder.java'
s = rd(p)
if 'fetchNow' not in s:
    i, j = cut(s, '    static void find(final Context ctx, final Song s, final Callback cb) {',
               '    private static String albumMid(Song s) throws Exception {')
    new_cover = '''    static void find(final Context ctx, final Song s, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = fetchNow(app, s);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onCover(bm);
                    }
                });
            }
        }, "cover-find").start();
    }

    /** 同步版，给一键补全用。 */
    static Bitmap fetchNow(Context ctx, Song s) {
        Context app = ctx.getApplicationContext();
        Bitmap bm = null;
        try {
            String mid = albumMid(s);
            if (mid != null) {
                bm = Http.image("https://y.gtimg.cn/music/photo_new/T002R500x500M000"
                        + mid + ".jpg", "https://y.qq.com/", 640);
            }
        } catch (Throwable ignored) {
        }
        if (bm != null) CoverStore.saveCache(app, s.id, bm);
        return bm;
    }

'''
    s = s[:i] + new_cover + s[j:]
    wr(p, s)

# ---------- CoverStore：hasCache ----------
p = 'src/com/xiaojing/jingge/CoverStore.java'
s = rd(p)
if 'hasCache' not in s:
    old = '''    static boolean hasUser(Context c, long id) {'''
    new = '''    static boolean hasCache(Context c, long id) {
        return file(c, id, false).exists();
    }

    static boolean hasUser(Context c, long id) {'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)
print('core ok')
