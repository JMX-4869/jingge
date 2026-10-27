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

/** 歌词：当前行亮、自动滚到中间，点一行跳到那句。 */
public class LyricView extends ScrollView {
    public interface OnSeek {
        void onSeek(int ms);
    }

    private final LinearLayout box;
    private final List<TextView> rows = new ArrayList<TextView>();
    private final List<Integer> times = new ArrayList<Integer>();
    private int current = -2;
    private int lastPos;
    private OnSeek onSeek;

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

    public void setMessage(String msg) {
        reset();
        TextView t = new TextView(getContext());
        t.setText(msg);
        t.setTextSize(13);
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
            for (String line : l.plain) addRow(line, -1);
            return;
        }
        for (Lyric.Line line : l.lines) addRow(line.text, line.timeMs);
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
            rows.get(i).setTextColor(i == idx ? Ui.TEXT : Ui.DIM);
        }
        if (idx < rows.size()) {
            View v = rows.get(idx);
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

    private void addRow(String text, final int timeMs) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(Ui.DIM);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(getContext(), 4), 0, Ui.dp(getContext(), 4), 0);
        t.setMinHeight(Ui.dp(getContext(), 36));
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (timeMs >= 0) {
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onSeek != null) onSeek.onSeek(timeMs);
                }
            });
            times.add(timeMs);
        }
        rows.add(t);
        box.addView(t);
    }
}
