import com.xiaojing.jingge.Match;
import com.xiaojing.jingge.Song;

/** 歌词匹配打分自测。 */
public class MatchTest {
    static int pass, fail;

    static void eq(String what, Object got, Object want) {
        if (want == null ? got == null : want.equals(got)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + "  got=" + got + " want=" + want);
        }
    }

    static Song song(String title, String artist, long ms) {
        return new Song(1, title, artist, "album", "content://x/1", ms);
    }

    public static void main(String[] args) {
        eq("norm brackets", Match.norm("晴天 (Live)"), "晴天");
        eq("norm fullwidth", Match.norm("晴天（伴奏）"), "晴天");
        eq("norm spaces", Match.norm("Jay Chou"), "jaychou");
        eq("norm null", Match.norm(null), "");

        Song local = song("晴天", "周杰伦", 269000);

        // 原版：标题、歌手、时长全中
        int perfect = Match.score("晴天", "周杰伦", 269, local);
        // 翻唱：标题被去括号后相同，歌手不匹配，时长差 9 秒
        int cover = Match.score("晴天 (深情版)", "Lucky小", 278, local);
        // 英文名歌手：标题、时长对，歌手对不上
        int jay = Match.score("晴天", "Jay", 269, local);
        // 同名不同歌：标题对，歌手错，时长差很远
        int other = Match.score("晴天", "别的人", 180, local);
        // 完全不沾
        int none = Match.score("七里香", "周杰伦", 299, local);

        eq("perfect", perfect, 7);
        eq("cover", cover, 4);
        eq("jay", jay, 5);
        eq("other", other, 3);
        eq("none", none, -1);
        eq("cover loses", cover < perfect, true);
        eq("jay loses", jay < perfect, true);

        System.out.println(fail == 0 ? ("ALL PASS (" + pass + ")") : ("FAILED " + fail + "/" + (pass + fail)));
        if (fail > 0) System.exit(1);
    }
}
