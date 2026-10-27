package com.xiaojing.jingge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LRC 解析结果：带时间轴就同步滚，不带就整篇显示。译文挂在每一行下面。 */
public final class Lyric {
    public static final class Line {
        public final int timeMs;
        public final String text;
        public final String trans;

        public Line(int t, String s) {
            this(t, s, null);
        }

        public Line(int t, String s, String tr) {
            timeMs = t;
            text = s;
            trans = tr;
        }
    }

    private static final Pattern STAMP =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    public final List<Line> lines;
    public final List<String> plain;
    public final boolean synced;
    /** 有没有任何一句带译文 */
    public final boolean hasTrans;

    private Lyric(List<Line> lines, List<String> plain, boolean synced, boolean hasTrans) {
        this.lines = lines;
        this.plain = plain;
        this.synced = synced;
        this.hasTrans = hasTrans;
    }

    public static Lyric parse(String lrc) {
        return parse(lrc, null);
    }

    /** lrc 是原文，transLrc 是同一首歌的翻译轨（网易云 tlyric / QQ trans）。 */
    public static Lyric parse(String lrc, String transLrc) {
        if (lrc == null) return null;
        List<Line> main = parseLines(lrc);
        if (main.isEmpty()) {
            List<String> plain = new ArrayList<String>();
            for (String raw : lrc.replace("\r", "").split("\n")) {
                String s = raw.trim();
                if (s.length() > 0) plain.add(s);
            }
            if (plain.isEmpty()) return null;
            return new Lyric(new ArrayList<Line>(), plain, false, false);
        }
        Collections.sort(main, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return a.timeMs < b.timeMs ? -1 : (a.timeMs > b.timeMs ? 1 : 0);
            }
        });

        // 同一个时间点的第二条，就是这句的译文
        List<Line> clean = new ArrayList<Line>();
        for (Line l : main) {
            if (!clean.isEmpty() && clean.get(clean.size() - 1).timeMs == l.timeMs) {
                Line prev = clean.get(clean.size() - 1);
                clean.set(clean.size() - 1, new Line(prev.timeMs, prev.text, l.text));
                continue;
            }
            clean.add(l);
        }

        if (clean.size() < 2) {
            List<String> plain = new ArrayList<String>();
            for (String raw : lrc.replace("\r", "").split("\n")) {
                String line = STAMP.matcher(raw).replaceAll("").trim();
                if (line.length() > 0) plain.add(line);
            }
            if (plain.isEmpty()) return null;
            return new Lyric(new ArrayList<Line>(), plain, false, false);
        }

        if (transLrc != null && transLrc.trim().length() > 0) {
            Map<Integer, String> map = new HashMap<Integer, String>();
            for (Line t : parseLines(transLrc)) {
                if (!map.containsKey(t.timeMs)) map.put(t.timeMs, t.text);
            }
            for (int i = 0; i < clean.size(); i++) {
                Line l = clean.get(i);
                String tr = l.trans != null ? l.trans : map.get(l.timeMs);
                if (tr != null) clean.set(i, new Line(l.timeMs, l.text, tr));
            }
        }

        boolean any = false;
        for (Line l : clean) {
            if (l.trans != null && l.trans.length() > 0) {
                any = true;
                break;
            }
        }
        return new Lyric(clean, new ArrayList<String>(), true, any);
    }

    /** 只拆时间戳，不做配对。 */
    private static List<Line> parseLines(String lrc) {
        List<Line> out = new ArrayList<Line>();
        for (String raw : lrc.replace("\r", "").split("\n")) {
            Matcher m = STAMP.matcher(raw);
            List<Integer> times = new ArrayList<Integer>();
            while (m.find()) {
                int min = safeInt(m.group(1));
                int sec = safeInt(m.group(2));
                String frac = m.group(3);
                int ms = 0;
                if (frac != null) {
                    if (frac.length() == 1) ms = safeInt(frac) * 100;
                    else if (frac.length() == 2) ms = safeInt(frac) * 10;
                    else ms = safeInt(frac);
                }
                times.add(min * 60000 + sec * 1000 + ms);
            }
            String text = STAMP.matcher(raw).replaceAll("").trim();
            if (times.isEmpty() || text.length() == 0) continue;
            for (int t : times) out.add(new Line(t, text));
        }
        return out;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 写回成 LRC（带译文的两行同一时间戳）。 */
    public String toLrc() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            sb.append(stamp(l.timeMs)).append(l.text).append('\n');
            if (l.trans != null && l.trans.length() > 0) {
                sb.append(stamp(l.timeMs)).append(l.trans).append('\n');
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

    /** 当前时间对应的第几行。 */
    public int indexAt(int ms) {
        if (lines.isEmpty()) return -1;
        int idx = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= ms) idx = i;
            else break;
        }
        return idx;
    }
}
