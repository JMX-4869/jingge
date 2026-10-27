package com.xiaojing.jingge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 纯逻辑播放队列：顺序 / 单曲 / 随机。
 * 随机模式用"历史栈"支持上一首，避免"随机一按就回不去"。
 */
public final class PlayQueue {
    public static final int MODE_LIST = 0;
    public static final int MODE_ONE = 1;
    public static final int MODE_SHUFFLE = 2;

    private static final int HISTORY_MAX = 50;

    private final List<Song> songs = new ArrayList<Song>();
    private final List<Integer> history = new ArrayList<Integer>();
    private final Random rnd;

    private int index = -1;
    private int mode = MODE_LIST;

    public PlayQueue() {
        this(new Random());
    }

    public PlayQueue(Random r) {
        this.rnd = r;
    }

    public void setSongs(List<Song> list, int startIndex) {
        songs.clear();
        history.clear();
        if (list != null) songs.addAll(list);
        if (songs.isEmpty()) {
            index = -1;
        } else if (startIndex < 0 || startIndex >= songs.size()) {
            index = 0;
        } else {
            index = startIndex;
        }
    }

    public int size() {
        return songs.size();
    }

    public boolean isEmpty() {
        return songs.isEmpty();
    }

    public Song current() {
        return index >= 0 && index < songs.size() ? songs.get(index) : null;
    }

    public Song get(int i) {
        return i >= 0 && i < songs.size() ? songs.get(i) : null;
    }

    public int index() {
        return index;
    }

    public int mode() {
        return mode;
    }

    public void setMode(int m) {
        mode = (m == MODE_ONE || m == MODE_SHUFFLE) ? m : MODE_LIST;
        history.clear();
    }

    public int modeCycle() {
        setMode((mode + 1) % 3);
        return mode;
    }

    /** 选第 i 首。返回是否有效。 */
    public boolean select(int i) {
        if (i < 0 || i >= songs.size()) return false;
        index = i;
        return true;
    }

    public int nextIndex() {
        return next(false);
    }

    /**
     * @param autoAdvance true = 这首自然播完了；false = 用户点了"下一首"
     *                    单曲循环时自然播完回到自己，但手点下一首要真的走
     */
    public int next(boolean autoAdvance) {
        if (songs.isEmpty()) return -1;
        if (autoAdvance && mode == MODE_ONE && index >= 0) return index;
        if (songs.size() == 1) {
            index = 0;
            return index;
        }
        if (mode == MODE_SHUFFLE) {
            int from = index;
            int n = from;
            while (n == from) n = rnd.nextInt(songs.size());
            pushHistory(from);
            index = n;
            return index;
        }
        index = index < 0 ? 0 : (index + 1) % songs.size();
        return index;
    }

    public int prev() {
        if (songs.isEmpty()) return -1;
        if (mode == MODE_SHUFFLE && !history.isEmpty()) {
            index = history.remove(history.size() - 1);
            return index;
        }
        index = index <= 0 ? songs.size() - 1 : index - 1;
        return index;
    }

    private void pushHistory(int i) {
        if (i < 0) return;
        history.add(i);
        if (history.size() > HISTORY_MAX) history.remove(0);
    }
}
