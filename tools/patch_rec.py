# -*- coding: utf-8 -*-
import io

# ── MainActivity ──
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('private static final String VER = "0.37";', 'private static final String VER = "0.38";')
s = s.replace('''    private int beforeSheet = MODE_SONG;''',
'''    private int beforeSheet = MODE_SONG;
    private boolean pendingBall;''')

# onResume：恢复悬浮球
old = '''    protected void onResume() {
        super.onResume();
        ui.post(tick);
        syncNow();
    }'''
new = '''    protected void onResume() {
        super.onResume();
        ui.post(tick);
        syncNow();
        if (pendingBall) {
            pendingBall = false;
            if (android.os.Build.VERSION.SDK_INT < 23
                    || android.provider.Settings.canDrawOverlays(this)) {
                BallService.setWanted(this, true);
                BallService.start(this);
                Toast.makeText(this, "悬浮球开了 · 点一下听音识曲", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没给「显示在其他应用上层」，悬浮球起不来",
                        Toast.LENGTH_LONG).show();
            }
        } else if (BallService.wanted(this) && !BallService.running
                && (android.os.Build.VERSION.SDK_INT < 23
                || android.provider.Settings.canDrawOverlays(this))) {
            BallService.start(this);
        }
    }'''
assert old in s
s = s.replace(old, new, 1)

# onNewIntent：设置页 / 识曲
old = '''        if (intent != null && "open".equals(intent.getAction())) {
            intent.setAction(null);
            if (svc != null && svc.currentSong() != null) openPlayer();
        }
    }'''
