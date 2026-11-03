# -*- coding: utf-8 -*-
"""0.54：通知/悬浮球自检 + 切到「悬浮球」入口时自动把球起起来"""
import io

def rw(p, pairs):
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, 'missing in %s:\n%s' % (p, old[:80])
        s = s.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ─────────── MusicService：记录失败原因 + 自检 ───────────
rw('src/com/xiaojing/jingge/MusicService.java', [
("""    private static final String CHANNEL = "playback";""",
"""    private static final String CHANNEL = "playback";
    static boolean fg = false;
    static String lastErr = null;"""),

("""    private void ensureForeground() {
        try {""",
"""    /** 通知为什么不出来 —— 让 App 自己说，别让用户猜 */
    static String selfCheck(Context c) {
        StringBuilder sb = new StringBuilder();
        NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (m == null) return "取不到通知服务";
        boolean allowed = Build.VERSION.SDK_INT < 24 || m.areNotificationsEnabled();
        sb.append("．通知总开关：").append(allowed ? "开" : "关着（系统设置 → 通知 → 鲸歌）").append('\\n');
        if (Build.VERSION.SDK_INT >= 33) {
            boolean p = c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            sb.append("．通知权限：").append(p ? "已给" : "没给（设置里点一下会再问）").append('\\n');
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = m.getNotificationChannel(CHANNEL);
            if (ch == null) {
                sb.append("．频道「播放控制」：还没建\\n");
            } else {
                int im = ch.getImportance();
                String s = im == NotificationManager.IMPORTANCE_NONE ? "被你关掉了"
                        : im >= NotificationManager.IMPORTANCE_DEFAULT ? "正常"
                        : "低（会缩成一行，展开才看得见按钮）";
                sb.append("．频道「播放控制」：").append(s).append('\\n');
            }
        }
        sb.append("．前台服务：").append(fg ? "在跑" : "没起（先放一首歌）").append('\\n');
        if (lastErr != null) sb.append("．上次起通知失败：").append(lastErr).append('\\n');
        return sb.toString();
    }

    private void ensureForeground() {
        try {"""),

("""            foreground = true;
            if (session != null) session.setActive(true);
        } catch (Throwable t) {
            foreground = false;
        }""",
"""            foreground = true;
            fg = true;
            lastErr = null;
            if (session != null) session.setActive(true);
        } catch (Throwable t) {
            foreground = false;
            fg = false;
            lastErr = t.getClass().getSimpleName() + "：" + t.getMessage();
        }"""),
])

# ─────────── MainActivity：切入口自动起球 + 设置里加自检 ───────────
rw('src/com/xiaojing/jingge/MainActivity.java', [
('private static final String VER = "0.53";', 'private static final String VER = "0.54";'),

("""    private void setRecEntry(String v) {
        Ai.prefs(this).edit().putString("rec_entry", v).apply();
        if ("notif".equals(v)) {
            BallService.setWanted(this, false);
            BallService.stop(this);
        }
        if (svc != null) svc.refreshNotification();
    }""",
"""    private void setRecEntry(String v) {
        Ai.prefs(this).edit().putString("rec_entry", v).apply();
        if ("notif".equals(v)) {
            BallService.setWanted(this, false);
            BallService.stop(this);
        } else if (android.os.Build.VERSION.SDK_INT < 23
                || android.provider.Settings.canDrawOverlays(this)) {
            // 选了带悬浮球的入口，就把它起起来，不然选了跟没选一样
            BallService.setWanted(this, true);
            BallService.start(this);
        } else {
            pendingBall = true;
        }
        if (svc != null) svc.refreshNotification();
    }

    /** 通知栏体检：说不出原因就别让用户瞎试 */
    private void showNotifCheck() {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("通知栏体检", 15, Ui.TEXT, true));

        TextView body = text(MusicService.selfCheck(this), 12, Ui.MUTED, false);
        body.setPadding(0, dp(10), 0, dp(12));
        body.setLineSpacing(dp(4), 1f);
        box.addView(body);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView sys = text("去通知设置", 13, Ui.ACCENT, true);
        sys.setPadding(dp(12), dp(8), dp(4), dp(8));
        sys.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra("android.provider.extra.APP_PACKAGE", getPackageName())
                            .putExtra("app_package", getPackageName())
                            .putExtra("app_uid", getApplicationInfo().uid);
                    startActivity(i);
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "打不开通知设置", Toast.LENGTH_SHORT).show();
                }
            }
        });
        row.addView(sys);

        TextView close = text("知道了", 13, Ui.MUTED, false);
        close.setPadding(dp(12), dp(8), dp(12), dp(8));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(close);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }"""),

("""        TextView aboutItem = text("关于", 15, Ui.MUTED, false);""",
"""        TextView notifItem = text("通知栏体检", 15, Ui.MUTED, false);
        notifItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        notifItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                showNotifCheck();
            }
        });
        drawer.addView(notifItem);

        TextView aboutItem = text("关于", 15, Ui.MUTED, false);"""),
])

# ─────────── Manifest ───────────
def mn(s):
    return (s.replace('android:versionCode="53"', 'android:versionCode="54"')
             .replace('android:versionName="0.53"', 'android:versionName="0.54"'))
p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s2 = mn(s)
assert s2 != s
io.open(p, 'w', encoding='utf-8', newline='\n').write(s2)
print('patched 0.54')
