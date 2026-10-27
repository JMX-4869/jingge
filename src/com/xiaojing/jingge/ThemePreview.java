package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 拿一套候选配色，摆三个样张出来给人看：列表、播放页、对话框。 */
final class ThemePreview {
    private ThemePreview() {
    }

    static View build(Context c, Theme t) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(caption(c, t, "列表"));
        box.addView(listSample(c, t));
        box.addView(caption(c, t, "播放页"));
        box.addView(playerSample(c, t));
        box.addView(caption(c, t, "对话框"));
        box.addView(dialogSample(c, t));
        return box;
    }

    private static TextView caption(Context c, Theme t, String s) {
        TextView v = tv(c, s, 10, blend(t.muted, t.bg, 0.25f), false);
        v.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        return v;
    }

    private static View listSample(Context c, Theme t) {
        LinearLayout box = panel(c, t.bg, t.line, 10);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tv(c, "鲸歌", 11, t.text, true));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(new View(c), sp);
        head.addView(tv(c, "补全 · 校名 · 刷新", 9, t.muted, false));
        box.addView(head);
        box.addView(rule(c, t.line));

        String[][] rows = {
                {"夜航西飞", "陈粒", "4:12", "0"},
                {"真夜中のドア", "松原みき", "5:11", "1"},
                {"Love you like I do", "Jamillions", "3:54", "0"},
        };
        int up = blend(t.bg, t.text, 0.10f);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));

            View cover = new View(c);
            cover.setBackground(Ui.round(up, Ui.dp(c, 4)));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    Ui.dp(c, 26), Ui.dp(c, 26));
            clp.setMargins(0, 0, Ui.dp(c, 8), 0);
            r.addView(cover, clp);

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            boolean on = rows[i][3].equals("1");
            col.addView(tv(c, rows[i][0], 11, on ? t.accent : t.text, false));
            col.addView(tv(c, rows[i][1], 9, t.muted, false));
            r.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(tv(c, rows[i][2], 9, blend(t.muted, t.bg, 0.35f), false));

            if (i < rows.length - 1) {
                LinearLayout wrap = new LinearLayout(c);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.addView(r);
                wrap.addView(rule(c, blend(t.line, t.bg, 0.45f)));
                box.addView(wrap);
            } else {
                box.addView(r);
            }
        }
        return box;
    }

    private static View playerSample(Context c, Theme t) {
        LinearLayout box = panel(c, t.bg, t.line, 10);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        View disc = new View(c);
        disc.setBackground(Ui.roundStroke(blend(t.bg, t.text, 0.06f),
                blend(t.accent, t.bg, 0.35f), Ui.dp(c, 40), Ui.dp(c, 1)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                Ui.dp(c, 46), Ui.dp(c, 46));
        box.addView(disc, dlp);

        TextView title = tv(c, "真夜中のドア / Stay With Me", 12, t.text, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(c, 6), 0, 0);
        box.addView(title);

        TextView l1 = tv(c, "もう戻れないの あの日に", 10, t.muted, false);
        l1.setGravity(Gravity.CENTER);
        box.addView(l1);

        TextView l2 = tv(c, "已经回不去 那一天了", 10, t.accent, false);
        l2.setGravity(Gravity.CENTER);
        box.addView(l2);
        return box;
    }

    private static View dialogSample(Context c, Theme t) {
        LinearLayout box = panel(c, t.panel, t.accent, 10);
        box.addView(tv(c, "设置", 12, t.text, true));
        TextView body = tv(c, "当前主题：" + t.name, 10, t.muted, false);
        body.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 8));
        box.addView(body);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.addView(tv(c, "取消", 10, t.muted, false));
        TextView ok = tv(c, "  保存", 10, t.accent, true);
        row.addView(ok);
        box.addView(row);
        return box;
    }

    private static LinearLayout panel(Context c, int fill, int border, int pad) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.roundStroke(fill, blend(border, fill, 0.35f),
                Ui.dp(c, 10), Math.max(1, Ui.dp(c, 1))));
        box.setPadding(Ui.dp(c, pad), Ui.dp(c, pad), Ui.dp(c, pad), Ui.dp(c, pad));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(c, 2);
        box.setLayoutParams(lp);
        return box;
    }

    private static View rule(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setSingleLine(true);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static int blend(int a, int b, float f) {
        if (f < 0f) f = 0f;
        if (f > 1f) f = 1f;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * f);
        int g = (int) (ag + (bg - ag) * f);
        int bl = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
