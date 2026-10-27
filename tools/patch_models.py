# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ================= Ai：拉模型列表 =================
p = 'src/com/xiaojing/jingge/Ai.java'
s = rd(p)
old = '''    static final String DEFAULT_BASE = "https://api.deepseek.com/v1";'''
new = '''    interface ModelsCb {
        void onModels(java.util.List<String> models, String error);
    }

    static final String DEFAULT_BASE = "https://api.deepseek.com/v1";'''
assert old in s
s = s.replace(old, new, 1)

old = '''    static String base(Context c) {'''
new = '''    /** GET {base}/models，拉服务端实际的模型名。 */
    static void listModels(final Context ctx, final ModelsCb cb) {
        final String k = key(ctx);
        if (k == null || k.trim().length() < 8) {
            cb.onModels(null, "还没填 API Key");
            return;
        }
        final String url = base(ctx) + "/models";
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                java.util.List<String> out = null;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("Authorization", "Bearer " + k);
                    c.setRequestProperty("Accept", "application/json");
                    int code = c.getResponseCode();
                    InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                        in.close();
                    }
                    String body = new String(bos.toByteArray(), "UTF-8");
                    if (code < 200 || code >= 300) {
                        err = "HTTP " + code;
                    } else {
                        JSONArray arr = new JSONObject(body).optJSONArray("data");
                        if (arr == null) {
                            err = "返回里没有 data";
                        } else {
                            out = new java.util.ArrayList<String>();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.optJSONObject(i);
                                String id = o == null ? "" : o.optString("id", "");
                                if (id.length() > 0) out.add(id);
                            }
                        }
                    }
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String fe = err;
                final java.util.List<String> fo = out;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onModels(fo, fe);
                    }
                });
            }
        }).start();
    }

    static String base(Context c) {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ================= ModelPicker：设置里用的那个按钮 =================
p = 'src/com/xiaojing/jingge/ModelPicker.java'
s = rd(p)
old = '''    static void show(final Activity act, final Runnable after) {'''
new = '''    static String label(Activity a) {
        return "模型：" + Ai.model(a) + "  ▾";
    }

    /** 设置页里的那个「模型」行：点开拉列表，也能手动填。 */
    static TextView button(final Activity act) {
        final TextView t = new TextView(act);
        t.setText(label(act));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(Ui.TEXT);
        t.setPadding(Ui.dp(act, 10), Ui.dp(act, 11), Ui.dp(act, 10), Ui.dp(act, 11));
        t.setBackground(Ui.round(Ui.PANEL_UP, Ui.dp(act, 8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(act, new Runnable() {
                    @Override
                    public void run() {
                        t.setText(label(act));
                    }
                });
            }
        });
        return t;
    }

    static void show(final Activity act, final Runnable after) {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ================= MainActivity =================
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.18";', 'private static final String VER = "0.19";')
old = '''        final EditText model = field(Ai.model(this), "模型");
        final EditText key = field(Ai.key(this), "API Key（只存本机）");
        box.addView(base);
        box.addView(model);
        box.addView(key);'''
new = '''        final EditText key = field(Ai.key(this), "API Key（只存本机）");
        box.addView(base);
        box.addView(ModelPicker.button(this));
        box.addView(key);'''
assert old in s
s = s.replace(old, new, 1)
old = '''                        .putString("ai_base", base.getText().toString().trim())
                        .putString("ai_model", model.getText().toString().trim())
                        .putString("ai_key", key.getText().toString().trim())'''
new = '''                        .putString("ai_base", base.getText().toString().trim())
                        .putString("ai_key", key.getText().toString().trim())'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ================= LyricEditorActivity =================
p = 'src/com/xiaojing/jingge/LyricEditorActivity.java'
s = rd(p)
old = '''        final EditText model = field(Ai.model(this), "模型");
        final EditText key = field(Ai.key(this), "API Key（只存本机）");
        box.addView(base);
        box.addView(model);
        box.addView(key);'''
new = '''        final EditText key = field(Ai.key(this), "API Key（只存本机）");
        box.addView(base);
        box.addView(ModelPicker.button(this));
        box.addView(key);'''
assert old in s
s = s.replace(old, new, 1)
old = '''                        .putString("ai_base", base.getText().toString().trim())
                        .putString("ai_model", model.getText().toString().trim())
                        .putString("ai_key", key.getText().toString().trim())'''
new = '''                        .putString("ai_base", base.getText().toString().trim())
                        .putString("ai_key", key.getText().toString().trim())'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="18"', 'android:versionCode="19"').replace('android:versionName="0.18"', 'android:versionName="0.19"')
wr(p, s)
print('ok')
