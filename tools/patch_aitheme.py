# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- Ai：通用一问一答 ----------
p = 'src/com/xiaojing/jingge/Ai.java'
s = rd(p)
if 'static void ask(' not in s:
    anchor = '''    static void generate(final Context ctx, final String title, final String artist,'''
    ask = '''    /** 通用一问一答，给"AI 造主题"用。 */
    static void ask(final Context ctx, final String system, final String user,
                    final double temp, final Callback cb) {
        final String k = key(ctx);
        if (k.length() < 12) {
            cb.onText(null, "还没填 API Key");
            return;
        }
        final String url = base(ctx) + "/chat/completions";
        final String m = model(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = null;
                String err = null;
                HttpURLConnection c = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", m);
                    body.put("temperature", temp);
                    body.put("max_tokens", 500);
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content", system));
                    msgs.put(new JSONObject().put("role", "user").put("content", user));
                    body.put("messages", msgs);

                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(60000);
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
                        int n;
                        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                        in.close();
                    }
                    String resp = new String(bos.toByteArray(), "UTF-8");
                    if (code != 200) {
                        err = "接口 " + code + "：" + shorten(resp);
                    } else {
                        JSONObject o = new JSONObject(resp);
                        out = o.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content");
                        if (out == null || out.trim().length() == 0) err = "模型回了空内容";
                    }
                } catch (Throwable t) {
                    err = "连不上：" + t.getMessage();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String fOut = out;
                final String fErr = err;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onText(fOut, fErr);
                    }
                });
            }
        }).start();
    }

'''
    assert anchor in s
    s = s.replace(anchor, ask + anchor, 1)
    wr(p, s)

# ---------- MainActivity：入口 + 流程 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
if 'showAiTheme' not in s:
    s = s.replace('private static final String VER = "0.28";',
                  'private static final String VER = "0.29";')

    # 设置里加入口
    old = '''        TextView mine = text("自制主题", 12, Ui.ACCENT, false);'''
    new = '''        TextView aiTheme = text("AI 造主题", 12, Ui.ACCENT, false);
        aiTheme.setPadding(dp(6), dp(8), dp(16), dp(8));
        aiTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showAiTheme();
            }
        });
        trow.addView(aiTheme);

        TextView mine = text("自制主题", 12, Ui.ACCENT, false);'''
    assert old in s
    s = s.replace(old, new, 1)

    old = '''    private void showThemeEditor() {'''
    new = '''    private void showAiTheme() {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("AI 造主题", 15, Ui.TEXT, true));
        TextView tip = text("一句话描述想要的配色；生成后看样张，不满意继续提意见", 11, Ui.DIM, false);
        tip.setPadding(0, dp(4), 0, dp(10));
        box.addView(tip);

        final EditText desc = field("", "比如：深夜电台，暖橘色，安静一点");
        box.addView(desc);

        final LinearLayout previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        box.addView(previewBox, plp);

        final EditText fb = field("", "改哪：比如主色太扎眼 / 再暗一点");
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(8);
        fb.setVisibility(View.GONE);
        box.addView(fb, flp);

        final Theme[] cur = new Theme[1];
        final TextView status = text("", 11, Ui.DIM, false);
        status.setPadding(0, dp(6), 0, 0);
        box.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(8), 0, 0);
        box.addView(row);

        final TextView save = text("保存并应用", 13, Ui.MUTED, false);
        save.setPadding(dp(10), dp(8), dp(10), dp(8));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cur[0] == null) {
                    Toast.makeText(MainActivity.this, "先生成一版", Toast.LENGTH_SHORT).show();
                    return;
                }
                ThemeStore.save(MainActivity.this, cur[0]);
                ThemeStore.setActive(MainActivity.this, cur[0].id);
                d.dismiss();
                recreate();
            }
        });
        row.addView(save);

        final TextView gen = text("生成", 13, Ui.ACCENT, true);
        gen.setPadding(dp(14), dp(8), dp(14), dp(8));
        gen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = desc.getText().toString().trim();
                if (text.length() < 2) {
                    Toast.makeText(MainActivity.this, "先写一句描述", Toast.LENGTH_SHORT).show();
                    return;
                }
                String feel = fb.getVisibility() == View.VISIBLE
                        ? fb.getText().toString().trim() : "";
                gen.setText("生成中…");
                gen.setEnabled(false);
                status.setText("");
                AiTheme.generate(MainActivity.this, text,
                        cur[0] == null ? null : AiTheme.json(cur[0]), feel,
                        new Ai.Callback() {
                            @Override
                            public void onText(String out, String error) {
                                gen.setText(cur[0] == null ? "生成" : "改一版");
                                gen.setEnabled(true);
                                if (error != null) {
                                    status.setText(error);
                                    return;
                                }
                                Theme t = AiTheme.parse(out);
                                if (t == null) {
                                    status.setText("模型没给出能用的配色，再试一次");
                                    return;
                                }
                                cur[0] = t;
                                previewBox.removeAllViews();
                                previewBox.addView(ThemePreview.build(MainActivity.this, t));
                                fb.setVisibility(View.VISIBLE);
                                save.setTextColor(Ui.ACCENT);
                                status.setText(t.name + " · " + ThemeStore.hex(t.accent));
                                gen.setText("改一版");
                            }
                        });
            }
        });
        row.addView(gen);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        d.setContentView(sv);
        dialogWindowTall(d);
        d.show();
    }

    private void showThemeEditor() {'''
    assert old in s
    s = s.replace(old, new, 1)
    wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="28"', 'android:versionCode="29"').replace('android:versionName="0.28"', 'android:versionName="0.29"')
wr(p, s)
print('ok')
