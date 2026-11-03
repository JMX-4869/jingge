# -*- coding: utf-8 -*-
import io

# ── 1) Ai.java：加翻译接口 ──
p = 'src/com/xiaojing/jingge/Ai.java'
s = io.open(p, encoding='utf-8').read()

assert 'interface LinesCb' not in s
s = s.replace('''    interface ModelsCb {''', '''    interface LinesCb {
        void onLines(String[] lines, String error);
    }

    interface ModelsCb {''', 1)

marker = '    private static String shorten(String s) {'
assert marker in s
translate = '''    /** 逐行翻译；返回的数组长度和输入一致。 */
    static void translate(final Context ctx, final String lang, final String[] lines,
                          final LinesCb cb) {
        if (lines == null || lines.length == 0) {
            cb.onLines(null, "这首歌没有可翻的歌词行");
            return;
        }
        final String k = key(ctx);
        if (k.length() < 12) {
            cb.onLines(null, "还没填 API Key（设置 → AI）");
            return;
        }
        final String url = base(ctx) + "/chat/completions";
        final String m = model(ctx);
        final int n = lines.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i + 1).append(". ").append(lines[i]).append('\\n');
        final String ask = "把下面每一行歌词翻成" + lang + "：\\n" + sb.toString()
                + "\\n只输出一个 JSON 字符串数组，元素数量必须正好 " + n
                + " 个，顺序与上面一一对应；不要行号、不要引号以外的说明、不要解释。";

        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] out = null;
                String err = null;
                HttpURLConnection c = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", m);
                    body.put("temperature", 0.2);
                    body.put("max_tokens", Math.min(4000, 300 + n * 90));
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content",
                            "你是歌词翻译。只做翻译：保留语气和情绪，句子短，不合并不拆行，不加解释。"));
                    msgs.put(new JSONObject().put("role", "user").put("content", ask));
                    body.put("messages", msgs);

                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(90000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    c.setRequestProperty("Authorization", "Bearer " + k);
                    OutputStream os = c.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();

                    int code = c.getResponseCode();
                    InputStream in = code == 200 ? c.getInputStream() : c.getErrorStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[4096];
                        int it;
                        while ((it = in.read(buf)) != -1) bos.write(buf, 0, it);
                        in.close();
                    }
                    String resp = new String(bos.toByteArray(), "UTF-8");
                    if (code != 200) {
                        err = "接口 " + code + "：" + shorten(resp);
                    } else {
                        JSONObject o = new JSONObject(resp);
                        String content = o.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content");
                        String[] got = splitArray(content);
                        if (got == null) {
                            err = "模型没按格式回";
                        } else if (got.length != n) {
                            err = "行数对不上（要 " + n + " 行，回了 " + got.length + " 行）";
                        } else {
                            out = got;
                        }
                    }
                } catch (Throwable t) {
                    err = "连不上：" + t.getMessage();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String[] fOut = out;
                final String fErr = err;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onLines(fOut, fErr);
                    }
                });
            }
        }).start();
    }

    /** 从模型回复里抠出字符串数组：优先 JSON，其次按行拆。 */
    static String[] splitArray(String content) {
        if (content == null) return null;
        String s = content.trim();
        int a = s.indexOf('[');
        int b = s.lastIndexOf(']');
        if (a >= 0 && b > a) {
            String body = s.substring(a, b + 1);
            try {
                JSONArray arr = new JSONArray(body);
                String[] out = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) out[i] = arr.optString(i, "");
                return out;
            } catch (Throwable ignored) {
            }
            // 纯文本数组、每条一行
            String[] parts = body.substring(1, body.length() - 1).split("\\n");
            java.util.List<String> list = new java.util.ArrayList<String>();
            for (String p : parts) {
                String v = p.trim().replaceAll("^[,]+|[,]+$", "").trim();
                v = v.replaceAll("^\\"|\\"$", "").trim();
                v = v.replaceAll("^\\\\\\"|\\\\\\"$", "").trim();
                if (v.length() > 0) list.add(v);
            }
            if (list.size() > 0) return list.toArray(new String[0]);
        }
        String[] lines = s.replace("\\r", "").split("\\n");
        java.util.List<String> list = new java.util.ArrayList<String>();
        for (String l : lines) {
            String v = l.trim();
            if (v.length() == 0) continue;
            v = v.replaceAll("^[-*\\\\d]+[.、)]\\\\s*", "").trim();
            v = v.replaceAll("^\\"|\\"$", "").trim();
            if (v.length() > 0) list.add(v);
        }
        return list.isEmpty() ? null : list.toArray(new String[0]);
    }

'''
s = s.replace(marker, translate + marker, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ── 2) MainActivity：菜单项 + 语言选择 + 生成/保存 ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('private static final String VER = "0.34";', 'private static final String VER = "0.35";')

old = '''        box.addView(menuRow("编辑歌词", Ui.TEXT, false, new Runnable() {
            public void run() {
                editLyric(s);
            }
        }, d));'''
new = '''        box.addView(menuRow("编辑歌词", Ui.TEXT, false, new Runnable() {
            public void run() {
                editLyric(s);
            }
        }, d));
        box.addView(menuRow("AI 生成翻译歌词", Ui.TEXT, false, new Runnable() {
            public void run() {
                aiTransMenu(s);
            }
        }, d));'''
