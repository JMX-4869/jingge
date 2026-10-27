import com.xiaojing.jingge.PlayQueue;
import com.xiaojing.jingge.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 纯 Java 队列逻辑自测（不碰 Android）。 */
public class QueueTest {
    static int pass, fail;

    static void eq(String what, Object got, Object want) {
        if (want == null ? got == null : want.equals(got)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + "  got=" + got + " want=" + want);
        }
    }

    static List<Song> songs(int n) {
        List<Song> l = new ArrayList<Song>();
        for (int i = 0; i < n; i++) {
            l.add(new Song(i, "s" + i, "a", "b", "content://x/" + i, 60000));
        }
        return l;
    }

    public static void main(String[] args) {
        // 1. 空队列
        PlayQueue q = new PlayQueue(new Random(7));
        eq("empty size", q.size(), 0);
        eq("empty current", q.current(), null);
        eq("empty next", q.next(false), -1);
        eq("empty prev", q.prev(), -1);

        // 2. 列表循环
        q.setSongs(songs(3), 2);
        eq("list idx", q.index(), 2);
        eq("list next wraps", q.next(false), 0);
        eq("list prev wraps", q.prev(), 2);
        eq("list prev 1", q.prev(), 1);

        // 3. 单曲循环：自然播完停自己，手点下一首要走
        q.setSongs(songs(3), 0);
        q.setMode(PlayQueue.MODE_ONE);
        eq("one auto stays", q.next(true), 0);
        eq("one manual next", q.next(false), 1);
        eq("one mode kept", q.mode(), PlayQueue.MODE_ONE);

        // 4. 随机：不连着同一首；上一首能回到来的地方
        q.setSongs(songs(6), 0);
        q.setMode(PlayQueue.MODE_SHUFFLE);
        int from = q.index();
        int n1 = q.next(false);
        if (n1 != from) pass++; else { fail++; System.out.println("FAIL shuffle repeats"); }
        int n2 = q.next(false);
        if (n2 != n1) pass++; else { fail++; System.out.println("FAIL shuffle repeats 2"); }
        eq("shuffle prev -> n1", q.prev(), n1);
        eq("shuffle prev -> from", q.prev(), from);

        // 5. 切模式保留当前曲目与索引
        q.setSongs(songs(4), 3);
        q.setMode(PlayQueue.MODE_ONE);
        eq("mode keep index", q.index(), 3);
        eq("mode keep song", q.current().title, "s3");
        eq("cycle one->shuffle", q.modeCycle(), PlayQueue.MODE_SHUFFLE);
        eq("cycle shuffle->list", q.modeCycle(), PlayQueue.MODE_LIST);
        eq("cycle list->one", q.modeCycle(), PlayQueue.MODE_ONE);

        // 6. 越界与单曲
        eq("select oob", q.select(9), false);
        q.setSongs(songs(1), 0);
        q.setMode(PlayQueue.MODE_SHUFFLE);
        eq("single shuffle next", q.next(false), 0);
        eq("single shuffle prev", q.prev(), 0);
        q.setSongs(songs(3), 99);
        eq("bad start clamps", q.index(), 0);

        System.out.println(fail == 0 ? ("ALL PASS (" + pass + ")") : ("FAILED " + fail + "/" + (pass + fail)));
        if (fail > 0) System.exit(1);
    }
}
