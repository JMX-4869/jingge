package com.xiaojing.jingge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LRC 解析结果：带时间轴就同步滚，不带就整篇显示。 */
public final class Lyric {
    public static final class Line {
        public final int timeMs;
        public final String text;

        public Line(int t, String s) {
            timeMs = t;
            text = s;
        }
    }

    private static final Pattern STAMP =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    public final List<Line> lines;
    public final List<String> plain;
    public final boolean synced;

    private Lyric(List<Line> lines, List<String> plain, boolean synced) {
        this.lines = lines;
        this.plain = plain;
        this.synced = synced;
    }

    public static Lyric parse(String lrc) {
        if (lrc == null) return null;
        lrc = lrc.replace("\r", "");
        List<Line> out = new ArrayList<Line>();
        String[] rows = lrc.split("\n");
        for (String raw : rows) {
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
        if (out.isEmpty()) {
            List<String> plain = new ArrayList<String>();
            for (String raw : rows) {
                String s = raw.trim();
                if (s.length() > 0) plain.add(s);
            }
            if (plain.isEmpty()) return null;
            return new Lyric(new ArrayList<Line>(), plain, false);
        }
        Collections.sort(out, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return a.timeMs < b.timeMs ? -1 : (a.timeMs > b.timeMs ? 1 : 0);
            }
        });
        // 同一时间点只留一条
        List<Line> clean = new ArrayList<Line>();
        int last = -1;
        for (Line l : out) {
            if (l.timeMs == last) continue;
            clean.add(l);
            last = l.timeMs;
        }
        if (clean.size() < 2) {
            List<String> plain = new ArrayList<String>();
            for (Line l : clean) plain.add(l.text);
            return new Lyric(new ArrayList<Line>(), plain, false);
        }
        return new Lyric(clean, new ArrayList<String>(), true);
    }

    /** 当前时间对应的行号；不是同步歌词就返回 -1。 */
    public int indexAt(int ms) {
        if (!synced || lines.isEmpty()) return -1;
        int idx = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= ms) idx = i;
            else break;
        }
        return idx;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }
}
