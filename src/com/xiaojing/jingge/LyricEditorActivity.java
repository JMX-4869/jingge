package com.xiaojing.jingge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑歌词并打轴：上面粘贴歌词，下面每行点一下记当前播放时间。
 * 保存成 files/lyrics/<id>.lrc，主界面按文件时间自动重载。
 */
public class LyricEditorActivity extends Activity {

    private long songId;
    private String title, artist, album, uri;
    private long durMs;

    private MusicService svc;
    private boolean bound;

    private EditText memo;
    private LinearLayout rowsBox;
    private ScrollView rowsScroll;
    private TextView nowTime;
    private Icons playBtn;

    private final List<TextView> timeViews = new ArrayList<TextView>();
    private final List<View> rowViews = new ArrayList<View>();

    private String[] lines = new String[0];
    private int[] times = new int[0];
    private int pos;
    private int lastPunched = -1;
    private boolean loading;

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
        } catch (Throwable ignored) {
        }
        songId = getIntent().getLongExtra("id", -1);
        title = getIntent().getStringExtra("title");
        artist = getIntent().getStringExtra("artist");
        album = getIntent().getStringExtra("album");
        uri = getIntent().getStringExtra("uri");
        durMs = getIntent().getLongExtra("dur", 0);
        if (songId <= 0) {
            finish();
            return;
        }
        setContentView(build());
        load();
    }

    // ───────────────────────── 界面 ─────────────────────────

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), 0, dp(12), 0);
        head.setMinimumHeight(dp(56));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Icons back = new Icons(this).icon(Icons.CHEVRON_LEFT).color(Ui.TEXT).ratio(0.44f);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(6), 0, 0, 0);
        head.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titles.addView(text("编辑歌词", 14, Ui.TEXT, false));
        TextView sub = text(title == null ? "" : title, 11, Ui.DIM, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(sub);

        TextView save = text("保存", 13, Ui.ACCENT, false);
        save.setPadding(dp(12), dp(10), dp(12), dp(10));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        head.addView(save);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER_VERTICAL);
        ctrl.setPadding(dp(20), 0, dp(20), 0);
        ctrl.setBackgroundColor(Ui.PANEL);
        ctrl.setMinimumHeight(dp(64));
        root.addView(ctrl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playBtn = new Icons(this).icon(Icons.PLAY).color(Ui.TEXT).ratio(0.42f);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });
        ctrl.addView(playBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        nowTime = text("00:00.00", 16, Ui.TEXT, false);
        nowTime.setTypeface(Typeface.MONOSPACE);
        nowTime.setPadding(dp(12), 0, 0, 0);
        ctrl.addView(nowTime, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView punch = text("打点", 13, Ui.ACCENT, true);
        punch.setGravity(Gravity.CENTER);
        punch.setPadding(dp(18), dp(8), dp(18), dp(8));
        punch.setBackground(Ui.roundStroke(0x00000000, 0x6658E0C8, dp(10), dp(1)));
        punch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                punchNext();
            }
        });
        ctrl.addView(punch);

        memo = new EditText(this);
        memo.setBackground(Ui.round(Ui.PANEL, dp(10)));
        memo.setPadding(dp(14), dp(12), dp(14), dp(12));
        memo.setTextColor(Ui.TEXT);
        memo.setHintTextColor(Ui.DIM);
        memo.setHint("把歌词粘贴进来，一行一句");
        memo.setTextSize(14);
        memo.setGravity(Gravity.TOP | Gravity.START);
        memo.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        memo.setImeOptions(EditorInfo.IME_ACTION_NONE);
        memo.setMinLines(3);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mlp.setMargins(dp(16), dp(8), dp(16), dp(8));
        root.addView(memo, mlp);

        TextView tip = text("听到一句就点一下，记下当前时间", 11, Ui.DIM, false);
        tip.setPadding(dp(20), dp(2), dp(20), dp(6));
        root.addView(tip);

        rowsScroll = new ScrollView(this);
        rowsScroll.setVerticalScrollBarEnabled(false);
        rowsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f);
        slp.setMargins(dp(16), 0, dp(16), dp(12));
        root.addView(rowsScroll, slp);

        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsScroll.addView(rowsBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        memo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (loading) return;
                ui.removeCallbacks(syncText);
                ui.postDelayed(syncText, 300);
            }
        });

        return root;
    }

    private final Runnable syncText = new Runnable() {
        @Override
        public void run() {
            String[] nl = memo.getText().toString().split("\n", -1);
            int n = nl.length;
            while (n > 0 && nl[n - 1].trim().length() == 0) n--;
            int[] nt = new int[n];
            for (int i = 0; i < n; i++) nt[i] = i < times.length ? times[i] : -1;
            String[] ls = new String[n];
            System.arraycopy(nl, 0, ls, 0, n);
            lines = ls;
            times = nt;
            rebuildRows();
        }
    };

    // ───────────────────────── 数据 ─────────────────────────

    private void load() {
        loading = true;
        String raw = LyricStore.read(this, songId);
        Lyric l = raw == null ? null : Lyric.parse(raw);
        if (l == null) l = LyricFinder.peek(songId);

        List<String> ls = new ArrayList<String>();
        List<Integer> ts = new ArrayList<Integer>();
        if (l != null) {
            if (l.synced) {
                for (Lyric.Line line : l.lines) {
                    ls.add(line.text);
                    ts.add(line.timeMs);
                }
            } else {
                for (String s : l.plain) {
                    ls.add(s);
                    ts.add(-1);
                }
            }
        }
        lines = ls.toArray(new String[0]);
        times = new int[lines.length];
        for (int i = 0; i < times.length; i++) times[i] = ts.get(i);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        memo.setText(sb.toString());
        loading = false;
        rebuildRows();
    }

    private void rebuildRows() {
        rowsBox.removeAllViews();
        timeViews.clear();
        rowViews.clear();
        for (int i = 0; i < lines.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), 0, dp(4), 0);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    punch(idx);
                }
            });

            TextView tv = text(label(times[i]), 12,
                    times[i] >= 0 ? Ui.ACCENT : Ui.DIM, false);
            tv.setTypeface(Typeface.MONOSPACE);
            row.addView(tv, new LinearLayout.LayoutParams(
                    dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tx = text(lines[i], 13, Ui.TEXT, false);
            tx.setSingleLine(true);
            tx.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tx, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView hit = text("打点", 11, Ui.ACCENT, false);
            hit.setPadding(dp(10), dp(8), dp(10), dp(8));
            hit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    punch(idx);
                }
            });
            row.addView(hit);

            row.setMinimumHeight(dp(40));
            rowsBox.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            timeViews.add(tv);
            rowViews.add(row);
        }
    }

    private void punchNext() {
        if (lines.length == 0) return;
        int idx = lastPunched + 1;
        if (idx >= lines.length) idx = lines.length - 1;
        punch(idx);
    }

    private void punch(int i) {
        if (i < 0 || i >= lines.length) return;
        if (lines[i].trim().length() == 0) return;
        if (svc == null || !isThisSong()) {
            Toast.makeText(this, "先点播放，听到这句再打点", Toast.LENGTH_SHORT).show();
            return;
        }
        pos = svc.position();
        times[i] = pos;
        lastPunched = i;
        if (i < timeViews.size()) {
            timeViews.get(i).setText(label(pos));
            timeViews.get(i).setTextColor(Ui.ACCENT);
        }
        final int next = Math.min(i + 1, lines.length - 1);
        rowsScroll.post(new Runnable() {
            @Override
            public void run() {
                if (next < rowViews.size()) {
                    View v = rowViews.get(next);
                    rowsScroll.smoothScrollTo(0, Math.max(0, v.getTop() - dp(70)));
                }
            }
        });
    }

    private void save() {
        boolean hasText = false;
        for (String l : lines) {
            if (l.trim().length() > 0) {
                hasText = true;
                break;
            }
        }
        if (!hasText) {
            Toast.makeText(this, "先写点歌词", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append("[ti:").append(title).append("]\n");
        if (artist != null) sb.append("[ar:").append(artist).append("]\n");

        boolean any = false;
        for (int t : times) {
            if (t >= 0) {
                any = true;
                break;
            }
        }
        int last = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            if (times[i] >= 0) last = times[i];
            if (any) {
                sb.append(stamp(times[i] >= 0 ? times[i] : last)).append(line).append('\n');
            } else {
                sb.append(line).append('\n');
            }
        }
        LyricStore.save(this, songId, sb.toString());
        LyricFinder.invalidate(songId);
        Toast.makeText(this, any ? "已保存，带时间轴" : "已保存，纯文本（还没打点）",
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ───────────────────────── 播放 ─────────────────────────

    private boolean isThisSong() {
        return svc != null && svc.currentSong() != null && svc.currentSong().id == songId;
    }

    private void toggle() {
        if (svc == null) return;
        if (!isThisSong()) svc.playById(songId);
        else svc.toggle();
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((MusicService.LocalBinder) b).service();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            svc = null;
            bound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, MusicService.class);
        startService(i);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacks(tick);
        if (bound) {
            try {
                unbindService(conn);
            } catch (Throwable ignored) {
            }
            bound = false;
        }
        svc = null;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (svc != null && isThisSong()) {
                pos = svc.position();
                nowTime.setText(label(pos));
                playBtn.icon(svc.isPlaying() ? Icons.PAUSE : Icons.PLAY);
            } else {
                nowTime.setText("00:00.00");
                playBtn.icon(Icons.PLAY);
            }
            ui.postDelayed(this, 300);
        }
    };

    // ───────────────────────── 格式 ─────────────────────────

    private static String label(int ms) {
        if (ms < 0) return "--:--";
        int m = ms / 60000;
        int s = (ms / 1000) % 60;
        int cs = (ms % 1000) / 10;
        return String.format("%02d:%02d.%02d", m, s, cs);
    }

    private static String stamp(int ms) {
        return "[" + label(ms) + "]";
    }
}
