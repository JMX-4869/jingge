# -*- coding: utf-8 -*-
import io

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('private static final String VER = "0.37";', 'private static final String VER = "0.38";')

# 新增字段
s = s.replace('''    private int beforeSheet = MODE_SONG;''',
'''    private int beforeSheet = MODE_SONG;
    private boolean pendingBall;''')

# onCreate 之后：恢复悬浮球 + 处理 intent
old = '''    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "open".equals(intent.getAction())) {
            intent.setAction(null);
            if (svc != null && svc.currentSong() != null) openPlayer();
        }
    }'''
new = '''    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        String act = intent.getAction();
        if ("open".equals(act)) {
            intent.setAction(null);
            if (svc != null && svc.currentSong() != null) openPlayer();
        } else if ("settings".equals(act)) {
            intent.setAction(null);
            showRecSettings();
        }
    }'''
assert old in s
s = s.replace(old, new, 1)

# onResume 里补悬浮球权限回来后的启动：找 onResume
old = '''    @Override
    protected void onResume() {'''
assert old in s
s = s.replace(old, '''    @Override
    protected void onResume() {
        super.onResume();
        if (pendingBall) {
            pendingBall = false;
            if (Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)) {
                BallService.setWanted(this, true);
                BallService.start(this);
                Toast.makeText(this, "悬浮球开了 · 点一下听音识曲", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没给「显示在其他应用上层」，悬浮球起不来",
                        Toast.LENGTH_LONG).show();
            }
        }
        if (BallService.wanted(this) && !BallService.running
                && (Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this))) {
            BallService.start(this);
        }
        onResumeRest();''', 1)

# 原 onResume 的剩余内容接到 onResumeRest 上
old2 = '''        onResumeRest();
'''
assert s.count(old2) == 1
idx = s.index(old2) + len(old2)
# 原 onResume 主体以 super.onResume() 开头，删掉重复那句
rest_start = s.index(old2)
rest = s[rest_start:]
if rest.lstrip().startswith('super.onResume();'):
    rest = rest.lstrip()[len('super.onResume();'):]
    s = s[:rest_start] + rest

# 方法 onResumeRest 需要把原 onResume 的 body 收进去：改成包一层
s = s.replace('''    @Override
    protected void onResume() {
        super.onResume();''', '''    private void onResumeRest() {''', 1)

# 上面替换后，原 onResume 的结尾 "}" 会成为 onResumeRest 的结尾——检查结构
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('stage1')
