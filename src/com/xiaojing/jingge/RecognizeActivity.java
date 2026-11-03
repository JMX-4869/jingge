package com.xiaojing.jingge;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 * 听音识曲：录 8 秒，交给 Recognize 去问服务端。
 * 从悬浮球、通知栏、侧边栏都能进来。
 */
public class RecognizeActivity extends Activity {
    private static final int REQ_MIC = 91;
    private static final int SECONDS = 8;
    private static final int SR = 44100;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private RingView ring;
    private TextView status;
    private TextView hint;
    private LinearLayout resultBox;
    private ImageView cover;
    private TextView rTitle, rArtist, rAlbum, rVia;
    private LinearLayout btnRow;
    private TextView again, copy, close;

    private AudioRecord rec;
    private Thread worker;
    private volatile boolean stop;
    private volatile int level;
    private Recognize.Track found;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.apply(this);
        try {
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xCC000000));
        } catch (Throwable ignored) {
        }
        setContentView(build());
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            status.setText("要用麦克风");
            hint.setText("先给录音权限，只录这几秒，不上传整首");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            start();
        }
    }

    // ───────────────── 界面 ─────────────────

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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xCC000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(16));
        card.setBackground(Ui.dialogSurface(this, dp(20)));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        ring = new RingView(this);
        card.addView(ring, new LinearLayout.LayoutParams(dp(150), dp(150)));

        status = text("正在听…", 15, Ui.TEXT, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, 0);
        card.addView(status);

        hint = text("把手机靠近声源，安静一点更准", 12, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        hint.setMaxWidth(dp(260));
        card.addView(hint);

        // 结果区
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setGravity(Gravity.CENTER_HORIZONTAL);
        resultBox.setPadding(0, dp(14), 0, 0);
        resultBox.setVisibility(View.GONE);

        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.round(Ui.PANEL_UP, dp(12)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(112), dp(112));
        resultBox.addView(cover, clp);

        rTitle = text("", 17, Ui.TEXT, true);
        rTitle.setGravity(Gravity.CENTER);
        rTitle.setPadding(0, dp(12), 0, 0);
        rTitle.setMaxWidth(dp(280));
        resultBox.addView(rTitle);

        rArtist = text("", 13, Ui.ACCENT, false);
        rArtist.setGravity(Gravity.CENTER);
        rArtist.setPadding(0, dp(6), 0, 0);
        resultBox.addView(rArtist);

        rAlbum = text("", 11, Ui.MUTED, false);
        rAlbum.setGravity(Gravity.CENTER);
        rAlbum.setPadding(0, dp(4), 0, 0);
        resultBox.addView(rAlbum);

        rVia = text("", 10, Ui.DIM, false);
        rVia.setGravity(Gravity.CENTER);
        rVia.setPadding(0, dp(8), 0, 0);
        resultBox.addView(rVia);

        card.addView(resultBox);

        btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, dp(16), 0, 0);
        card.addView(btnRow);

        again = pill("再听一次", Ui.MUTED, false);
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        btnRow.addView(again);

        copy = pill("复制名字", Ui.MUTED, false);
        copy.setVisibility(View.GONE);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (found == null) return;
                String s = (found.title == null ? "" : found.title)
                        + (found.artist == null ? "" : " - " + found.artist);
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("歌曲", s));
                Toast.makeText(RecognizeActivity.this, "复制了：" + s, Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(copy);

        close = pill("关闭", Ui.ACCENT, true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btnRow.addView(close);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.addView(card, lp);
        card.setMinimumWidth(dp(280));
        return root;
    }

    private TextView pill(String label, int color, boolean bold) {
        TextView t = text(label, 13, color, bold);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        t.setBackground(Ui.roundStroke(0, color == Ui.ACCENT ? Ui.ACCENT : Ui.LINE, dp(12), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        return t;
    }

    // ───────────────── 流程 ─────────────────

    private void start() {
        if (!Recognize.configured(this)) {
            ring.setMode(RingView.IDLE);
            status.setText("还没配识别服务");
            hint.setText(Recognize.P_AUDD.equals(Recognize.provider(this))
                    ? "设置 → 听音识曲 → 填 AudD token"
                    : "设置 → 听音识曲 → 填 ACRCloud 的 host / key / secret");
            again.setVisibility(View.GONE);
            close.setText("去设置");
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(RecognizeActivity.this, MainActivity.class)
                            .setAction("settings")
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                }
            });
            return;
        }
        found = null;
        resultBox.setVisibility(View.GONE);
        copy.setVisibility(View.GONE);
        status.setText("正在听…");
        hint.setText("把手机靠近声源，安静一点更准");
        ring.setMode(RingView.LISTEN);
        stop = false;
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                record();
            }
        }, "listen");
        worker.start();
    }

    private void record() {
        int min = AudioRecord.getMinBufferSize(SR,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 4096;
        ByteArrayOutputStream out = new ByteArrayOutputStream(SR * 2 * SECONDS);
        short[] buf = new short[Math.max(1024, min / 2)];
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SR,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                fail("麦克风打不开（可能被别的应用占着）");
                return;
            }
            rec.startRecording();
            long end = System.currentTimeMillis() + SECONDS * 1000L;
            while (!stop && System.currentTimeMillis() < end) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) break;
                byte[] bytes = new byte[n * 2];
                int peak = 0;
                for (int i = 0; i < n; i++) {
                    bytes[i * 2] = (byte) (buf[i] & 0xff);
                    bytes[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xff);
                    int a = Math.abs(buf[i]);
                    if (a > peak) peak = a;
                }
                out.write(bytes, 0, bytes.length);
                level = peak * 100 / 32768;
            }
            try {
                rec.stop();
            } catch (Throwable ignored) {
            }
            rec.release();
            rec = null;
            if (stop) return;
            postStatus("正在认…", "这段已经录完了，等服务器回话");
            ui.post(new Runnable() {
                @Override
                public void run() {
                    ring.setMode(RingView.THINK);
                }
            });
            Recognize.identify(RecognizeActivity.this, out.toByteArray(), SR, new Recognize.Cb() {
                @Override
                public void onTrack(Recognize.Track t, String error) {
                    if (isFinishing()) return;
                    if (t == null) {
                        fail(error == null ? "没听出来" : error);
                    } else {
                        show(t);
                    }
                }
            });
        } catch (Throwable t) {
            fail("录音出错：" + t.getMessage());
        }
    }

    private void postStatus(final String title, final String sub) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                status.setText(title);
                hint.setText(sub);
            }
        });
    }

    private void fail(String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                ring.setMode(RingView.IDLE);
                status.setText("没听出来");
                hint.setText(msg);
                again.setVisibility(View.VISIBLE);
            }
        });
    }

    private void show(final Recognize.Track t) {
        found = t;
        ring.setMode(RingView.DONE);
        status.setText("听出来了");
        hint.setText("点「复制名字」，或者回主界面搜同名歌");
        rTitle.setText(t.title == null ? "未命名" : t.title);
        rArtist.setText(t.artist == null ? "" : t.artist);
        String al = t.album == null ? "" : "专辑： " + t.album;
        rAlbum.setText(al);
        rVia.setText("来源：" + (t.via == null ? "识别服务" : t.via));
        resultBox.setVisibility(View.VISIBLE);
        copy.setVisibility(View.VISIBLE);
        again.setVisibility(View.VISIBLE);
        if (t.cover != null && t.cover.length() > 8) {
            final String url = t.cover;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final byte[] b = Http.bytes(url, null, 8000, 15000);
                    if (b == null) return;
                    final Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                    if (bm == null) return;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            cover.setImageBitmap(bm);
                        }
                    });
                }
            }, "cover").start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req != REQ_MIC) return;
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            ring.setMode(RingView.IDLE);
            status.setText("没给麦克风权限");
            hint.setText("识曲要用麦克风录几秒；不给也能用别的功能");
            again.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        stop = true;
        try {
            if (rec != null) {
                rec.stop();
                rec.release();
                rec = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    // 录音时的呼吸圈
    static class RingView extends View {
        static final int IDLE = 0, LISTEN = 1, THINK = 2, DONE = 3;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int mode = IDLE;
        private float phase;

        RingView(Context c) {
            super(c);
        }

        void setMode(int m) {
            mode = m;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float base = Math.min(w, h) / 2f - 4;

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF11171A);
            cv.drawCircle(cx, cy, base * 0.62f, p);

            if (mode == LISTEN || mode == THINK) {
                phase += 0.03f;
                if (phase > 1f) phase -= 1f;
                for (int i = 0; i < 3; i++) {
                    float k = (phase + i / 3f) % 1f;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f);
                    p.setColor(Ui.ACCENT & 0x00FFFFFF | (((int) ((1 - k) * 130)) << 24));
                    cv.drawCircle(cx, cy, base * (0.62f + 0.36f * k), p);
                }
                postInvalidateDelayed(33);
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(mode == DONE ? Ui.ACCENT : (mode == IDLE ? Ui.DIM : Ui.ACCENT));
            cv.drawCircle(cx, cy, base * 0.72f, p);

            // 中间画个音符
            p.setStyle(Paint.Style.FILL);
            p.setColor(mode == IDLE ? Ui.MUTED : Ui.ACCENT);
            float s = base * 0.30f;
            cv.drawCircle(cx - s * 0.34f, cy + s * 0.34f, s * 0.26f, p);
            cv.drawCircle(cx + s * 0.44f, cy + s * 0.14f, s * 0.26f, p);
            p.setStrokeWidth(s * 0.16f);
            p.setStyle(Paint.Style.STROKE);
            cv.drawLine(cx - s * 0.12f, cy + s * 0.30f, cx - s * 0.12f, cy - s * 0.34f, p);
            cv.drawLine(cx + s * 0.66f, cy + s * 0.10f, cx + s * 0.66f, cy - s * 0.54f, p);
            cv.drawLine(cx - s * 0.12f, cy - s * 0.34f, cx + s * 0.66f, cy - s * 0.54f, p);
        }
    }
}
