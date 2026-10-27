package com.xiaojing.jingge;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** MediaStore 扫描 + 封面缓存。扫描与解码都在后台线程调用。 */
final class MusicRepo {
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };

    private MusicRepo() {
    }

    static List<Song> scan(Context ctx) {
        List<Song> out = new ArrayList<Song>();
        String[] cols = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.DATA
        };
        String where = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND "
                + MediaStore.Audio.Media.IS_ALARM + "=0 AND "
                + MediaStore.Audio.Media.IS_NOTIFICATION + "=0 AND "
                + MediaStore.Audio.Media.IS_RINGTONE + "=0 AND "
                + MediaStore.Audio.Media.DURATION + ">=30000";
        String order = MediaStore.Audio.Media.DATE_MODIFIED + " DESC, "
                + MediaStore.Audio.Media.DATE_ADDED + " DESC, "
                + MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cols, where, null, order);
            if (c == null) return out;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String title = trim(c.getString(1));
                String artist = trim(c.getString(2));
                String album = trim(c.getString(3));
                long dur = c.getLong(4);
                String file = trim(c.getString(5));
                String albumArtist = trim(c.getString(6));
                String path = trim(c.getString(7));
                if (title == null) title = stripExt(file);
                if (title == null) title = "未命名";
                if (junk(artist)) artist = null;
                if (artist == null && !junk(albumArtist)) artist = albumArtist;
                if (artist == null) {
                    String[] pair = splitTitle(title);
                    if (pair != null) {
                        title = pair[0];
                        artist = pair[1];
                    }
                }
                if (junk(album)) album = null;
                if (looksLikeRecording(title) || looksLikeRecording(file)) continue;
                if (Hidden.has(ctx, id)) continue;
                String[] meta = MetaStore.get(ctx, id);
                if (meta != null) {
                    if (meta[0] != null) title = meta[0];
                    artist = meta[1];
                    album = meta[2];
                }
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                out.add(new Song(id, title, artist, album, uri.toString(), dur, folderOf(path)));
            }
        } catch (Throwable t) {
            // 权限被撤 / 媒体库异常：给空列表，界面自己会说
        } finally {
            if (c != null) try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** 读内嵌封面；没有就 null。px 是希望的最大边。 */
    static Bitmap cover(Context ctx, Song s, int px) {
        String key = s.id + ":" + px;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, Uri.parse(s.uri));
            byte[] data = r.getEmbeddedPicture();
            if (data == null || data.length == 0) return null;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth > 0 && Math.max(o.outWidth, o.outHeight) / (sample * 2) >= px) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (bm == null) return null;

            int max = Math.max(bm.getWidth(), bm.getHeight());
            if (max > px) {
                float sc = (float) px / max;
                Bitmap small = Bitmap.createScaledBitmap(bm,
                        Math.max(1, Math.round(bm.getWidth() * sc)),
                        Math.max(1, Math.round(bm.getHeight() * sc)), true);
                if (small != bm) bm.recycle();
                bm = small;
            }
            CACHE.put(key, bm);
            return bm;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.length() == 0 ? null : s;
    }

    /** 录音、通话、语音备忘这类不是歌的东西。 */
    private static boolean looksLikeRecording(String s) {
        if (s == null) return false;
        String t = s.toLowerCase(Locale.ROOT);
        return t.contains("录音") || t.contains("通话") || t.contains("语音备忘")
                || t.contains("语音记录") || t.contains("recording")
                || t.contains("voice memo") || t.contains("voicememo")
                || t.startsWith("rec0") || t.startsWith("rec_") || t.startsWith("rec-")
                || t.startsWith("aud-") || t.startsWith("ptt-");
    }

    private static String stripExt(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** /storage/.../Music/xxx.mp3 → "Music" */
    private static String folderOf(String path) {
        if (path == null) return "其他";
        int i = path.lastIndexOf('/');
        if (i <= 0) return "其他";
        String dir = path.substring(0, i);
        int j = dir.lastIndexOf('/');
        String name = j >= 0 ? dir.substring(j + 1) : dir;
        return name.length() == 0 ? "其他" : name;
    }

    /** MediaStore 里这些值不是歌手，是目录名或占位符。 */
    private static boolean junk(String s) {
        if (s == null) return true;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.length() == 0 || t.equals("音乐") || t.equals("music") || t.equals("<unknown>")
                || t.equals("unknown") || t.equals("未知") || t.equals("未知艺术家")
                || t.equals("artist") || t.equals("audio") || t.equals("sound")
                || t.equals("未分类") || t.equals("none");
    }

    /**
     * 很多下载文件把 "歌名 - 歌手" 整个塞进标题、歌手留空或填成"音乐"。
     * 只在歌手确实为空时才拆，右边太像版本说明就不拆。
     */
    private static String[] splitTitle(String title) {
        if (title == null) return null;
        int i = title.lastIndexOf(" - ");
        if (i <= 0 || i + 3 >= title.length()) return null;
        String left = title.substring(0, i).trim();
        String right = title.substring(i + 3).trim();
        if (left.length() == 0 || right.length() == 0 || right.length() > 40) return null;
        String lr = right.toLowerCase(Locale.ROOT);
        if (lr.contains("remix") || lr.contains("live") || lr.contains("version")
                || lr.contains("edit") || lr.contains(" mix") || lr.endsWith("版")) {
            return null;
        }
        return new String[]{left, right};
    }
}
