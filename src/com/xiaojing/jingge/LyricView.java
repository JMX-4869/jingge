package com.xiaojing.jingge;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌词：当前行亮、自动滚到中间，点一行跳到那句。
 * 当前行带 K 歌式进度：字从左到右被强调色填过去。
 * 非中文歌每行下面带中文译文。
 */
public class LyricView extends ScrollView {
    public interface OnSeek {
        void onSeek(int ms);
    }

    /** 播放进度来源（每帧读一次）。 */
    public interface Clock {
        int now();
    }

    private static final class Row {
        LinearLayout root;
        TextView base;
        TextView fill;
        TextView trans;
    }

    private final LinearLayout box;
    private final List<Row> rows = new ArrayList<Row>();
    private final List<Integer> times = new ArrayList<Integer>();
    private final Rect clip = new Rect();
    private int current = -2;
    private int lastPos;
    private boolean showTrans = true;
    private OnSeek onSeek;
    private Clock clock;
    private boolean running;

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

    public void setClock(Clock c) {
        clock = c;
    }

    /** 双语歌词开关；关了只显示原文。 */
    public void setShowTrans(boolean on) {
        showTrans = on;
    }

    public boolean showTrans() {
        return showTrans;
    }

    /** 播放页出现时开始按帧刷新。 */
    public void start() {
        if (running) return;
        running = true;
        postOnAnimation(tick);
    }

    public void stop() {
        running = false;
        removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (clock != null) setPosition(clock.now());
            postOnAnimation(this);
        }
    };

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

    /** 播放进度变化时调用（每帧一次）。 */
    public void setPosition(int ms) {
        lastPos = ms;
        if (times.isEmpty() || rows.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) <= ms) idx = i;
            else break;
        }
        if (idx != current) {
            // 换行：旧的收掉填充，新的亮起来
            for (Row r : rows) {
                setFill(r, 0f);
                r.base.setTextColor(Ui.DIM);
                if (r.trans != null) r.trans.setTextColor(Ui.blend(Ui.DIM, Ui.BG, 0.35f));
            }
            Row cur = rows.get(idx);
            cur.base.setTextColor(Ui.TEXT);
            if (cur.trans != null) cur.trans.setTextColor(Ui.MUTED);
            current = idx;
            View v = cur.root;
            smoothScrollTo(0, Math.max(0,
                    v.getTop() - (getHeight() - v.getHeight()) / 2));
        }
        Row r = rows.get(idx);
        int start = times.get(idx);
        int end = idx + 1 < times.size() ? times.get(idx + 1) : start + 4000;
        if (end <= start) end = start + 2000;
        float p = (ms - start) / (float) (end - start);
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        setFill(r, p);
    }

    private void setFill(Row r, float p) {
        if (r.fill == null) return;
        int w = r.fill.getWidth();
        int h = r.fill.getHeight();
        if (w <= 0 || h <= 0) return;
        if (p <= 0f) {
            r.fill.setVisibility(View.INVISIBLE);
            return;
        }
        r.fill.setVisibility(View.VISIBLE);
        clip.set(0, 0, (int) (w * p), h);
        r.fill.setClipBounds(clip);
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

        FrameLayout stack = new FrameLayout(getContext());
        TextView base = lyricText(text, Ui.DIM);
        TextView fill = lyricText(text, Ui.ACCENT);
        fill.setVisibility(View.INVISIBLE);
        stack.addView(base, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stack.addView(fill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stack.setMinimumHeight(Ui.dp(getContext(), 26));
        root.addView(stack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tr = null;
        if (trans != null && trans.trim().length() > 0
                && !trans.trim().equals(text.trim())) {
            tr = lyricText(trans, Ui.blend(Ui.DIM, Ui.BG, 0.35f));
            tr.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
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
        row.base = base;
        row.fill = fill;
        row.trans = tr;
        rows.add(row);
        box.addView(root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView lyricText(String text, int color) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }
}
