# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ================= MainActivity =================
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.16";', 'private static final String VER = "0.17";')

# 崩：tabs 早就没了，这两行是 NPE
s = s.replace('''        currentGroup = null;
        tabs.setVisibility(View.VISIBLE);
        backRow.setVisibility(View.GONE);
        updateNav();''', '''        currentGroup = null;
        backRow.setVisibility(View.GONE);
        updateNav();''')
s = s.replace('''        currentGroup = null;
        tabs.setVisibility(View.VISIBLE);
        backRow.setVisibility(View.GONE);
        updateNav();
        search.setText("");''', '''        currentGroup = null;
        backRow.setVisibility(View.GONE);
        updateNav();
        search.setText("");''')
assert 'tabs.setVisibility' not in s
s = s.replace('    private LinearLayout tabs, backRow;', '    private LinearLayout backRow;')
s = s.replace('    private final TextView[] tabViews = new TextView[4];\n', '')

# 侧边栏 AI 入口
old = '''        TextView settingsItem = text("设置", 15, Ui.MUTED, false);'''
new = '''        TextView aiItem = text("AI 写词", 15, Ui.MUTED, false);
        aiItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        aiItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                openAiForCurrent();
            }
        });
        drawer.addView(aiItem);

        TextView settingsItem = text("设置", 15, Ui.MUTED, false);'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private void toggleTrans() {'''
