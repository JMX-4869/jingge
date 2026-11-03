# -*- coding: utf-8 -*-
"""0.70: 授权改成「先存凭证，服务里先起前台再 getMediaProjection」（Android 14 要求的顺序）"""
import io

# ── 1. Capture：存凭证 + 延后取 ──
p = 'src/com/xiaojing/jingge/Capture.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('''    static MediaProjection projection = null;''',
'''    static MediaProjection projection = null;
    private static int pendingCode = 0;
    private static Intent pendingData = null;

    /** Activity 拿到系统返回后先存着，等服务的带类型前台起来再取 */
    static void store(int resultCode, Intent data) {
        pendingCode = resultCode;
        pendingData = data == null ? null : new Intent(data);
    }

    static boolean hasPending() {
        return pendingCode != 0 && pendingData != null;
    }

    /** 必须在 startForeground(含 mediaProjection 类型) 之后调用 */
    static boolean takePending(Context c) {
        if (!supported() || !hasPending()) return false;
        int code = pendingCode;
        Intent data = pendingData;
        pendingCode = 0;
        pendingData = null;
        return take(c, code, data);
    }''', 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('capture ok')

# ── 2. MusicService：capOn 时先起前台、再取授权 ──
p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

old = '''        final String src = Ai.prefs(this).getString("rec_src", "mic");
        boolean capOn = "capture".equals(src) || "mix".equals(src);
        final boolean micOn = !"capture".equals(src);
        if (capOn && !Capture.ready()) {
            if (!micOn) {
                toastMain("内录还没授权：设置 → 听音识曲 → 授权内录");
                return;
            }
            capOn = false;   // 想内外一起但没授权，就先用麦克风
            toastMain("内录没授权，这次只听麦克风；要到设置里点一下授权");
        }'''
new = '''        final String src = Ai.prefs(this).getString("rec_src", "mic");
        boolean capOn = "capture".equals(src) || "mix".equals(src);
        final boolean micOn = !"capture".equals(src);
        if (capOn) {
            // Android 14 要求：先起带 mediaProjection 类型的前台服务，再取 MediaProjection
            promoteCaptureForeground();
            if (!Capture.ready() && Capture.hasPending()) Capture.takePending(this);
        }
        if (capOn && !Capture.ready()) {
            if (!micOn) {
                toastMain("内录没授权：设置 → 听音识曲 → 点「还没授权」那里让系统确认");
                return;
            }
            capOn = false;   // 想内外一起但没授权，就先用麦克风
            toastMain("内录没授权，这次只听麦克风；要到设置里点一下授权");
        }'''
assert old in s
s = s.replace(old, new, 1)

# 后面这行别再重复 promote（已提前做）
old2 = '''        recStatus = "正在听… 0s";
        if (useCap) promoteCaptureForeground();
        else promoteMicForeground();'''
new2 = '''        recStatus = "正在听… 0s";
        if (!useCap) promoteMicForeground();'''
assert old2 in s
s = s.replace(old2, new2, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('service ok')

# ── 3. MainActivity：授权回调只存不取 + 先弹说明 ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('private static final String VER = "0.69";', 'private static final String VER = "0.70";', 1)

old3 = '''        if (req == REQ_CAPTURE) {
            boolean ok = res == RESULT_OK && Capture.take(this, res, data);
            Toast.makeText(this, ok ? "内录已授权，可以点识曲了"
                            : "没同意，内录用不了（还是用外放吧）",
                    Toast.LENGTH_LONG).show();
            return;
        }'''
new3 = '''        if (req == REQ_CAPTURE) {
            if (res == RESULT_OK && data != null) {
                Capture.store(res, data);
                Toast.makeText(this, "授权拿到了；点「开始识别」时才会真正抓声音",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "没同意，内录用不了（还能用外放）",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }'''
assert old3 in s
s = s.replace(old3, new3, 1)

# 「点这里让系统确认」先弹说明
old4 = '''            capState.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!Capture.supported()) {
                        Toast.makeText(MainActivity.this, "系统太老，没有内录接口",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Capture.ask(MainActivity.this, REQ_CAPTURE);
                }
            });'''
new4 = '''            capState.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!Capture.supported()) {
                        Toast.makeText(MainActivity.this, "系统太老，没有内录接口",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showCaptureIntro(d);
                }
            });'''
assert old4 in s
s = s.replace(old4, new4, 1)

# 说明框
old5 = '''    private View srcChip(final Dialog d, final String name, final String id, String cur) {'''
new5 = '''    /** 先说清楚为什么要点，再弹系统的录屏授权框 */
    private void showCaptureIntro(final Dialog parent) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("要开一次录屏授权", 15, Ui.TEXT, true));
        TextView t = text("系统只允许「看过屏幕」的 App 去听内部声音，所以会弹 "
                + "「开始录制或投放屏幕」。\\n\\n"
                + "它听起来像录屏，但鲸歌只拿走音频那一路，不截屏、不上传画面。\\n\\n"
                + "点「立即开始」，再点「开始识别」就行了。", 12, Ui.MUTED, false);
        t.setPadding(0, dp(10), 0, dp(12));
        t.setLineSpacing(dp(3), 1f);
        box.addView(t);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView no = text("算了", 13, Ui.MUTED, false);
        no.setPadding(dp(12), dp(8), dp(16), dp(8));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(no);

        TextView go = text("去授权", 13, Ui.ACCENT, true);
        go.setPadding(dp(12), dp(8), dp(12), dp(8));
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                Capture.ask(MainActivity.this, REQ_CAPTURE);
            }
        });
        row.addView(go);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private View srcChip(final Dialog d, final String name, final String id, String cur) {'''
assert old5 in s
s = s.replace(old5, new5, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('activity ok')

def rw(path, pairs):
    x = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in x, a
        x = x.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(x)

rw('AndroidManifest.xml',
   [('android:versionCode="69"', 'android:versionCode="70"'),
    ('android:versionName="0.69"', 'android:versionName="0.70"')])
print('version ok')
