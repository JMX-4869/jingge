# -*- coding: utf-8 -*-
"""0.68: 加「手机内录」—— AudioPlaybackCapture + MediaProjection 授权"""
import io

# ── 1. Manifest ──
p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />''',
'''    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />''', 1)
s = s.replace('android:foregroundServiceType="mediaPlayback|microphone"',
              'android:foregroundServiceType="mediaPlayback|microphone|mediaProjection"', 1)
s = s.replace('android:versionCode="67"', 'android:versionCode="68"')
s = s.replace('android:versionName="0.67"', 'android:versionName="0.68"')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('1/3 manifest ok')

# ── 2. MusicService：内录分支 ──
p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

old = '''        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toastMain("没给麦克风权限：设置 → 应用 → 鲸歌 → 权限 → 麦克风");
            return;
        }'''
new = '''        final boolean inner = "capture".equals(Ai.prefs(this).getString("rec_src", "mic"));
        if (inner) {
            if (!Capture.ready()) {
                toastMain("内录还没授权：设置 → 听音识曲 → 授权内录");
                return;
            }
            promoteCaptureForeground();
        } else if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toastMain("没给麦克风权限：设置 → 应用 → 鲸歌 → 权限 → 麦克风");
            return;
        }'''
assert old in s
s = s.replace(old, new, 1)

old2 = '''                    int min = android.media.AudioRecord.getMinBufferSize(sr,
                            android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT);'''
new2 = '''                    int min = android.media.AudioRecord.getMinBufferSize(sr,
                            android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT);
                    if (inner) {
                        r = Capture.open(sr);
                        if (r == null) {
                            throw new IllegalStateException("内录起不来（这个播放器可能不允许被录）");
                        }
                        r.startRecording();
                        while (got < total * 2) {
                            int n = r.read(pcm, got, Math.min(4096, total * 2 - got));
                            if (n <= 0) break;
                            got += n;
                            final int secs = Math.min(7, got / 2 / sr);
                            main.post(new Runnable() {
                                public void run() {
                                    if (!recBusy) return;
                                    recStatus = "正在听（内录）… " + secs + "s";
                                    updateNotification();
                                    showRecCard();
                                }
                            });
                        }
                        throw new InnerDone();
                    }'''
assert old2 in s
s = s.replace(old2, new2, 1)

# 内录走完用异常跳出，避免复制一大段；catch 里单独处理
old3 = '''                } catch (Throwable t) {
                    got = 0;
                } finally {'''
new3 = '''                } catch (InnerDone ignored) {
                    // 内录已经录完，正常往下走
                } catch (Throwable t) {
                    got = 0;
                    if (inner) lastErr = String.valueOf(t.getMessage());
                } finally {'''
assert old3 in s
s = s.replace(old3, new3, 1)

# 内录失败时把原因说出来（got<=0 分支已有；这里补一句 inner 场景）
old4 = '''                if (got <= 0) {
                    finishRec(null, "录不到声音（麦克风被别的应用占着？）", wasPlaying);
                    return;
                }'''
new4 = '''                if (got <= 0) {
                    finishRec(null, inner
                            ? ("内录没拿到声音" + (lastErr == null ? "" : "：" + lastErr))
                            : "录不到声音（麦克风被别的应用占着？）", wasPlaying);
                    return;
                }'''
assert old4 in s
s = s.replace(old4, new4, 1)

# 内录专用前台类型 + 内部异常类
old5 = '''    private void promoteMicForeground() {'''
new5 = '''    /** 内录要带 mediaProjection 类型的前台服务（Android 14 硬要求） */
    private void promoteCaptureForeground() {
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
        if (Build.VERSION.SDK_INT >= 30) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        if (Build.VERSION.SDK_INT >= 29) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        try {
            startForeground(NOTIF_ID, buildNotification(), type);
        } catch (Throwable t) {
            lastErr = "内录前台：" + t.getMessage();
        }
    }

    private static final class InnerDone extends Exception {
    }

    private void promoteMicForeground() {'''
assert old5 in s
s = s.replace(old5, new5, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('2/3 service ok')

# ── 3. MainActivity：来源 chips + 授权按钮 + 回调 ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('private static final String VER = "0.67";', 'private static final String VER = "0.68";', 1)
s = s.replace('''    private static final int REQ_COVER = 51;''',
              '''    private static final int REQ_COVER = 51;
    private static final int REQ_CAPTURE = 53;''', 1)

# 设置里加「听音来源」
old6 = '''        TextView elabel = text("识曲入口", 11, Ui.DIM, false);'''
new6 = '''        final String src = Ai.prefs(this).getString("rec_src", "mic");
        TextView slabel = text("听音来源", 11, Ui.DIM, false);
        slabel.setPadding(0, dp(12), 0, dp(6));
        box.addView(slabel);

        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(srow);
        srow.addView(srcChip(d, "外放（麦克风）", "mic", src));
        srow.addView(srcChip(d, "手机内录", "capture", src));

        if ("capture".equals(src)) {
            TextView capState = text(Capture.ready()
                            ? "内录已授权，可以用了"
                            : (Capture.supported() ? "还没授权 → 点这里让系统确认"
                            : "这个系统（Android 9 及以下）不支持内录"),
                    11, Capture.ready() ? Ui.ACCENT : Ui.MUTED, false);
            capState.setPadding(0, dp(8), 0, dp(4));
            capState.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!Capture.supported()) {
                        Toast.makeText(MainActivity.this, "系统太老，没有内录接口",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Capture.ask(MainActivity.this, REQ_CAPTURE);
                }
            });
            box.addView(capState);

            TextView capNote = text("系统会弹「开始录制或投放」——同意后，"
                    + "只有允许被录的 App 的声音才抓得到；戴耳机的歌也能认。", 10, Ui.DIM, false);
            capNote.setLineSpacing(dp(3), 1f);
            box.addView(capNote);
        }

        TextView elabel = text("识曲入口", 11, Ui.DIM, false);'''
assert old6 in s
s = s.replace(old6, new6, 1)

# srcChip
old7 = '''    private View entryChip(final Dialog d, final String name, final String id, String cur) {'''
new7 = '''    private View srcChip(final Dialog d, final String name, final String id, String cur) {
        final boolean on = id.equals(cur);
        TextView t = text(name, 13, on ? Ui.ACCENT : Ui.MUTED, on);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(Ui.roundStroke(0, on ? Ui.ACCENT : Ui.LINE, dp(10), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ai.prefs(MainActivity.this).edit().putString("rec_src", id).apply();
                d.dismiss();
                if ("capture".equals(id)) Capture.ask(MainActivity.this, REQ_CAPTURE);
                showRecSettings();
            }
        });
        return t;
    }

    private View entryChip(final Dialog d, final String name, final String id, String cur) {'''
assert old7 in s
s = s.replace(old7, new7, 1)

# 授权回调
old8 = '''    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);'''
new8 = '''    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CAPTURE) {
            boolean ok = res == RESULT_OK && Capture.take(this, res, data);
            Toast.makeText(this, ok ? "内录已授权，可以点识曲了"
                            : "没同意，内录用不了（还是用外放吧）",
                    Toast.LENGTH_LONG).show();
            return;
        }'''
assert old8 in s
s = s.replace(old8, new8, 1)

# 体检里补一行听音来源
old9 = '''    /** 通知栏体检：说不出原因就别让用户瞎试 */'''
new9 = '''    /** 通知栏体检：说不出原因就别让用户瞎试 */'''
s = s.replace(old9, new9, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('3/3 activity ok')
