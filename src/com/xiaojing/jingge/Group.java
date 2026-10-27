package com.xiaojing.jingge;

import java.util.List;

/** 分类里的一组：一张专辑、一个艺术家、一个文件夹。 */
public final class Group {
    public final String title;
    public final List<Song> songs;

    public Group(String title, List<Song> songs) {
        this.title = title;
        this.songs = songs;
    }
}
