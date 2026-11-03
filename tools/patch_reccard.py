# -*- coding: utf-8 -*-
"""0.66: 单独一条常驻通知「听音识曲」，按钮一直挂在那儿，点了才录"""
import io

p = 'src/com/xiaojing/jingge/MusicService.java'
s = io.open(p, encoding='utf-8').read()

# 1) 常量
old = '''    private static final int NOTIF_ID = 1001;'''
new = '''    private static final int NOTIF_ID = 1001;
    private static final int NOTIF_ID_REC = 1002;'''
assert old in s
s = s.replace(old, new, 1)

# 2) 常驻通知的构建 + 刷新
anchor = '''    private void ensureForeground() {'''
body = '''    /** 常驻的「听音识曲」卡：按钮一直在，点了才录 */
    private Notification buildRecNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        String text = recBusy ? (recStatus == null ? "正在听…" : recStatus)
                : (recStatus != null ? recStatus : "点一下开始听，外放或哼都行");
        b.setSmallIcon(R.drawable.stat_music)
                .setContentTitle("听音识曲")
                .setContentText(text)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(!recBusy)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Ui.ACCENT)
                .setContentIntent(PendingIntent.getActivity(this, 8,
                        new Intent(this, MainActivity.class).setAction("open")
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP), piFlags()))
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_scan, recBusy ? "正在听…" : "开始识别",
                        servicePi(ACT_REC, 9)).build());
        if (Build.VERSION.SDK_INT >= 24 && !recBusy) {
            b.setStyle(new Notification.BigTextStyle().bigText(text));
        }
        return b.build();
    }

    private void showRecCard() {
        if (nm == null) return;
        try {
            nm.notify(NOTIF_ID_REC, buildRecNotification());
        } catch (Throwable t) {
            lastErr = "识曲卡：" + t.getMessage();
        }
    }

    private void ensureForeground() {'''
assert anchor in s
s = s.replace(anchor, body, 1)

# 3) 前台起来后把识曲卡挂上
old3 = '''            lastErr = null;
            if (Build.VERSION.SDK_INT >= 24 && nm != null) {'''
new3 = '''            lastErr = null;
            showRecCard();
            if (Build.VERSION.SDK_INT >= 24 && nm != null) {'''
assert old3 in s
s = s.replace(old3, new3, 1)

# 4) 识别开始/进度/结束都刷这张卡
old4 = '''        recStatus = "正在听… 0s";
        promoteMicForeground();
        updateNotification();'''
new4 = '''        recStatus = "正在听… 0s";
        promoteMicForeground();
        updateNotification();
        showRecCard();
        toastMain("开始听，8 秒别停");'''
assert old4 in s
s = s.replace(old4, new4, 1)

old5 = '''                                recStatus = "正在听… " + secs + "s";
                                updateNotification();'''
new5 = '''                                recStatus = "正在听… " + secs + "s";
                                updateNotification();
                                showRecCard();'''
assert old5 in s
s = s.replace(old5, new5, 1)

old6 = '''                toastMain(say);
                updateNotification();'''
new6 = '''                toastMain(say);
                updateNotification();
                showRecCard();'''
assert old6 in s
s = s.replace(old6, new6, 1)

old7 = '''                        recStatus = null;
                        updateNotification();'''
new7 = '''                        recStatus = null;
                        updateNotification();
                        showRecCard();'''
assert old7 in s
s = s.replace(old7, new7, 1)

# 5) 服务停掉时把卡收走
old8 = '''    @Override
    public void onDestroy() {'''
if old8 in s:
    s = s.replace(old8, '''    @Override
    public void onDestroy() {
        if (nm != null) nm.cancel(NOTIF_ID_REC);''', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

def rw(path, pairs):
    t = io.open(path, encoding='utf-8').read()
    for a, b in pairs:
        assert a in t, a
        t = t.replace(a, b, 1)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(t)

rw('src/com/xiaojing/jingge/MainActivity.java',
   [('private static final String VER = "0.65";', 'private static final String VER = "0.66";')])
rw('AndroidManifest.xml',
   [('android:versionCode="65"', 'android:versionCode="66"'),
    ('android:versionName="0.65"', 'android:versionName="0.66"')])
print('patched 0.66')
