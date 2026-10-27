package com.xiaojing.jingge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简界面：黑底、灰阶字、封面是唯一的颜色来源。
 * 青色只出现在两处 —— 进度条的"现在"，和正在播放的那一行。
 */
public class MainActivity extends Activity implements MusicService.Listener {

    private static final int REQ_PERM = 41;
    private static final String PREFS = "jingge";
    private static final String VER = "0.26";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Song> all = new ArrayList<Song>();
    private final List<Song> display = new ArrayList<Song>();
    private static final int MODE_SONG = 0, MODE_ALBUM = 1, MODE_ARTIST = 2,
            MODE_FOLDER = 3, MODE_GROUP = 4;
    private int mode = MODE_SONG;
    private int lastTab = MODE_SONG;
    private final List<Group> groups = new ArrayList<Group>();
    private Group currentGroup;
    private GroupAdapter groupAdapter;
    private AlbumAdapter albumAdapter;
    private LinearLayout backRow;
    private FrameLayout drawerScrim;
    private LinearLayout drawer;
    private int drawerW;
    private boolean drawerOpen;
    private final TextView[] navItems = new TextView[4];
    private final View[] navBars = new View[4];
    private TextView backTitle;
    private GridView grid;
    private TextView transBtn;
    private boolean showTrans = true;
    private BatchMatch batchMatch;
    private NameMatch nameMatch;
    private Dialog batchDialog;
    private Lyric curLyric;
    private final LruCache<Long, Bitmap> albumCovers =
            new LruCache<Long, Bitmap>(12 * 1024 * 1024) {
                @Override
                protected int sizeOf(Long k, Bitmap v) {
                    return v.getByteCount();
                }
            };
    private final LruCache<Long, Bitmap> smallCovers =
            new LruCache<Long, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(Long k, Bitmap v) {
                    return v.getByteCount();
                }
            };
    private final ExecutorService coverPool = Executors.newFixedThreadPool(3);

    private MusicService svc;
    private boolean bound;
    private boolean requestedOnce;
    private long restoreId = -1;
    private long coverId = -1;

    private ListView listView;
    private SongAdapter adapter;
    private TextView emptyTitle, emptySub, emptyBtn;
    private View emptyBox, miniBar;
    private CoverView miniCover;
    private TextView miniTitle, miniArtist;
    private Icons miniPlay;
    private EditText search;

    private View playerPage;
    private VinylView vinyl;
    private LyricView lyricView;
    private TextView pTitle, pArtist, tNow, tAll;
    private ThinSeek seek;
    private Icons bigPlay, modeIcon;
    private boolean playerOpen, dragging;
    private long lyricSongId = -1;
    private long lyricStamp = -1;
    private long pendingCoverId = -1;
    private LinearLayout nowBox;
    private TextView nowTitle, nowLyric;
    private Lyric currentLyric;
    private int nowLine = -2;
    private static final int REQ_COVER = 51;
    private static final int REQ_EDIT = 52;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.apply(this);
        showTrans = Ai.prefs(this).getBoolean("lyric_trans", true);
        try {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
            if (!Ui.isDark()) {
                int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                getWindow().getDecorView().setSystemUiVisibility(f);
            }
        } catch (Throwable ignored) {
        }
        restoreId = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("lastId", -1);

        setContentView(buildUi());

        adapter = new SongAdapter();
        groupAdapter = new GroupAdapter();
        albumAdapter = new AlbumAdapter();
        grid.setAdapter(albumAdapter);
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyBox);
        updateNav();
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (listView.getAdapter() == groupAdapter) {
                    if (pos >= 0 && pos < groups.size()) openGroup(groups.get(pos));
                    return;
                }
                if (pos < 0 || pos >= display.size()) return;
                Song s = display.get(pos);
                if (svc == null) return;
                svc.playById(s.id);
                openPlayer();
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (listView.getAdapter() == groupAdapter) {
                    if (pos >= 0 && pos < groups.size()) openGroup(groups.get(pos));
                    return true;
                }
                if (pos >= 0 && pos < display.size()) showSongMenu(display.get(pos));
                return true;
            }
        });

        seek.setListener(new ThinSeek.Listener() {
            @Override
            public void onSeekStart() {
                dragging = true;
            }

            @Override
            public void onSeekMove(int ms) {
                tNow.setText(Song.fmtDuration(ms));
                lyricView.setPosition(ms);
            }

            @Override
            public void onSeekEnd(int ms) {
                dragging = false;
                if (svc != null) svc.seekTo(ms);
            }
        });

        syncNow();
        updateEmpty();
        if (hasPerm()) scan();
        else requestPerms();
    }

    // ───────────────────────── 生命周期 ─────────────────────────

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
        syncNow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        vinyl.setSpinning(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (svc != null) svc.setListener(null);
        if (bound) {
            try {
                unbindService(conn);
            } catch (Throwable ignored) {
            }
            bound = false;
        }
        svc = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "open".equals(intent.getAction())) {
            intent.setAction(null);
            if (svc != null && svc.currentSong() != null) openPlayer();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (playerOpen) {
            closePlayer();
            return;
        }
        if (mode == MODE_GROUP) {
            backToTabs();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        coverPool.shutdownNow();
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((MusicService.LocalBinder) b).service();
            bound = true;
            svc.setListener(MainActivity.this);
            if (!all.isEmpty()) svc.setQueue(all, restoreId);
            syncNow();
            if ("open".equals(getIntent().getAction()) && svc.currentSong() != null) {
                getIntent().setAction(null);
                openPlayer();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            bound = false;
            svc = null;
            syncNow();
        }
    };

    // ───────────────────────── 扫描与权限 ─────────────────────────

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasPerm() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT < 23) {
            scan();
            return;
        }
        requestedOnce = true;
        List<String> ps = new ArrayList<String>();
        ps.add(audioPermission());
        if (Build.VERSION.SDK_INT >= 33) ps.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(ps.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] grant) {
        if (code == REQ_PERM) {
            if (hasPerm()) scan();
            else updateEmpty();
        }
    }

    private void scan() {
        emptyTitle.setText("正在扫描");
        emptySub.setText("翻一下手机里的音乐…");
        emptyBtn.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Song> got = MusicRepo.scan(MainActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        onSongs(got);
                    }
                });
            }
        }, "scan").start();
    }

    private void onSongs(List<Song> got) {
        all.clear();
        all.addAll(got);
        applyQuery(search.getText().toString());
        if (svc != null && !all.isEmpty()) svc.setQueue(all, restoreId);
        syncNow();
    }

    private void updateEmpty() {
        emptyBtn.setVisibility(View.VISIBLE);
        if (!hasPerm()) {
            emptyTitle.setText("要读一下音乐文件");
            emptySub.setText("只在本机扫描，不联网、不上传");
            emptyBtn.setText("授权读取  →");
        } else if (all.isEmpty()) {
            emptyTitle.setText("没找到音乐");
            emptySub.setText("把歌放进手机存储，再点右上角刷新");
            emptyBtn.setText("重新扫描  →");
        } else if (mode == MODE_ARTIST || mode == MODE_FOLDER) {
            if (groups.isEmpty()) {
                emptyTitle.setText("这一类是空的");
                emptySub.setText("换一个分类看看");
            }
            emptyBtn.setVisibility(View.GONE);
        } else if (display.isEmpty()) {
            if (mode == MODE_GROUP) {
                emptyTitle.setText("这组里没有歌");
                emptySub.setText("换个关键词");
            } else {
                emptyTitle.setText("没搜到");
                emptySub.setText("换个关键词");
            }
            emptyBtn.setText("清空搜索  →");
        } else {
            emptyBtn.setVisibility(View.GONE);
        }
    }

    private void onEmptyAction() {
        if (!hasPerm()) {
            boolean stuck = requestedOnce && Build.VERSION.SDK_INT >= 23
                    && !shouldShowRequestPermissionRationale(audioPermission());
            if (stuck) {
                Toast.makeText(this, "在设置里允许读取音频", Toast.LENGTH_SHORT).show();
                openAppSettings();
            } else {
                requestPerms();
            }
        } else if (all.isEmpty()) {
            scan();
        } else {
            search.setText("");
        }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    // ───────────────────────── 状态同步 ─────────────────────────

    @Override
    public void onPlaybackChanged() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                syncNow();
            }
        });
    }

    private void syncNow() {
        Song cur = svc == null ? null : svc.currentSong();
        boolean playing = svc != null && svc.isPlaying();
        boolean has = cur != null;

        miniBar.setVisibility(has ? View.VISIBLE : View.GONE);
        nowBox.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            miniTitle.setText(cur.title);
            miniArtist.setText(cur.subtitle());
            nowTitle.setText(cur.title);
            miniCover.setPlaceholder(cur.title);
            vinyl.setPlaceholder(cur.title);
        }
        miniPlay.icon(playing ? Icons.PAUSE : Icons.PLAY);
        bigPlay.icon(playing ? Icons.PAUSE : Icons.PLAY);

        if (has) {
            pTitle.setText(cur.title);
            pArtist.setText(cur.subtitle());
            maybeLoadLyric(cur);
        }

        if (svc != null) {
            int m = svc.mode();
            modeIcon.icon(m == PlayQueue.MODE_SHUFFLE ? Icons.SHUFFLE
                    : m == PlayQueue.MODE_ONE ? Icons.REPEAT_ONE : Icons.REPEAT);
            modeIcon.color(m == PlayQueue.MODE_LIST ? Ui.DIM : Ui.TEXT);
        }

        vinyl.setSpinning(playing && playerOpen);
        adapter.notifyDataSetChanged();
        refreshCover(has ? cur : null);

        if (has) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("lastId", cur.id).apply();
        }
    }

    /** 切歌、或用户刚编辑过歌词（文件时间变了）才重新加载。 */
    private void maybeLoadLyric(final Song cur) {
        long stamp = LyricStore.stamp(this, cur.id);
        if (cur.id == lyricSongId && stamp == lyricStamp) return;
        boolean localChanged = stamp != lyricStamp;
        lyricSongId = cur.id;
        lyricStamp = stamp;
        if (localChanged) LyricFinder.invalidate(cur.id);
        lyricView.setMessage("正在找歌词");
        nowLyric.setText("正在找歌词");
        currentLyric = null;
        nowLine = -2;
        final long want = cur.id;
        LyricFinder.find(this, cur, new LyricFinder.Callback() {
            @Override
            public void onLyric(Lyric l, String source) {
                if (lyricSongId != want) return;
                currentLyric = l;
                nowLine = -2;
                if (l == null) {
                    lyricView.setMessage("没找到歌词");
                    nowLyric.setText("没找到歌词");
                } else {
                    curLyric = l;
            lyricView.setLyric(l);
                    if (l.synced && !l.lines.isEmpty()) {
                        nowLyric.setText(l.lines.get(0).text);
                    } else if (!l.plain.isEmpty()) {
                        nowLyric.setText(l.plain.get(0));
                    } else {
                        nowLyric.setText("");
                    }
                }
            }
        });
    }

    private void refreshCover(Song s) {
        if (s == null) {
            coverId = -1;
            vinyl.setCover(null);
            miniCover.setBmp(null);
            return;
        }
        if (s.id == coverId) return;
        coverId = s.id;
        vinyl.setCover(null);
        miniCover.setBmp(null);
        final long id = s.id;
        final Song song = s;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = CoverStore.loadUser(MainActivity.this, id, 720);
                if (bm == null) bm = MusicRepo.cover(MainActivity.this, song, 720);
                if (bm == null) bm = CoverStore.loadCache(MainActivity.this, id, 720);
                final Bitmap local = bm;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (svc == null || svc.currentSong() == null
                                || svc.currentSong().id != id) return;
                        vinyl.setCover(local);
                        miniCover.setBmp(local);
                        if (local == null) fetchCoverOnline(song, id);
                    }
                });
            }
        }, "cover").start();
    }

    private void fetchCoverOnline(final Song song, final long id) {
        CoverFinder.find(this, song, new CoverFinder.Callback() {
            @Override
            public void onCover(Bitmap bm) {
                if (bm == null) return;
                if (svc == null || svc.currentSong() == null
                        || svc.currentSong().id != id) return;
                coverId = id;
                vinyl.setCover(bm);
                miniCover.setBmp(bm);
            }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (svc != null) {
                int pos = svc.position();
                updateNowLyric(pos);
                if (playerOpen) {
                    int dur = svc.duration();
                    if (dur > 0) seek.setDuration(dur);
                    if (!dragging) {
                        seek.setPosition(pos);
                        tNow.setText(Song.fmtDuration(pos));
                        tAll.setText(Song.fmtDuration(dur));
                        lyricView.setPosition(pos);
                    }
                }
            }
            ui.postDelayed(this, 500);
        }
    };

    /** 列表页顶部那一行歌词，跟着播放走。 */
    private void updateNowLyric(int pos) {
        if (currentLyric == null || !currentLyric.synced) return;
        int idx = currentLyric.indexAt(pos);
        if (idx == nowLine) return;
        nowLine = idx;
        if (idx >= 0 && idx < currentLyric.lines.size()) {
            nowLyric.setText(currentLyric.lines.get(idx).text);
        }
    }

    // ───────────────────────── 播放页 ─────────────────────────

    private void openPlayer() {
        if (svc == null || svc.currentSong() == null) return;
        if (!playerOpen) {
            playerOpen = true;
            playerPage.setVisibility(View.VISIBLE);
            playerPage.setAlpha(0f);
            playerPage.setTranslationY(dp(36));
            playerPage.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
        if (lyricView != null) lyricView.start();
        syncNow();
    }

    // ───────────────────────── 封面 / 歌词编辑 ─────────────────────────

    private void showSongMenu(final Song s) {
        if (s == null) return;
        final String[] items = {"编辑歌曲信息", "恢复原来的名字", "换封面",
                "清除自定义封面", "重新找封面", "编辑歌词"};
        new AlertDialog.Builder(this)
                .setTitle(s.title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                editMeta(s);
                                break;
                            case 1:
                                MetaStore.remove(MainActivity.this, s.id);
                                scan();
                                break;
                            case 2:
                                pickCover(s);
                                break;
                            case 3:
                                CoverStore.deleteUser(MainActivity.this, s.id);
                                resetCoverIfCurrent(s.id);
                                break;
                            case 4:
                                CoverStore.deleteCache(MainActivity.this, s.id);
                                resetCoverIfCurrent(s.id);
                                break;
                            case 5:
                                editLyric(s);
                                break;
                            default:
                                break;
                        }
                    }
                })
                .show();
    }

    private void editMeta(final Song s) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("编辑歌曲信息", 15, Ui.TEXT, true));

        final EditText tTitle = field(s.title == null ? "" : s.title, "歌名");
        final EditText tArtist = field(s.artist == null ? "" : s.artist, "歌手");
        final EditText tAlbum = field(s.album == null ? "" : s.album, "专辑");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(tTitle, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(8);
        box.addView(tArtist, lp2);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp3.topMargin = dp(8);
        box.addView(tAlbum, lp3);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(12), 0, 0);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(12), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("保存", 13, Ui.ACCENT, true);
        ok.setPadding(dp(16), dp(8), dp(16), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nt = tTitle.getText().toString().trim();
                if (nt.length() == 0) {
                    Toast.makeText(MainActivity.this, "歌名不能空", Toast.LENGTH_SHORT).show();
                    return;
                }
                MetaStore.put(MainActivity.this, s.id, nt,
                        tArtist.getText().toString().trim(),
                        tAlbum.getText().toString().trim());
                d.dismiss();
                scan();
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void resetCoverIfCurrent(long id) {
        if (coverId != id) return;
        coverId = -1;
        refreshCover(svc != null ? svc.currentSong() : null);
    }

    private void pickCover(Song s) {
        pendingCoverId = s.id;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选一张图片当封面"), REQ_COVER);
        } catch (Throwable t) {
            pendingCoverId = -1;
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void editLyric(Song s) {
        Intent i = new Intent(this, LyricEditorActivity.class);
        i.putExtra("id", s.id);
        i.putExtra("title", s.title);
        i.putExtra("artist", s.artist);
        i.putExtra("album", s.album);
        i.putExtra("uri", s.uri);
        i.putExtra("dur", s.durationMs);
        startActivityForResult(i, REQ_EDIT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EDIT && res == RESULT_OK) {
            lyricSongId = -1;
            lyricStamp = -1;
            syncNow();
            return;
        }
        if (req != REQ_COVER || res != RESULT_OK || data == null || data.getData() == null) return;
        final long id = pendingCoverId;
        if (id <= 0) return;
        pendingCoverId = -1;
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = decodeUri(uri, 900);
                if (bm != null) CoverStore.saveUser(MainActivity.this, id, bm);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (svc != null && svc.currentSong() != null
                                && svc.currentSong().id == id) {
                            coverId = -1;
                            refreshCover(svc.currentSong());
                        }
                    }
                });
            }
        }, "cover-save").start();
    }

    private Bitmap decodeUri(Uri uri, int maxPx) {
        InputStream in = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            in = null;
            int sample = 1;
            while (o.outWidth > 0 && Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(in, null, o);
            in.close();
            in = null;
            return bm;
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void closePlayer() {
        if (!playerOpen) return;
        playerOpen = false;
        if (lyricView != null) lyricView.stop();
        vinyl.setSpinning(false);
        playerPage.animate().alpha(0f).translationY(dp(28)).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        playerPage.setVisibility(View.GONE);
                    }
                }).start();
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

    private void showSettings() {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("设置", 15, Ui.TEXT, true));

        TextView sl = text("风格", 11, Ui.DIM, false);
        sl.setPadding(0, dp(14), 0, dp(6));
        box.addView(sl);

        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(srow);
        srow.addView(styleChip(d, "极简", Ui.STYLE_MINIMAL, Ui.style(this)));
        srow.addView(styleChip(d, "群星", Ui.STYLE_GLOW, Ui.style(this)));

        TextView tl = text("主题 · 点一下就用", 11, Ui.DIM, false);
        tl.setPadding(0, dp(14), 0, dp(4));
        box.addView(tl);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Theme t : ThemeStore.all(this)) {
            list.addView(themeRow(d, t));
        }
        box.addView(list);

        LinearLayout trow = new LinearLayout(this);
        trow.setOrientation(LinearLayout.HORIZONTAL);
        trow.setPadding(0, dp(2), 0, 0);
        box.addView(trow);

        TextView mine = text("自制主题", 12, Ui.ACCENT, false);
        mine.setPadding(dp(6), dp(8), dp(16), dp(8));
        mine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showThemeEditor();
            }
        });
        trow.addView(mine);

        TextView imp = text("导入", 12, Ui.MUTED, false);
        imp.setPadding(dp(6), dp(8), dp(6), dp(8));
        imp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showThemeImport();
            }
        });
        trow.addView(imp);

        TextView al = text("AI 写词", 11, Ui.DIM, false);
        al.setPadding(0, dp(18), 0, dp(6));
        box.addView(al);

        final EditText base = field(Ai.base(this), "接口地址");
        final EditText key = field(Ai.key(this), "API Key（只存本机）");
        box.addView(base);
        box.addView(ModelPicker.button(this));
        box.addView(key);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(10), 0, 0);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(12), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("保存", 13, Ui.ACCENT, true);
        ok.setPadding(dp(16), dp(8), dp(16), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ai.prefs(MainActivity.this).edit()
                        .putString("ai_base", base.getText().toString().trim())
                        .putString("ai_key", key.getText().toString().trim())
                        .apply();
                d.dismiss();
            }
        });
        row.addView(ok);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        d.setContentView(sv);
        dialogWindowTall(d);
        d.show();
    }

    private View styleChip(final Dialog d, final String name, final int style, int cur) {
        final boolean on = style == cur;
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
                Ui.setStyle(MainActivity.this, style);
                ThemeStore.setActive(MainActivity.this, style == Ui.STYLE_GLOW
                        ? "bi:entropy"
                        : (Ui.systemDark(MainActivity.this) ? "bi:black" : "bi:paper"));
                d.dismiss();
                recreate();
            }
        });
        return t;
    }

    private View themeRow(final Dialog d, final Theme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));

        View sw = new View(this);
        sw.setBackground(Ui.oval(t.bg));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(18), dp(18));
        slp.setMargins(0, 0, dp(10), 0);
        row.addView(sw, slp);

        View dot = new View(this);
        dot.setBackground(Ui.oval(t.accent));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dlp.setMargins(0, 0, dp(10), 0);
        row.addView(dot, dlp);

        boolean cur = ThemeStore.active(this).id.equals(t.id);
        TextView name = text(t.name + (t.custom() ? "  ·自制" : ""), 13,
                cur ? Ui.ACCENT : (t.custom() ? Ui.TEXT : Ui.MUTED), cur);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (t.custom()) {
            TextView del = text("删", 11, Ui.DIM, false);
            del.setPadding(dp(10), dp(4), dp(2), dp(4));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ThemeStore.delete(MainActivity.this, t.id);
                    d.dismiss();
                    showSettings();
                }
            });
            row.addView(del);

            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(
                                "jingge-theme", ThemeStore.toJson(t).toString()));
                        Toast.makeText(MainActivity.this,
                                "主题代码已复制，可发给别人导入", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ThemeStore.setActive(MainActivity.this, t.id);
                d.dismiss();
                recreate();
            }
        });
        return row;
    }

    private void showThemeEditor() {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("自制主题", 15, Ui.TEXT, true));
        TextView tip = text("填 #RRGGBB。面板色按背景自动算。", 11, Ui.DIM, false);
        tip.setPadding(0, dp(4), 0, dp(10));
        box.addView(tip);

        final Theme cur = ThemeStore.active(this);
        final EditText name = field("我的主题", "名字");
        final EditText bg = field(ThemeStore.hex(cur.bg), "背景");
        final EditText txt = field(ThemeStore.hex(cur.text), "文字");
        final EditText mut = field(ThemeStore.hex(cur.muted), "次要文字");
        final EditText line = field(ThemeStore.hex(cur.line), "分隔线");
        final EditText acc = field(ThemeStore.hex(cur.accent), "强调色");
        box.addView(name);
        box.addView(bg);
        box.addView(txt);
        box.addView(mut);
        box.addView(line);
        box.addView(acc);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(10), 0, 0);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(12), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("保存并用", 13, Ui.ACCENT, true);
        ok.setPadding(dp(16), dp(8), dp(16), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nm = name.getText().toString().trim();
                if (nm.length() == 0) nm = "我的主题";
                int cbg = ThemeStore.parseColor(bg.getText().toString(), cur.bg);
                int ctext = ThemeStore.parseColor(txt.getText().toString(), cur.text);
                int cmut = ThemeStore.parseColor(mut.getText().toString(), cur.muted);
                int cline = ThemeStore.parseColor(line.getText().toString(), cur.line);
                int cacc = ThemeStore.parseColor(acc.getText().toString(), cur.accent);
                Theme t = new Theme("c:" + nm, nm, cbg, Ui.blend(cbg, ctext, 0.06f),
                        cline, ctext, cmut, cacc);
                ThemeStore.save(MainActivity.this, t);
                ThemeStore.setActive(MainActivity.this, t.id);
                d.dismiss();
                recreate();
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindowTall(d);
        d.show();
    }

    private void showThemeImport() {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("导入主题", 15, Ui.TEXT, true));
        TextView tip = text("粘贴主题代码：name / bg / text / muted / line / accent", 11, Ui.DIM, false);
        tip.setPadding(0, dp(4), 0, dp(10));
        box.addView(tip);

        final EditText json = new EditText(this);
        json.setHint("{\"name\":\"夜色\",\"bg\":\"#0A0A0A\",\"text\":\"#EDEDED\",...}");
        json.setHintTextColor(Ui.DIM);
        json.setTextColor(Ui.TEXT);
        json.setTextSize(12);
        json.setTypeface(android.graphics.Typeface.MONOSPACE);
        json.setBackground(Ui.round(Ui.PANEL_UP, dp(8)));
        json.setPadding(dp(10), dp(8), dp(10), dp(8));
        json.setMinLines(5);
        json.setGravity(Gravity.TOP | Gravity.START);
        box.addView(json);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(10), 0, 0);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(12), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("导入并用", 13, Ui.ACCENT, true);
        ok.setPadding(dp(16), dp(8), dp(16), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    org.json.JSONObject o = new org.json.JSONObject(
                            json.getText().toString().trim());
                    Theme t = ThemeStore.fromJson(o);
                    ThemeStore.save(MainActivity.this, t);
                    ThemeStore.setActive(MainActivity.this, t.id);
                    d.dismiss();
                    recreate();
                } catch (Throwable e) {
                    Toast.makeText(MainActivity.this, "主题代码看不懂：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindowTall(d);
        d.show();
    }

    private void dialogWindowTall(Dialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int wpx = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
        int hpx = (int) (getResources().getDisplayMetrics().heightPixels * 0.74f);
        w.setLayout(wpx, hpx);
    }

    private void showAbout() {

        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("鲸歌 " + VER, 15, Ui.TEXT, true));
        TextView t = text("本地音乐播放器。\n扫描只在本机，不上传；歌词和封面会去公开接口找。",
                12, Ui.MUTED, false);
        t.setPadding(0, dp(8), 0, 0);
        box.addView(t);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(10), 0, 0);
        box.addView(row);
        TextView ok = text("好", 13, Ui.ACCENT, true);
        ok.setPadding(dp(16), dp(8), dp(16), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.dialogSurface(this, dp(16)));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        return box;
    }

    private EditText field(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(Ui.DIM);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setBackground(Ui.round(Ui.PANEL_UP, dp(8)));
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        e.setLayoutParams(lp);
        return e;
    }

    private void dialogWindow(Dialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int wpx = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
        w.setLayout(wpx, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void openAiForCurrent() {
        Song cur = svc == null ? null : svc.currentSong();
        if (cur == null) {
            Toast.makeText(this, "先放一首歌", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Intent it = new android.content.Intent(this, LyricEditorActivity.class);
        it.putExtra("id", cur.id);
        it.putExtra("title", cur.title);
        it.putExtra("artist", cur.artist);
        it.putExtra("album", cur.album);
        it.putExtra("uri", cur.uri);
        it.putExtra("dur", cur.durationMs);
        it.putExtra("ai", true);
        startActivityForResult(it, REQ_EDIT);
    }

    private void startNameMatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nameMatch != null) return;

        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("匹配歌名", 15, Ui.TEXT, true));
        final TextView line = text("准备中…", 12, Ui.MUTED, false);
        line.setPadding(0, dp(8), 0, dp(12));
        box.addView(line);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        box.addView(row);
        TextView stop = text("停止", 13, Ui.MUTED, false);
        stop.setPadding(dp(12), dp(8), dp(12), dp(8));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nameMatch != null) nameMatch.cancel();
                d.dismiss();
            }
        });
        row.addView(stop);

        d.setContentView(box);
        dialogWindow(d);
        d.show();

        nameMatch = new NameMatch(this, new ArrayList<Song>(all), new NameMatch.Listener() {
            @Override
            public void onTick(int done, int total, int matched) {
                line.setText(done + " / " + total + "\n改成新名字 " + matched + " 首");
            }

            @Override
            public void onDone(int total, int matched, boolean cancelled) {
                nameMatch = null;
                if (d.isShowing()) d.dismiss();
                Toast.makeText(MainActivity.this,
                        (cancelled ? "停下了：" : "匹配完了：")
                                + "改名 " + matched + " 首（共 " + total + "）",
                        Toast.LENGTH_LONG).show();
                if (matched > 0) scan();
            }
        });
        nameMatch.start();
    }

    private void startBatch() {
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (batchMatch != null) return;

        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("补全歌词和封面", 15, Ui.TEXT, true));
        final TextView line = text("准备中…", 12, Ui.MUTED, false);
        line.setPadding(0, dp(8), 0, dp(12));
        box.addView(line);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        box.addView(row);
        TextView stop = text("停止", 13, Ui.MUTED, false);
        stop.setPadding(dp(12), dp(8), dp(12), dp(8));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (batchMatch != null) batchMatch.cancel();
                d.dismiss();
            }
        });
        row.addView(stop);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
        batchDialog = d;

        batchMatch = new BatchMatch(this, new ArrayList<Song>(all), new BatchMatch.Listener() {
            @Override
            public void onTick(int done, int total, int lyrics, int covers) {
                line.setText(done + " / " + total + "\n歌词 " + lyrics + " 首 · 封面 " + covers + " 张");
            }

            @Override
            public void onDone(int total, int lyrics, int covers, boolean cancelled) {
                if (batchDialog != null && batchDialog.isShowing()) batchDialog.dismiss();
                batchDialog = null;
                batchMatch = null;
                Toast.makeText(MainActivity.this,
                        (cancelled ? "停下了：" : "补全完了：")
                                + "歌词 " + lyrics + " 首 · 封面 " + covers + " 张",
                        Toast.LENGTH_LONG).show();
                if (adapter != null) adapter.notifyDataSetChanged();
                if (albumAdapter != null) albumAdapter.notifyDataSetChanged();
                if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
            }
        });
        batchMatch.start();
    }

    private void toggleTrans() {
        showTrans = !showTrans;
        Ai.prefs(this).edit().putBoolean("lyric_trans", showTrans).apply();
        if (transBtn != null) {
            transBtn.setTextColor(showTrans ? Ui.ACCENT : Ui.DIM);
            transBtn.setBackground(Ui.roundStroke(0,
                    showTrans ? Ui.ACCENT : Ui.LINE, dp(8), dp(1)));
        }
        if (lyricView != null) {
            lyricView.setShowTrans(showTrans);
            if (curLyric != null) lyricView.setLyric(curLyric);
        }
    }

    private View buildUi() {
        final FrameLayout root = new FrameLayout(this);
        android.graphics.drawable.Drawable screen = Ui.screenSurface(this);
        if (screen != null) root.setBackground(screen);
        else root.setBackgroundColor(Ui.BG);
        // 兜底：万一窗口被量成 wrap，至少还是满屏高
        root.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);

        if (Ui.STYLE == Ui.STYLE_GLOW) {
            root.addView(new StarfieldView(this), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        final RelativeLayout page = new RelativeLayout(this);
        page.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部整块固定从 0 开始，列表用相对规则填中间，不靠 weight
        final LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setId(View.generateViewId());
        RelativeLayout.LayoutParams topLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        top.setGravity(Gravity.TOP);
        page.addView(top, topLp);

        // 顶：小字标 + 数量 + 刷新。标题不抢内容。
        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(22), dp(12), 0);
        header.setMinimumHeight(dp(56));
        top.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // 正在播放 + 当前歌词：列表页顶上的一小块，点它进播放页。
        nowBox = new LinearLayout(this);
        nowBox.setOrientation(LinearLayout.VERTICAL);
        nowBox.setPadding(dp(20), dp(8), dp(20), dp(8));
        nowBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPlayer();
            }
        });
        nowBox.setVisibility(View.GONE);
        top.addView(nowBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        nowTitle = text("", 13, Ui.TEXT, false);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowBox.addView(nowTitle);

        nowLyric = text("", 12, Ui.MUTED, false);
        nowLyric.setSingleLine(true);
        nowLyric.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowLyric.setPadding(0, dp(4), 0, 0);
        nowBox.addView(nowLyric);

        View nowHair = new View(this);
        nowHair.setBackgroundColor(Ui.LINE_SOFT);
        LinearLayout.LayoutParams nhp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        nhp.setMargins(0, dp(8), 0, 0);
        nowBox.addView(nowHair, nhp);
        nowBox.setBackgroundColor(Ui.blend(Ui.BG, Ui.ACCENT, 0.045f));

        Icons menu = new Icons(this).icon(Icons.LIST).color(Ui.MUTED).ratio(0.5f);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(36), dp(36));
        mlp.setMargins(0, 0, dp(6), 0);
        header.addView(menu, mlp);

        TextView brand = text("鲸歌 " + VER, 13, Ui.MUTED, false);
        brand.setLetterSpacing(0.12f);
        header.addView(brand);

        header.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView batchBtn = text("补全", 11, Ui.ACCENT, false);
        batchBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        batchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBatch();
            }
        });
        header.addView(batchBtn);

        TextView nameBtn = text("校名", 11, Ui.MUTED, false);
        nameBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        nameBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNameMatch();
            }
        });
        header.addView(nameBtn);

        TextView rescan = text("刷新", 11, Ui.MUTED, false);
        rescan.setPadding(dp(8), dp(8), dp(8), dp(8));
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasPerm()) scan();
                else requestPerms();
            }
        });
        header.addView(rescan);

        // 搜索：没有框，只有一条底线。
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(39));
        swp.setMargins(dp(20), dp(18), dp(20), 0);
        top.addView(searchWrap, swp);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        Icons mag = new Icons(this).icon(Icons.SEARCH).color(Ui.MUTED).ratio(0.5f);
        searchRow.addView(mag, new LinearLayout.LayoutParams(dp(16), dp(16)));

        search = new EditText(this);
        search.setBackground(null);
        search.setHint("搜索");
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(13);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(10), 0, 0, 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyQuery(s.toString());
            }
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        View searchLine = new View(this);
        searchLine.setBackgroundColor(Ui.LINE);
        searchWrap.addView(searchLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        backRow = new LinearLayout(this);
        backRow.setOrientation(LinearLayout.HORIZONTAL);
        backRow.setGravity(Gravity.CENTER_VERTICAL);
        backRow.setPadding(dp(12), 0, dp(20), 0);
        backRow.setVisibility(View.GONE);
        backRow.setMinimumHeight(dp(40));
        top.addView(backRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        Icons back = new Icons(this).icon(Icons.CHEVRON_LEFT).color(Ui.TEXT).ratio(0.44f);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backToTabs();
            }
        });
        backRow.addView(back, new LinearLayout.LayoutParams(dp(36), dp(36)));
        backTitle = text("", 13, Ui.TEXT, false);
        backTitle.setSingleLine(true);
        backTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        backRow.addView(backTitle);

        // 迷你条先建，列表要"在它上面"
        miniBar = buildMini();
        miniBar.setId(View.generateViewId());
        miniBar.setMinimumHeight(dp(60));
        RelativeLayout.LayoutParams mbLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        page.addView(miniBar, mbLp);

        // 列表 + 空状态
        FrameLayout listBox = new FrameLayout(this);
        RelativeLayout.LayoutParams lbLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lbLp.addRule(RelativeLayout.BELOW, top.getId());
        lbLp.addRule(RelativeLayout.ABOVE, miniBar.getId());
        page.addView(listBox, lbLp);

        listView = new ListView(this);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listView.setSelector(new ColorDrawable(Ui.RIPPLE));
        listView.setPadding(0, dp(6), 0, dp(6));
        listBox.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyBox = buildEmpty();
        listBox.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(18));
        grid.setPadding(dp(20), dp(6), dp(20), dp(8));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(0x00000000));
        grid.setVerticalScrollBarEnabled(false);
        grid.setVisibility(View.GONE);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < groups.size()) openGroup(groups.get(pos));
            }
        });
        listBox.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 侧边栏
        drawerW = (int) (getResources().getDisplayMetrics().widthPixels * 0.76f);
        drawerScrim = new FrameLayout(this);
        drawerScrim.setBackgroundColor(0x99000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        root.addView(drawerScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Ui.BG);
        drawer.setPadding(0, dp(28), 0, 0);
        FrameLayout.LayoutParams drp = new FrameLayout.LayoutParams(
                drawerW, ViewGroup.LayoutParams.MATCH_PARENT);
        drp.gravity = Gravity.START;
        root.addView(drawer, drp);
        drawer.setTranslationX(-drawerW);

        TextView dtitle = text("鲸歌 " + VER, 15, Ui.TEXT, false);
        dtitle.setPadding(dp(24), dp(8), dp(24), dp(2));
        drawer.addView(dtitle);

        TextView dsub = text("JINGGE · LOCAL · PLAYER", 9, Ui.DIM, false);
        dsub.setLetterSpacing(0.28f);
        dsub.setPadding(dp(24), 0, dp(24), dp(18));
        drawer.addView(dsub);

        String[] cats = {"歌曲", "专辑", "艺术家", "文件夹"};
        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            LinearLayout navRow = new LinearLayout(this);
            navRow.setOrientation(LinearLayout.HORIZONTAL);
            navRow.setGravity(Gravity.CENTER_VERTICAL);

            View bar = new View(this);
            bar.setBackgroundColor(Ui.ACCENT);
            bar.setVisibility(View.INVISIBLE);
            navRow.addView(bar, new LinearLayout.LayoutParams(dp(2), dp(18)));

            TextView item = text(cats[i], 15, Ui.MUTED, false);
            item.setPadding(dp(22), dp(13), dp(24), dp(13));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawer();
                    setMode(idx);
                }
            });
            navRow.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            drawer.addView(navRow);
            navItems[i] = item;
            navBars[i] = bar;
        }

        View dline = new View(this);
        dline.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp2.setMargins(dp(24), dp(14), dp(24), dp(8));
        drawer.addView(dline, dlp2);

        TextView aiItem = text("AI 写词", 15, Ui.MUTED, false);
        aiItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        aiItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                openAiForCurrent();
            }
        });
        drawer.addView(aiItem);

        TextView settingsItem = text("设置", 15, Ui.MUTED, false);
        settingsItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        settingsItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                showSettings();
            }
        });
        drawer.addView(settingsItem);

        TextView aboutItem = text("关于", 15, Ui.MUTED, false);
        aboutItem.setPadding(dp(24), dp(14), dp(24), dp(14));
        aboutItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                showAbout();
            }
        });
        drawer.addView(aboutItem);

        playerPage = buildPlayer();
        root.addView(playerPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playerPage.setVisibility(View.GONE);

        return root;
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(40), 0, dp(40), dp(40));

        emptyTitle = text("", 15, Ui.TEXT, false);
        emptyTitle.setGravity(Gravity.CENTER);
        box.addView(emptyTitle);

        emptySub = text("", 12, Ui.MUTED, false);
        emptySub.setGravity(Gravity.CENTER);
        emptySub.setPadding(0, dp(10), 0, 0);
        box.addView(emptySub);

        emptyBtn = text("", 13, Ui.ACCENT, false);
        emptyBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(20), 0, 0);
        emptyBtn.setLayoutParams(blp);
        emptyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onEmptyAction();
            }
        });
        box.addView(emptyBtn);
        return box;
    }

    private View buildMini() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(Ui.BG);

        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        wrap.addView(line, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(9), dp(8), dp(9));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(1);
        wrap.addView(bar, blp);
        wrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPlayer();
            }
        });

        miniCover = new CoverView(this);
        bar.addView(miniCover, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(dp(12), 0, dp(6), 0);
        bar.addView(texts, tlp);

        miniTitle = text("", 13, Ui.TEXT, false);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(miniTitle);

        miniArtist = text("", 11, Ui.MUTED, false);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniArtist.setPadding(0, dp(2), 0, 0);
        texts.addView(miniArtist);

        miniPlay = new Icons(this).icon(Icons.PLAY).color(Ui.TEXT).ratio(0.46f);
        miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svc != null) svc.toggle();
            }
        });
        bar.addView(miniPlay, new LinearLayout.LayoutParams(dp(44), dp(44)));

        Icons next = new Icons(this).icon(Icons.NEXT).color(Ui.MUTED).ratio(0.44f);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svc != null) svc.next(false);
            }
        });
        bar.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return wrap;
    }

    private View buildPlayer() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(20), 0, dp(20), dp(16));
        p.setBackgroundColor(Ui.BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(8), 0, 0);
        p.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        Icons down = new Icons(this).icon(Icons.CHEVRON_DOWN).color(Ui.TEXT).ratio(0.46f);
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePlayer();
            }
        });
        head.addView(down, new LinearLayout.LayoutParams(dp(44), dp(44)));

        head.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        transBtn = text("译", 12, showTrans ? Ui.ACCENT : Ui.DIM, true);
        transBtn.setGravity(Gravity.CENTER);
        transBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        transBtn.setBackground(Ui.roundStroke(0, showTrans ? Ui.ACCENT : Ui.LINE, dp(8), dp(1)));
        transBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTrans();
            }
        });
        head.addView(transBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        vinyl = new VinylView(this);
        vinyl.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showSongMenu(svc != null ? svc.currentSong() : null);
                return true;
            }
        });
        p.addView(vinyl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(196)));

        lyricView = new LyricView(this);
        lyricView.setOnSeek(new LyricView.OnSeek() {
            @Override
            public void onSeek(int ms) {
                if (svc != null) svc.seekTo(ms);
                lyricView.setPosition(ms);
            }
        });
        lyricView.setClock(new LyricView.Clock() {
            @Override
            public int now() {
                return svc == null ? 0 : svc.position();
            }
        });
        p.addView(lyricView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pTitle = text("", 20, Ui.TEXT, true);
        pTitle.setGravity(Gravity.CENTER);
        pTitle.setSingleLine(true);
        pTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.addView(pTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pArtist = text("", 12, Ui.MUTED, false);
        pArtist.setGravity(Gravity.CENTER);
        pArtist.setSingleLine(true);
        pArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pArtist.setPadding(0, dp(8), 0, 0);
        p.addView(pArtist, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seek = new ThinSeek(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        slp.setMargins(0, dp(18), 0, 0);
        p.addView(seek, slp);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(times, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tNow = text("0:00", 11, Ui.DIM, false);
        times.addView(tNow, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tAll = text("0:00", 11, Ui.DIM, false);
        times.addView(tAll);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        clp.setMargins(0, dp(14), 0, 0);
        p.addView(ctrl, clp);

        modeIcon = new Icons(this).icon(Icons.REPEAT).color(Ui.DIM).ratio(0.44f);
        modeIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svc != null) svc.cycleMode();
            }
        });
        ctrl.addView(modeIcon, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Icons prev = new Icons(this).icon(Icons.PREV).color(Ui.TEXT).ratio(0.46f);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svc != null) svc.prev();
            }
        });
        ctrl.addView(prev, new LinearLayout.LayoutParams(0, dp(48), 1f));

        bigPlay = new Icons(this).icon(Icons.PLAY).color(Ui.TEXT).ratio(0.44f);
        bigPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svc != null) svc.toggle();
            }
        });
        ctrl.addView(bigPlay, new LinearLayout.LayoutParams(dp(76), dp(76)));

        Icons next = new Icons(this).icon(Icons.NEXT).color(Ui.TEXT).ratio(0.46f);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svc != null) svc.next(false);
            }
        });
        ctrl.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Icons listIcon = new Icons(this).icon(Icons.LIST).color(Ui.DIM).ratio(0.44f);
        listIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePlayer();
            }
        });
        ctrl.addView(listIcon, new LinearLayout.LayoutParams(0, dp(48), 1f));

        return p;
    }

    // ───────────────────────── 列表 ─────────────────────────

    private void applyQuery(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (mode == MODE_SONG || mode == MODE_GROUP) {
            display.clear();
            if (mode == MODE_SONG) {
                for (Song s : all) if (matchSong(s, needle)) display.add(s);
            } else if (currentGroup != null) {
                for (Song s : currentGroup.songs) if (matchSong(s, needle)) display.add(s);
            }
            showAdapter(adapter);
        } else {
            groups.clear();
            buildGroups(mode, needle);
            showAdapter(mode == MODE_ALBUM ? albumAdapter : groupAdapter);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
        if (albumAdapter != null) albumAdapter.notifyDataSetChanged();
        updateEmpty();
    }

    private void showAdapter(BaseAdapter a) {
        boolean album = a == albumAdapter;
        grid.setVisibility(album ? View.VISIBLE : View.GONE);
        listView.setVisibility(album ? View.GONE : View.VISIBLE);
        if (album) {
            emptyBox.setVisibility(View.GONE);
            if (grid.getAdapter() != a) grid.setAdapter(a);
            return;
        }
        if (listView.getAdapter() != a) listView.setAdapter(a);
        listView.setEmptyView(emptyBox);
    }

    private static boolean matchSong(Song s, String needle) {
        if (needle.length() == 0) return true;
        return s.title.toLowerCase(Locale.ROOT).contains(needle)
                || (s.artist != null && s.artist.toLowerCase(Locale.ROOT).contains(needle))
                || (s.album != null && s.album.toLowerCase(Locale.ROOT).contains(needle));
    }

    private void buildGroups(int m, String needle) {
        TreeMap<String, List<Song>> map = new TreeMap<String, List<Song>>(String.CASE_INSENSITIVE_ORDER);
        for (Song s : all) {
            String key;
            if (m == MODE_ALBUM) key = s.album == null ? "未知专辑" : s.album;
            else if (m == MODE_ARTIST) key = s.artist == null ? "未知艺术家" : s.artist;
            else key = s.folder == null ? "其他" : s.folder;
            if (needle.length() > 0 && !key.toLowerCase(Locale.ROOT).contains(needle)) continue;
            List<Song> l = map.get(key);
            if (l == null) {
                l = new ArrayList<Song>();
                map.put(key, l);
            }
            l.add(s);
        }
        for (Map.Entry<String, List<Song>> e : map.entrySet()) {
            groups.add(new Group(e.getKey(), e.getValue()));
        }
    }

    private void setMode(int m) {
        if (m == mode && m != MODE_GROUP) return;
        mode = m;
        lastTab = m;
        currentGroup = null;
        backRow.setVisibility(View.GONE);
        updateNav();
        applyQuery(search.getText().toString());
    }

    private void openGroup(Group g) {
        if (g == null) return;
        currentGroup = g;
        mode = MODE_GROUP;
        backRow.setVisibility(View.VISIBLE);
        backTitle.setText(g.title);
        search.setText("");
        applyQuery("");
        listView.setSelection(0);
    }

    private void backToTabs() {
        mode = lastTab;
        currentGroup = null;
        backRow.setVisibility(View.GONE);
        updateNav();
        search.setText("");
        applyQuery("");
        listView.setSelection(0);
    }

    private void updateNav() {
        for (int i = 0; i < navItems.length; i++) {
            if (navItems[i] == null) continue;
            boolean on = mode == i;
            navItems[i].setTextColor(on ? Ui.TEXT : Ui.MUTED);
            navItems[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (navBars[i] != null) {
                navBars[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        drawerScrim.setVisibility(View.VISIBLE);
        drawerScrim.setAlpha(0f);
        drawerScrim.animate().alpha(1f).setDuration(160).start();
        drawer.animate().translationX(0f).setDuration(200).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-drawerW).setDuration(180).start();
        drawerScrim.animate().alpha(0f).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        drawerScrim.setVisibility(View.GONE);
                    }
                }).start();
    }

    private class SongAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return display.size();
        }

        @Override
        public Song getItem(int i) {
            return display.get(i);
        }

        @Override
        public long getItemId(int i) {
            return display.get(i).id;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Row r;
            if (convert == null) {
                r = new Row();
                convert = r.build();
                convert.setTag(r);
            } else {
                r = (Row) convert.getTag();
            }
            Song s = getItem(pos);
            Song cur = svc == null ? null : svc.currentSong();
            boolean active = cur != null && cur.id == s.id;

            r.title.setText(s.title);
            r.title.setTextColor(active ? Ui.ACCENT : Ui.TEXT);
            r.sub.setText(s.subtitle());
            r.dur.setText(Song.fmtDuration(s.durationMs));
            loadCoverAsync(s, r.cover, 128, smallCovers, false);
            return convert;
        }
    }

    private class GroupAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return groups.size();
        }

        @Override
        public Group getItem(int i) {
            return groups.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            GroupRow r;
            if (convert == null) {
                r = new GroupRow();
                convert = r.build();
                convert.setTag(r);
            } else {
                r = (GroupRow) convert.getTag();
            }
            Group g = getItem(pos);
            r.title.setText(g.title);
            r.sub.setText(g.songs.size() + " 首");
            if (mode == MODE_ARTIST) {
                r.icon.setVisibility(View.GONE);
                r.avatar.setVisibility(View.VISIBLE);
                if (!g.songs.isEmpty()) loadCoverAsync(g.songs.get(0), r.avatar, 128, smallCovers, false);
            } else {
                r.avatar.setVisibility(View.GONE);
                r.icon.setVisibility(View.VISIBLE);
            }
            return convert;
        }
    }

    private class AlbumAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return groups.size();
        }

        @Override
        public Group getItem(int i) {
            return groups.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            AlbumCell c;
            int w = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight() - dp(12);
            w = w / 2;
            if (w <= 0) w = dp(150);
            if (convert == null) {
                c = new AlbumCell();
                convert = c.build();
                convert.setTag(c);
            } else {
                c = (AlbumCell) convert.getTag();
            }
            c.cover.setLayoutParams(new LinearLayout.LayoutParams(w, w));
            convert.setLayoutParams(new AbsListView.LayoutParams(w, w + dp(54)));
            Group g = getItem(pos);
            c.title.setText(g.title);
            c.sub.setText(g.songs.size() + " 首");
            if (!g.songs.isEmpty()) loadCoverAsync(g.songs.get(0), c.cover, 512, albumCovers, true);
            return convert;
        }
    }

    private class AlbumCell {
        CoverView cover;
        TextView title, sub;

        View build() {
            LinearLayout v = new LinearLayout(MainActivity.this);
            v.setOrientation(LinearLayout.VERTICAL);
            cover = new CoverView(MainActivity.this);
            cover.setRadiusDp(4);
            v.addView(cover, new LinearLayout.LayoutParams(dp(150), dp(150)));
            title = text("", 13, Ui.TEXT, false);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(0, dp(8), 0, 0);
            v.addView(title);
            sub = text("", 11, Ui.MUTED, false);
            sub.setPadding(0, dp(2), 0, 0);
            v.addView(sub);
            return v;
        }
    }

    /** 专辑网格 / 列表 / 头像：先用内嵌，再缓存；allowNet 时最后联网。 */
    private void loadCoverAsync(final Song s, final CoverView view, final int px,
                                final LruCache<Long, Bitmap> cache, final boolean allowNet) {
        final long key = s.id;
        view.setTag(key);
        view.setPlaceholder(s.title);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            view.setBmp(hit);
            return;
        }
        view.setBmp(null);
        coverPool.submit(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = MusicRepo.cover(MainActivity.this, s, px);
                if (bm == null) bm = CoverStore.loadCache(MainActivity.this, key, px);
                final Bitmap found = bm;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (found == null) {
                            if (allowNet) fetchAlbumOnline(s, key, view, cache);
                            return;
                        }
                        cache.put(key, found);
                        Object tag = view.getTag();
                        if (tag != null && tag.equals(key)) view.setBmp(found);
                    }
                });
            }
        });
    }

    private void fetchAlbumOnline(final Song s, final long key, final CoverView view,
                                  final LruCache<Long, Bitmap> cache) {
        CoverFinder.find(this, s, new CoverFinder.Callback() {
            @Override
            public void onCover(Bitmap bm) {
                if (bm == null) return;
                cache.put(key, bm);
                Object tag = view.getTag();
                if (tag != null && tag.equals(key)) view.setBmp(bm);
            }
        });
    }

    private class GroupRow {
        TextView title, sub;
        CoverView avatar;
        Icons icon;

        View build() {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(10), dp(20), dp(10));
            row.setMinimumHeight(dp(60));

            avatar = new CoverView(MainActivity.this);
            avatar.setRadiusDp(22);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(44));
            alp.setMargins(0, 0, dp(12), 0);
            row.addView(avatar, alp);

            icon = new Icons(MainActivity.this).icon(Icons.FOLDER).color(Ui.DIM).ratio(0.6f);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
            ilp.setMargins(0, 0, dp(12), 0);
            row.addView(icon, ilp);

            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            title = text("", 15, Ui.TEXT, false);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(title);

            sub = text("", 11, Ui.MUTED, false);
            sub.setPadding(0, dp(3), 0, 0);
            col.addView(sub);

            Icons chev = new Icons(MainActivity.this)
                    .icon(Icons.CHEVRON_LEFT).color(Ui.DIM).ratio(0.4f);
            chev.setRotation(180f);
            row.addView(chev, new LinearLayout.LayoutParams(dp(20), dp(20)));
            return row;
        }
    }

    private class Row {
        TextView title, sub, dur;
        CoverView cover;

        View build() {
            LinearLayout outer = new LinearLayout(MainActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(8), dp(20), dp(8));
            row.setMinimumHeight(dp(60));
            outer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            cover = new CoverView(MainActivity.this);
            cover.setRadiusDp(4);
            LinearLayout.LayoutParams cvp = new LinearLayout.LayoutParams(dp(44), dp(44));
            cvp.setMargins(0, 0, dp(12), 0);
            row.addView(cover, cvp);

            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMargins(0, 0, dp(14), 0);
            row.addView(col, clp);

            title = text("", 15, Ui.TEXT, false);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(title);

            sub = text("", 11, Ui.MUTED, false);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(3), 0, 0);
            col.addView(sub);

            dur = text("", 11, Ui.DIM, false);
            row.addView(dur);

            View hair = new View(MainActivity.this);
            hair.setBackgroundColor(Ui.LINE_SOFT);
            outer.addView(hair, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            return outer;
        }
    }

    // ───────────────────────── 小封面 ─────────────────────────

    private class CoverView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Matrix m = new Matrix();
        private Bitmap bmp;
        private BitmapShader shader;
        private float radiusDp = 2f;
        private String placeholder = "";

        CoverView(Context c) {
            super(c);
        }

        void setRadiusDp(float v) {
            radiusDp = v;
            invalidate();
        }

        void setPlaceholder(String s) {
            placeholder = s == null ? "" : s;
            invalidate();
        }

        void setBmp(Bitmap b) {
            bmp = b;
            shader = b == null ? null
                    : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            float radius = dp(radiusDp);
            r.set(0, 0, w, h);
            if (shader != null && bmp != null) {
                float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
                m.reset();
                m.setScale(scale, scale);
                m.postTranslate((w - bmp.getWidth() * scale) / 2f,
                        (h - bmp.getHeight() * scale) / 2f);
                shader.setLocalMatrix(m);
                p.setShader(shader);
                c.drawRoundRect(r, radius, radius, p);
                p.setShader(null);
            } else {
                p.setShader(null);
                p.setColor(Ui.PANEL_UP);
                c.drawRoundRect(r, radius, radius, p);
                int m = Math.min(w, h);
                // 没有封面时用歌名首字 + 一颗青点，这是鲸歌自己的占位
                if (placeholder.length() > 0) {
                    p.setColor((Ui.TEXT & 0x00FFFFFF) | 0x59000000);
                    p.setTextSize(m * 0.36f);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setFakeBoldText(true);
                    String one = placeholder.substring(0, 1).toUpperCase(Locale.ROOT);
                    c.drawText(one, w / 2f, h / 2f + m * 0.13f, p);
                    p.setFakeBoldText(false);
                }
                p.setColor(0x6658E0C8);
                c.drawCircle(w * 0.8f, h * 0.2f, m * 0.05f, p);
            }
            // 头发丝描边：让封面不和背景糊在一起
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, dp(0.6f)));
            p.setColor(Ui.LINE_SOFT);
            c.drawRoundRect(r, radius, radius, p);
            p.setStyle(Paint.Style.FILL);
        }
    }
}
