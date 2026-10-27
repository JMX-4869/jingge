package com.xiaojing.jingge;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 歌词：当前行亮、自动滚到中间，点一行跳到那句。非中文歌每行下面带中文译文。 */
public class LyricView extends ScrollView {
    public interface OnSeek {
        void onSeek(int ms);
    }

    private static final class Row {
        LinearLayout root;
        TextView main;
        TextView trans;
    }

    private final LinearLayout box;
    private final List<Row> rows = new ArrayList<Row>();
    private final List<Integer> times = new ArrayList<Integer>();
    private int current = -2;
    private int lastPos;
    private OnSeek onSeek;
    private boolean showTrans = true;

    public LyricView(Context c) {
        super(c);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setOnSeek(OnSeek s) {
        onSeek = s;
    }

    /** 双语歌词开关；关了只显示原文。 */
    public void setShowTrans(boolean on) {
        showTrans = on;
    }

    public boolean showTrans() {
        return showTrans;
    }

    public void setMessage(String msg) {
        reset();
        TextView t = new TextView(getContext());
        t.setText(msg);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(Ui.DIM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(getContext(), 30), 0, Ui.dp(getContext(), 30));
        box.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setLyric(final Lyric l) {
        reset();
        if (l == null) {
            setMessage("没找到歌词");
            return;
        }
        if (!l.synced) {
            for (String line : l.plain) addRow(line, null, -1);
            return;
        }
        for (Lyric.Line line : l.lines)
            addRow(line.text, showTrans ? line.trans : null, line.timeMs);
        post(new Runnable() {
            @Override
            public void run() {
                int pad = Math.max(0, (getHeight() - Ui.dp(getContext(), 36)) / 2);
                box.setPadding(0, pad, 0, pad);
                current = -2;
                setPosition(lastPos);
            }
        });
    }

    /** 播放进度变化时调用（500ms 一次足够）。 */
    public void setPosition(int ms) {
        lastPos = ms;
        if (times.isEmpty() || rows.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) <= ms) idx = i;
            else break;
        }
        if (idx == current) return;
        current = idx;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean on = i == idx;
            r.main.setTextColor(on ? Ui.TEXT : Ui.DIM);
            if (r.trans != null) {
                r.trans.setTextColor(on ? Ui.MUTED : Ui.blend(Ui.DIM, Ui.BG, 0.35f));
            }
        }
        if (idx < rows.size()) {
            View v = rows.get(idx).root;
            smoothScrollTo(0, Math.max(0,
                    v.getTop() - (getHeight() - v.getHeight()) / 2));
        }
    }

    private void reset() {
        box.removeAllViews();
        box.setPadding(0, 0, 0, 0);
        rows.clear();
        times.clear();
        current = -2;
        scrollTo(0, 0);
    }

    private void addRow(String text, String trans, final int timeMs) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(Ui.dp(getContext(), 4), Ui.dp(getContext(), 5),
                Ui.dp(getContext(), 4), Ui.dp(getContext(), 5));

        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(Ui.DIM);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMinHeight(Ui.dp(getContext(), 26));
        root.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tr = null;
        if (trans != null && trans.trim().length() > 0
                && !trans.trim().equals(text.trim())) {
            tr = new TextView(getContext());
            tr.setText(trans);
            tr.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            tr.setTextColor(Ui.blend(Ui.DIM, Ui.BG, 0.35f));
            tr.setGravity(Gravity.CENTER);
            tr.setSingleLine(true);
            tr.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(getContext(), 3);
            root.addView(tr, tlp);
        }

        if (timeMs >= 0) {
            root.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onSeek != null) onSeek.onSeek(timeMs);
                }
            });
            times.add(timeMs);
        }

        Row row = new Row();
        row.root = root;
        row.main = t;
        row.trans = tr;
        rows.add(row);
        box.addView(root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
