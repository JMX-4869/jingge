import com.xiaojing.jingge.Lyric;

/** LRC 解析自测（纯 Java）。 */
public class LyricTest {
    static int pass, fail;

    static void eq(String what, Object got, Object want) {
        if (want == null ? got == null : want.equals(got)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + "  got=" + got + " want=" + want);
        }
    }

    public static void main(String[] args) {
        // 标准两行
        Lyric a = Lyric.parse("[00:12.34]第一句\n[00:15.00]第二句");
        eq("a synced", a != null && a.synced, true);
        eq("a count", a.lines.size(), 2);
        eq("a t0", a.lines.get(0).timeMs, 12340);
        eq("a t1", a.lines.get(1).timeMs, 15000);

        // 一行多个时间标签
        Lyric b = Lyric.parse("[00:01.00][00:05.00]副歌");
        eq("b count", b.lines.size(), 2);
        eq("b t1", b.lines.get(1).timeMs, 5000);

        // 无小数
        Lyric c = Lyric.parse("[01:02]词\n[01:03]句");
        eq("c t", c.lines.get(0).timeMs, 62000);

        // 三位毫秒
        Lyric d = Lyric.parse("[01:02.345]词\n[01:03.000]句");
        eq("d t", d.lines.get(0).timeMs, 62345);

        // 元数据 + 空行被跳过
        Lyric e = Lyric.parse("[ti:晴天]\n[ar:周杰伦]\n[00:01.00]词\n\n[00:02.00]句");
        eq("e count", e.lines.size(), 2);
        eq("e synced", e.synced, true);

        // 纯文本 → 不同步
        Lyric f = Lyric.parse("第一行\n第二行\n");
        eq("f synced", f.synced, false);
        eq("f plain", f.plain.size(), 2);

        // 空
        eq("g null", Lyric.parse(""), null);
        eq("h null", Lyric.parse(null), null);

        // 乱序输入要按时间排好
        Lyric i = Lyric.parse("[00:09.00]后\n[00:03.00]前");
        eq("i t0", i.lines.get(0).timeMs, 3000);
        eq("i t1", i.lines.get(1).timeMs, 9000);

        // 只有一行时间轴 → 退化成纯文本
        Lyric j = Lyric.parse("[00:09.00]只有一句");
        eq("j synced", j.synced, false);

        System.out.println(fail == 0 ? ("ALL PASS (" + pass + ")") : ("FAILED " + fail + "/" + (pass + fail)));
        if (fail > 0) System.exit(1);
    }
}