assert old in s
s = s.replace(old, new, 1)

anchor = '''    private void confirmDelete(final Song s) {'''
assert anchor in s
methods = '''    // ───────────────── 长按：AI 生成翻译歌词 ─────────────────

    private void aiTransMenu(final Song s) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.setPadding(0, dp(16), 0, dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), 0, dp(20), dp(10));
        head.addView(text("AI 生成翻译歌词", 16, Ui.TEXT, true));
        TextView sub = text("每句原文下面加一行翻译，翻成哪种语言你自己选", 12, Ui.MUTED, false);
        sub.setPadding(0, dp(6), 0, 0);
        head.addView(sub);
        box.addView(head);
        box.addView(hair());

        String[] langs = {"中文", "繁體中文", "English", "日本語", "한국어"};
        for (final String lang : langs) {
            box.addView(menuRow(lang, Ui.TEXT, false, new Runnable() {
                public void run() {
                    runAiTrans(s, lang);
                }
            }, d));
        }
        box.addView(hair());
        box.addView(menuRow("自定义语言…", Ui.MUTED, false, new Runnable() {
            public void run() {
                askLang(s);
            }
        }, d));

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void askLang(final Song s) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("翻成什么语言", 15, Ui.TEXT, true));
        final EditText e = field("", "比如 ไทย / Português / 粤语");
        box.addView(e);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);
        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);
        TextView ok = text("开始", 13, Ui.ACCENT, true);
        ok.setPadding(dp(12), dp(8), dp(12), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String lang = e.getText().toString().trim();
                d.dismiss();
                if (lang.length() == 0) {
                    Toast.makeText(MainActivity.this, "先写个语言名", Toast.LENGTH_SHORT).show();
                    return;
                }
                runAiTrans(s, lang);
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private static String stampMs(int ms) {
        if (ms < 0) ms = 0;
        return String.format("[%02d:%02d.%03d]", ms / 60000, (ms / 1000) % 60, ms % 1000);
    }

    private void runAiTrans(final Song s, final String lang) {
        if (!Ai.configured(this)) {
            Toast.makeText(this, "先填 API Key：设置 → AI", Toast.LENGTH_LONG).show();
            return;
        }
        String raw = LyricStore.read(this, s.id);
        if (raw == null) raw = LyricStore.readAuto(this, s.id);
        if (raw == null) {
            Toast.makeText(this, "这首歌还没有歌词，先点「补全」或者「编辑歌词」",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Lyric ly = Lyric.parse(raw);
        if (ly == null || !ly.synced || ly.lines.size() < 2) {
            Toast.makeText(this, "歌词没有时间轴，先补全带时间轴的歌词",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final int n = ly.lines.size();
        final int[] times = new int[n];
        final String[] src = new String[n];
        for (int i = 0; i < n; i++) {
            times[i] = ly.lines.get(i).timeMs;
            src[i] = ly.lines.get(i).text;
        }

        final Dialog pd = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("AI 正在翻译…", 15, Ui.TEXT, true));
        TextView tip = text("翻成 " + lang + " · 共 " + n + " 句，可能要十几秒", 12, Ui.MUTED, false);
        tip.setPadding(0, dp(8), 0, 2);
        box.addView(tip);
        pd.setContentView(box);
        dialogWindow(pd);
        pd.setCancelable(false);
        pd.show();

        Ai.translate(this, lang, src, new Ai.LinesCb() {
            @Override
            public void onLines(String[] lines, String error) {
                pd.dismiss();
                if (error != null || lines == null) {
                    Toast.makeText(MainActivity.this, "没翻成：" + error, Toast.LENGTH_LONG).show();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                int kept = 0;
                for (int i = 0; i < times.length; i++) {
                    String tr = i < lines.length ? lines[i] : null;
                    sb.append(stampMs(times[i])).append(src[i]).append('\\n');
                    if (tr != null) {
                        tr = tr.trim();
                        if (tr.length() > 0 && !tr.equals(src[i].trim())) {
                            sb.append(stampMs(times[i])).append(tr).append('\\n');
                            kept++;
                        }
                    }
                }
                if (kept == 0) {
                    Toast.makeText(MainActivity.this, "AI 没给出可用的翻译", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!LyricStore.save(MainActivity.this, s.id, sb.toString())) {
                    Toast.makeText(MainActivity.this, "写不进文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                LyricFinder.invalidate(s.id);
                lyricSongId = -1;
                lyricStamp = -1;
                Toast.makeText(MainActivity.this, "已生成 " + lang + " 翻译 · " + kept + " 句",
                        Toast.LENGTH_SHORT).show();
                if (svc != null && svc.currentSong() != null && svc.currentSong().id == s.id) {
                    maybeLoadLyric(svc.currentSong());
                }
            }
        });
    }

'''
s = s.replace(anchor, methods + anchor, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ── 3) Manifest ──
p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('android:versionCode="34"', 'android:versionCode="35"')
s = s.replace('android:versionName="0.34"', 'android:versionName="0.35"')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('patched')