new = '''        if (intent == null) return;
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

# 侧边栏：加 听音识曲 / 悬浮球
old = '''        TextView settingsItem = text("设置", 15, Ui.MUTED, false);'''
new = '''        TextView recItem = text("听音识曲", 15, Ui.MUTED, false);
        recItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        recItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                startActivity(new Intent(MainActivity.this, RecognizeActivity.class));
            }
        });
        drawer.addView(recItem);

        final TextView ballItem = text("悬浮球", 15, Ui.MUTED, false);
        ballItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        ballItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                toggleBall();
            }
        });
        drawer.addView(ballItem);

        TextView settingsItem = text("设置", 15, Ui.MUTED, false);'''
assert old in s
s = s.replace(old, new, 1)

# 设置里加「听音识曲」入口
old = '''        TextView al = text("AI 写词", 11, Ui.DIM, false);'''
new = '''        TextView rl = text("听音识曲", 11, Ui.DIM, false);
        rl.setPadding(0, dp(16), 0, dp(6));
        box.addView(rl);

        TextView recRow = text("识别服务 · ACRCloud / AudD", 12, Ui.ACCENT, false);
        recRow.setPadding(dp(2), dp(6), dp(2), dp(10));
        recRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showRecSettings();
            }
        });
        box.addView(recRow);

        TextView al = text("AI 写词", 11, Ui.DIM, false);'''
assert old in s
s = s.replace(old, new, 1)

# 新方法：悬浮球开关 + 识曲设置
anchor = '''    private void confirmDelete(final Song s) {'''
methods = '''    // ───────────────── 听音识曲 ─────────────────

    private void toggleBall() {
        if (BallService.wanted(this) || BallService.running) {
            BallService.setWanted(this, false);
            BallService.stop(this);
            Toast.makeText(this, "悬浮球关了", Toast.LENGTH_SHORT).show();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23
                && !android.provider.Settings.canDrawOverlays(this)) {
            pendingBall = true;
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
                Toast.makeText(this, "先允许「显示在其他应用上层」，回来就开",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(this, "打不开权限页，去系统设置里手动给", Toast.LENGTH_LONG).show();
            }
            return;
        }
        BallService.setWanted(this, true);
        BallService.start(this);
        Toast.makeText(this, "悬浮球开了 · 点一下听音识曲，拖到底部圆圈松手就关",
                Toast.LENGTH_LONG).show();
    }

    private void showRecSettings() {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("听音识曲", 15, Ui.TEXT, true));

        TextView tip = text("录 8 秒交给服务商认指纹。两家都要自己去注册拿 key，"
                + "额度按他们的规则；识别片段会上传到那一家。", 11, Ui.MUTED, false);
        tip.setPadding(0, dp(8), 0, dp(10));
        box.addView(tip);

        final String cur = Recognize.provider(this);
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(prow);
        prow.addView(recChip(d, "ACRCloud", Recognize.P_ACR, cur));
        prow.addView(recChip(d, "AudD", Recognize.P_AUDD, cur));

        TextView hl = text("ACRCloud", 11, Ui.DIM, false);
        hl.setPadding(0, dp(14), 0, dp(6));
        box.addView(hl);

        final EditText host = field(Recognize.acrHost(this),
                "host 例如 identify-eu-west-1.acrcloud.com");
        final EditText key = field(Recognize.acrKey(this), "access key");
        final EditText secret = field(Recognize.acrSecret(this), "access secret");
        box.addView(host);
        box.addView(key);
        box.addView(secret);

        TextView tl2 = text("AudD", 11, Ui.DIM, false);
        tl2.setPadding(0, dp(12), 0, dp(6));
        box.addView(tl2);
        final EditText token = field(Recognize.auddToken(this), "AudD api_token");
        box.addView(token);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView cancel = text("关闭", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("保存", 13, Ui.ACCENT, true);
        ok.setPadding(dp(12), dp(8), dp(12), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Recognize.saveAcr(MainActivity.this,
                        host.getText().toString(), key.getText().toString(),
                        secret.getText().toString());
                Recognize.saveAudd(MainActivity.this, token.getText().toString());
                d.dismiss();
                Toast.makeText(MainActivity.this,
                        Recognize.configured(MainActivity.this) ? "存好了" : "还没填全，识曲会提示",
                        Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private View recChip(final Dialog d, final String name, final String id, String cur) {
        final boolean on = id.equals(cur);
        TextView t = text(name, 13, on ? Ui.ACCENT : Ui.MUTED, on);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        t.setBackground(Ui.roundStroke(0, on ? Ui.ACCENT : Ui.LINE, dp(10), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Recognize.setProvider(MainActivity.this, id);
                d.dismiss();
                showRecSettings();
            }
        });
        return t;
    }

'''
assert anchor in s
s = s.replace(anchor, methods + anchor, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ── MusicService：通知栏加「识曲」 ──
p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()
old = '''        Notification.Action aNext = new Notification.Action.Builder(
                R.drawable.ic_next, "下一首", servicePi(ACT_NEXT, 3)).build();'''
new = '''        Notification.Action aNext = new Notification.Action.Builder(
                R.drawable.ic_next, "下一首", servicePi(ACT_NEXT, 3)).build();
        Intent rec = new Intent(this, RecognizeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent recPi = PendingIntent.getActivity(this, 6, rec, piFlags());
        Notification.Action aRec = new Notification.Action.Builder(
                R.drawable.stat_music, "听音识曲", recPi).build();'''
assert old in s
s = s.replace(old, new, 1)

old = '''                .addAction(aPrev)
                .addAction(aPlay)
                .addAction(aNext);'''
new = '''                .addAction(aPrev)
                .addAction(aPlay)
                .addAction(aNext)
                .addAction(aRec);'''
assert old in s
s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ── Manifest ──
p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('android:versionCode="37"', 'android:versionCode="38"')
s = s.replace('android:versionName="0.37"', 'android:versionName="0.38"')
s = s.replace('''    <uses-permission android:name="android.permission.WAKE_LOCK" />''',
'''    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />''')
s = s.replace('''        <service
            android:name=".MusicService"''',
'''        <activity
            android:name=".RecognizeActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:launchMode="singleTop"
            android:theme="@android:style/Theme.Translucent.NoTitleBar"
            android:label="@string/app_name" />

        <service
            android:name=".BallService"
            android:exported="false"
            android:stopWithTask="false" />

        <service
            android:name=".MusicService"''')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('patched')
