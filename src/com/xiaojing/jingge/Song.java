package com.xiaojing.jingge;

/** 一首歌的最小信息集。不依赖 Android，便于单测。 */
public final class Song {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final String uri;        // content:// URI
    public final long durationMs;
    public final String folder;     // 所在文件夹名，分类用

    public Song(long id, String title, String artist, String album, String uri, long durationMs) {
        this(id, title, artist, album, uri, durationMs, null);
    }

    public Song(long id, String title, String artist, String album, String uri,
                long durationMs, String folder) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.uri = uri;
        this.durationMs = durationMs;
        this.folder = folder;
    }

    /** 列表第二行：歌手，没有就退到专辑。 */
    public String subtitle() {
        if (artist != null && artist.length() > 0) return artist;
        if (album != null && album.length() > 0) return album;
        return "未知艺术家";
    }

    public static String fmtDuration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
