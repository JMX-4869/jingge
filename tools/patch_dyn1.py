# -*- coding: utf-8 -*-
import io

def rd(p): return io.open(p, encoding='utf-8').read()
def wr(p, s): io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---------- GlowDrawable：描边自己呼吸 ----------
p = 'src/com/xiaojing/jingge/GlowDrawable.java'
s = rd(p)
s = s.replace('''import android.graphics.drawable.Drawable;''',
'''import android.graphics.drawable.Drawable;
import android.os.SystemClock;''')
s = s.replace('''    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();''',
'''    @Override
    public void draw(Canvas c) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(SystemClock.uptimeMillis() / 900.0);
        Rect b = getBounds();''')
s = s.replace('''                p.setColor(alpha(accent, 3 + i * 4));''',
'''                p.setColor(alpha(accent, (int) ((3 + i * 4) * (0.55f + 0.75f * pulse))));''')
s = s.replace('''            p.setColor(alpha(accent, 90));''',
'''            p.setColor(alpha(accent, (int) (60 + 70 * pulse)));''')
s = s.replace('''    private static int alpha(int color, int a) {''',
'''    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) invalidateSelf();
        return changed;
    }

    private static int alpha(int color, int a) {''')
# 画完自己再要一帧，形成呼吸动画
s = s.replace('''            c.drawRoundRect(rf, radius, radius, p);
        }
    }

    private static int alpha''',
'''            c.drawRoundRect(rf, radius, radius, p);
            if (isVisible()) invalidateSelf();
        }
    }

    private static int alpha''')
wr(p, s)

# ---------- MainActivity：播放页透出星空 + 侧边栏选中条呼吸 ----------
p = 'src/com/xiaojing/jingge/MainActivity.java'
s = rd(p)
s = s.replace('private static final String VER = "0.27";', 'private static final String VER = "0.28";')
s = s.replace('''        p.setPadding(dp(20), 0, dp(20), dp(16));
        p.setBackgroundColor(Ui.BG);''',
'''        p.setPadding(dp(20), 0, dp(20), dp(16));
        if (Ui.STYLE != Ui.STYLE_GLOW) p.setBackgroundColor(Ui.BG);''')

s = s.replace('''    private BatchMatch batchMatch;''',
'''    private BatchMatch batchMatch;
    private android.animation.ValueAnimator barPulse;''')

s = s.replace('''    private void openDrawer() {''',
'''    private void startBarPulse() {
        if (Ui.STYLE != Ui.STYLE_GLOW) return;
        if (barPulse != null) return;
        barPulse = android.animation.ValueAnimator.ofFloat(1f, 0.35f, 1f);
        barPulse.setDuration(2000);
        barPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        barPulse.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                for (int i = 0; i < navBars.length; i++) {
                    if (navBars[i] != null) navBars[i].setAlpha(i == mode ? v : 1f);
                }
            }
        });
        barPulse.start();
    }

    private void openDrawer() {''')
wr(p, s)

print('part1 ok')
