# -*- coding: utf-8 -*-
"""0.56：悬浮球缩小到 42dp，松手自动吸边并收进侧边，点一下再露出来"""
import io

p = 'src/com/xiaojing/jingge/BallService.java'
s = io.open(p, encoding='utf-8').read()

pairs = [
# 尺寸
("""            size = Ui.dp(this, 56);
            targetSize = Ui.dp(this, 82);""",
 """            size = Ui.dp(this, 42);
            targetSize = Ui.dp(this, 74);"""),

# 字段
("""    private float downRawX, downRawY;""",
 """    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean hidden;          // 收进侧边了
    private boolean justRevealed;    // 这一下触摸只是把它拉出来，不算点击
    private Runnable hider;

    private float downRawX, downRawY;"""),

# glyph 字号
("""        glyph.setTextSize(24);""",
 """        glyph.setTextSize(17);"""),
("""        bg.setStroke(Ui.dp(this, 1.5f), 0xFF2FE0C8);""",
 """        bg.setStroke(Ui.dp(this, 1.2f), 0xFF2FE0C8);"""),

# 贴边/露出/自动收
("""    private void savePos() {""",
 """    private boolean onLeftSide() {
        return ballLp.x + size / 2 < screenW / 2;
    }

    private int shownX() {
        return onLeftSide() ? 0 : screenW - size;
    }

    /** 一帧一帧挪，别瞬移 */
    private void slideTo(final int tx) {
        if (ball == null || ballLp == null) return;
        final int from = ballLp.x, steps = 7;
        if (from == tx) return;
        for (int i = 1; i <= steps; i++) {
            final int k = i;
            h.postDelayed(new Runnable() {
                public void run() {
                    if (ball == null || ballLp == null) return;
                    ballLp.x = from + (tx - from) * k / steps;
                    try {
                        wm.updateViewLayout(ball, ballLp);
                    } catch (Throwable ignored) {
                    }
                }
            }, i * 16);
        }
    }

    /** 收进最近的那条竖边，只露三分之一 */
    private void hideToEdge() {
        if (ballLp == null || ball == null || dragging) return;
        hidden = true;
        slideTo(onLeftSide() ? -(int) (size * 0.62f) : screenW - (int) (size * 0.38f));
    }

    private void reveal() {
        if (ballLp == null) return;
        hidden = false;
        slideTo(shownX());
        scheduleHide();
    }

    private void scheduleHide() {
        if (hider == null) {
            hider = new Runnable() {
                public void run() {
                    if (!dragging) hideToEdge();
                }
            };
        }
        h.removeCallbacks(hider);
        h.postDelayed(hider, 3500);
    }

    private void savePos() {"""),

# 保存的是露出时的位置，免得下次启动读到负数
("""        Ai.prefs(this).edit()
                .putFloat("ball_x", ballLp.x)
                .putFloat("ball_y", ballLp.y)
                .apply();""",
 """        Ai.prefs(this).edit()
                .putFloat("ball_x", hidden ? shownX() : ballLp.x)
                .putFloat("ball_y", ballLp.y)
                .apply();"""),

# 触摸：隐藏态先拉出来；松手吸边
("""                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = ballLp.x;
                        startY = ballLp.y;
                        dragging = false;
                        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).start();
                        return true;""",
 """                    case MotionEvent.ACTION_DOWN:
                        if (hidden) {
                            justRevealed = true;
                            reveal();
                            return true;
                        }
                        justRevealed = false;
                        if (hider != null) h.removeCallbacks(hider);
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = ballLp.x;
                        startY = ballLp.y;
                        dragging = false;
                        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).start();
                        return true;"""),

("""                            savePos();
                        } else {
                            openRecognize();
                        }
                        dragging = false;
                        over = false;
                        return true;""",
 """                            savePos();
                            hideToEdge();
                        } else if (justRevealed) {
                            justRevealed = false;
                        } else {
                            openRecognize();
                            scheduleHide();
                        }
                        dragging = false;
                        over = false;
                        return true;"""),

# 横竖屏切换后按新宽度重新贴边
("""        if (target != null && targetLp != null) {
            targetLp.x = (screenW - targetSize) / 2;""",
 """        if (ballLp != null && ball != null && hidden) {
            ballLp.x = onLeftSide() ? -(int) (size * 0.62f) : screenW - (int) (size * 0.38f);
            try {
                wm.updateViewLayout(ball, ballLp);
            } catch (Throwable ignored) {
            }
        }
        if (target != null && targetLp != null) {
            targetLp.x = (screenW - targetSize) / 2;"""),
]

for old, new in pairs:
    assert old in s, 'missing:\n' + old[:90]
    s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 版本号
def bump(path, pairs):
    t = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in t, a
        t = t.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(t)

bump('src/com/xiaojing/jingge/MainActivity.java',
     [('private static final String VER = "0.55";', 'private static final String VER = "0.56";')])
bump('AndroidManifest.xml',
     [('android:versionCode="55"', 'android:versionCode="56"'),
      ('android:versionName="0.55"', 'android:versionName="0.56"')])
print('patched 0.56')
