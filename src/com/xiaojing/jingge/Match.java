package com.xiaojing.jingge;

import java.util.Locale;
import java.util.regex.Pattern;

/** 歌词匹配打分：标题、歌手、时长三项，避免拿成翻唱或同名歌。纯逻辑，可单测。 */
public final class Match {
    private static final Pattern BRACKETS =
            Pattern.compile("\\(.*?\\)|（.*?）|\\[.*?\\]|【.*?】");

    private Match() {
    }

    /**
     * @return -1 = 标题都对不上（直接放弃）；否则分越高越像。
     */
    public static int score(String title, String artist, int durSec, Song s) {
        String a = norm(title), b = norm(s.title);
        if (a.length() == 0 || b.length() == 0) return -1;
        int sc;
        if (a.equals(b)) sc = 3;
        else if (a.contains(b) || b.contains(a)) sc = 2;
        else return -1;

        String x = norm(artist), y = norm(s.artist);
        if (x.length() > 0 && y.length() > 0 && (x.contains(y) || y.contains(x))) sc += 2;

        if (durSec > 0 && s.durationMs > 0) {
            long d = Math.abs(durSec * 1000L - s.durationMs);
            if (d < 4000) sc += 2;
            else if (d < 12000) sc += 1;
        }
        return sc;
    }

    /** 小写、去括号内容、只留字母数字（中文算字母）。 */
    public static String norm(String s) {
        if (s == null) return "";
        String t = BRACKETS.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}