new = '''    private void openAiForCurrent() {
        Song cur = svc == null ? null : svc.currentSong();
        if (cur == null) {
            Toast.makeText(this, "先放一首歌", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Intent it = new android.content.Intent(this, LyricEditorActivity.class);
        it.putExtra("id", cur.id);
        it.putExtra("title", cur.title);
        it.putExtra("artist", cur.artist);
        it.putExtra("album", cur.album);
        it.putExtra("uri", cur.uri);
        it.putExtra("dur", cur.durationMs);
        it.putExtra("ai", true);
        startActivityForResult(it, REQ_EDIT);
    }

    private void toggleTrans() {'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# ================= Ai =================
p = 'src/com/xiaojing/jingge/Ai.java'
s = rd(p)
s = s.replace('    static String prompt(String title, String artist, String hint) {',
              '    static String prompt(String title, String artist, String hint, boolean withTrans) {')
old = '''        sb.append("4. 只输出歌词正文：不要标题、不要解释、不要时间戳、不要序号。");'''
new = '''        if (withTrans) {
            sb.append("4. 每行固定格式：原文|||这一行的中文翻译（分隔符是三个竖线）；\\n");
            sb.append("5. 只输出这些行，不要标题、不要解释、不要时间戳、不要序号。");
        } else {
            sb.append("4. 只输出歌词正文：不要标题、不要解释、不要时间戳、不要序号。");
        }'''
assert old in s
s = s.replace(old, new, 1)
s = s.replace('''    static void generate(final Context ctx, final String title, final String artist,
                         final String hint, final Callback cb) {''',
'''    static void generate(final Context ctx, final String title, final String artist,
                         final String hint, final boolean withTrans, final Callback cb) {''')
s = s.replace('final String ask = prompt(title, artist, hint);',
              'final String ask = prompt(title, artist, hint, withTrans);')
wr(p, s)

# ================= 编辑页 =================
p = 'src/com/xiaojing/jingge/LyricEditorActivity.java'
s = rd(p)
s = s.replace('    private EditText memo;', '    private EditText memo;\n'
              '    private final List<String> pendingTrans = new ArrayList<String>();\n'
              '    private boolean postAi;')

# 从侧边栏进来直接弹 AI
s = s.replace('        songId = getIntent().getLongExtra("id", -1);',
              '        postAi = getIntent().getBooleanExtra("ai", false);\n'
              '        songId = getIntent().getLongExtra("id", -1);', 1)
assert 'setContentView(build());' in s
s = s.replace('        setContentView(build());',
'''        setContentView(build());
        if (postAi) {
            memo.post(new Runnable() {
                @Override
                public void run() {
                    showAiDialog();
                }
            });
        }''', 1)

# AI 对话框：加「同时生成翻译」开关
old = '''        box.addView(hint);

        LinearLayout row = new LinearLayout(this);'''
new = '''        box.addView(hint);

        final boolean[] withTrans = {Ai.prefs(this).getBoolean("ai_trans", false)};
        final TextView trToggle = text(withTrans[0] ? "✓ 同时生成中文翻译" : "○ 同时生成中文翻译",
                12, withTrans[0] ? Ui.ACCENT : Ui.MUTED, false);
        trToggle.setPadding(0, dp(10), 0, 0);
        trToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                withTrans[0] = !withTrans[0];
                Ai.prefs(LyricEditorActivity.this).edit()
                        .putBoolean("ai_trans", withTrans[0]).apply();
                trToggle.setText(withTrans[0] ? "✓ 同时生成中文翻译" : "○ 同时生成中文翻译");
                trToggle.setTextColor(withTrans[0] ? Ui.ACCENT : Ui.MUTED);
            }
        });
        box.addView(trToggle);

        LinearLayout row = new LinearLayout(this);'''
assert old in s
s = s.replace(old, new, 1)

# 生成：带翻译参数 + 解析成 原文/译文
old = '''                Ai.generate(LyricEditorActivity.this, title, artist,
                        hint.getText().toString(), new Ai.Callback() {'''
new = '''                Ai.generate(LyricEditorActivity.this, title, artist,
                        hint.getText().toString(), withTrans[0], new Ai.Callback() {'''
assert old in s
s = s.replace(old, new, 1)

old = '''                                memo.setText(text);
                                Toast.makeText(LyricEditorActivity.this,
                                        "生成好了，点「打点」对时间", Toast.LENGTH_SHORT).show();'''
new = '''                                pendingTrans.clear();
                                StringBuilder orig = new StringBuilder();
                                for (String raw : text.split("\\n")) {
                                    String line = raw.trim();
                                    if (line.length() == 0) continue;
                                    String[] pair = line.split("\\\\|\\\\|\\\\|");
                                    String o = pair[0].trim();
                                    String t = pair.length > 1 ? pair[1].trim() : "";
                                    if (o.length() == 0) continue;
                                    if (orig.length() > 0) orig.append('\\n');
                                    orig.append(o);
                                    pendingTrans.add(t);
                                }
                                memo.setText(orig.toString());
                                Toast.makeText(LyricEditorActivity.this,
                                        withTrans[0] ? "生成好了，带翻译；点「打点」对时间"
                                                : "生成好了，点「打点」对时间",
                                        Toast.LENGTH_SHORT).show();'''
assert old in s
s = s.replace(old, new, 1)

# 保存：同一时间点再写一行译文（标准双语 LRC）
old = '''        int last = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            if (times[i] >= 0) last = times[i];
            if (any) {
                sb.append(stamp(times[i] >= 0 ? times[i] : last)).append(line).append('\\n');
            } else {
                sb.append(line).append('\\n');
            }
        }'''
new = '''        int last = 0;
        int ti = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            if (times[i] >= 0) last = times[i];
            int ts = times[i] >= 0 ? times[i] : last;
            if (any) {
                sb.append(stamp(ts)).append(line).append('\\n');
            } else {
                sb.append(line).append('\\n');
            }
            String tr = ti < pendingTrans.size() ? pendingTrans.get(ti) : null;
            ti++;
            if (tr != null && tr.trim().length() > 0 && !tr.trim().equals(line)) {
                if (any) {
                    sb.append(stamp(ts)).append(tr.trim()).append('\\n');
                } else {
                    sb.append(tr.trim()).append('\\n');
                }
            }
        }'''
assert old in s
s = s.replace(old, new, 1)
wr(p, s)

# manifest
p = 'AndroidManifest.xml'
s = rd(p).replace('android:versionCode="16"', 'android:versionCode="17"')
s = s.replace('android:versionName="0.16"', 'android:versionName="0.17"')
wr(p, s)
print('ok')
