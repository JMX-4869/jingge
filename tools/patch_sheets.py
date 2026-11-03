# -*- coding: utf-8 -*-
import io

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

# ── 版本 ──
s = s.replace('private static final String VER = "0.35";', 'private static final String VER = "0.36";')

# ── 常量：歌单模式 ──
s = s.replace('''    private static final int MODE_SONG = 0, MODE_ALBUM = 1, MODE_ARTIST = 2,
            MODE_FOLDER = 3, MODE_GROUP = 4;''',
'''    private static final int MODE_SONG = 0, MODE_ALBUM = 1, MODE_ARTIST = 2,
            MODE_FOLDER = 3, MODE_GROUP = 4, MODE_SHEET = 5;''')

# ── 导航条多一格 ──
s = s.replace('''    private final TextView[] navItems = new TextView[4];
    private final View[] navBars = new View[4];''',
'''    private final TextView[] navItems = new TextView[5];
    private final View[] navBars = new View[5];''')
s = s.replace('String[] cats = {"歌曲", "专辑", "艺术家", "文件夹"};',
              'String[] cats = {"歌曲", "专辑", "艺术家", "文件夹", "歌单"};')

# ── 字段 ──
s = s.replace('''    private boolean playerOpen, dragging;''',
'''    private boolean playerOpen, dragging;
    private View sheetPage;
    private boolean sheetOpen;
    private LinearLayout sheetRows;
    private TextView sheetTitle, sheetHint;
    private Playlist.Item openedSheet;
    private int beforeSheet = MODE_SONG;''')

# ── 导航点击：第 5 格进歌单 ──
s = s.replace('''                public void onClick(View v) {
                    closeDrawer();
                    setMode(idx);
                }''',
'''                public void onClick(View v) {
                    closeDrawer();
                    setMode(idx == 4 ? MODE_SHEET : idx);
                }''')

# ── setMode 分流 ──
s = s.replace('''    private void setMode(int m) {
        if (m == mode && m != MODE_GROUP) return;
        mode = m;''',
'''    private void setMode(int m) {
        if (m == mode && m != MODE_GROUP) return;
        if (m == MODE_SHEET) {
            beforeSheet = mode >= MODE_SONG && mode <= MODE_FOLDER ? mode : MODE_SONG;
            mode = m;
            lastTab = beforeSheet;
            updateNav();
            openSheets();
            return;
        }
        if (sheetOpen) hideSheets();
        mode = m;''')

# ── 返回键 ──
s = s.replace('''        if (playerOpen) {
            closePlayer();
            return;
        }''',
'''        if (playerOpen) {
            closePlayer();
            return;
        }
        if (sheetOpen) {
            if (openedSheet != null) {
                openedSheet = null;
                renderSheets();
            } else {
                closeSheets();
            }
            return;
        }''')

# ── 扫描完成后刷新歌单页 ──
s = s.replace('''    private void onSongs(List<Song> got) {
        all.clear();
        all.addAll(got);
        applyQuery(search.getText().toString());''',
'''    private void onSongs(List<Song> got) {
        all.clear();
        all.addAll(got);
        applyQuery(search.getText().toString());
        if (sheetOpen) {
            if (openedSheet != null) {
                Playlist.Item keep = Playlist.byId(this, openedSheet.id);
                if (keep != null) openSheet(keep);
                else renderSheets();
            } else {
                renderSheets();
            }
        }''')

# ── 播放页：不息屏开关 ──
s = s.replace('''        if (lyricView != null) lyricView.start();
        syncNow();
    }''',
'''        if (lyricView != null) lyricView.start();
        syncNow();
        syncKeepOn();
    }

    private boolean keepOn() {
        return Ai.prefs(this).getBoolean("keep_on", true);
    }

    private void syncKeepOn() {
        if (playerOpen && keepOn()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }''', 1)

s = s.replace('''    private void closePlayer() {
        if (!playerOpen) return;
        playerOpen = false;''',
'''    private void closePlayer() {
        if (!playerOpen) return;
        playerOpen = false;
        syncKeepOn();''')

# ── 设置：不息屏开关 ──
s = s.replace('''        TextView tl = text("主题 · 点一下就用", 11, Ui.DIM, false);''',
'''        TextView kl = text("播放页", 11, Ui.DIM, false);
        kl.setPadding(0, dp(14), 0, dp(6));
        box.addView(kl);

        LinearLayout krow = new LinearLayout(this);
        krow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(krow);
        krow.addView(keepChip(d, "不息屏", true));
        krow.addView(keepChip(d, "允许息屏", false));

        TextView tl = text("主题 · 点一下就用", 11, Ui.DIM, false);''', 1)

# ── keepChip 方法 ──
s = s.replace('''    private View styleChip(final Dialog d, final String name, final int style, int cur) {''',
'''    private View keepChip(final Dialog d, final String name, final boolean on) {
        final boolean cur = keepOn() == on;
        TextView t = text(name, 13, cur ? Ui.ACCENT : Ui.MUTED, cur);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        t.setBackground(Ui.roundStroke(0, cur ? Ui.ACCENT : Ui.LINE, dp(10), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ai.prefs(MainActivity.this).edit().putBoolean("keep_on", on).apply();
                d.dismiss();
                syncKeepOn();
            }
        });
        return t;
    }

    private View styleChip(final Dialog d, final String name, final int style, int cur) {''', 1)

# ── 歌单页挂到 root ──
s = s.replace('''        playerPage.setVisibility(View.GONE);

        return root;''',
'''        playerPage.setVisibility(View.GONE);

        sheetPage = buildSheets();
        root.addView(sheetPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sheetPage.setVisibility(View.GONE);

        return root;''')

# ── 长按菜单加入口 ──
s = s.replace('''        box.addView(menuRow("AI 生成翻译歌词", Ui.TEXT, false, new Runnable() {
            public void run() {
                aiTransMenu(s);
            }
        }, d));''',
'''        box.addView(menuRow("AI 生成翻译歌词", Ui.TEXT, false, new Runnable() {
            public void run() {
                aiTransMenu(s);
            }
        }, d));
        box.addView(menuRow("添加到歌单…", Ui.TEXT, false, new Runnable() {
            public void run() {
                addToSheetDialog(s);
            }
        }, d));''')

# ── 歌单相关方法 ──
anchor = '''    private void confirmDelete(final Song s) {'''
methods = '''    // ───────────────────────── 歌单 ─────────────────────────

    private View buildSheets() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        page.setPadding(0, dp(26), 0, 0);
        page.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(4), dp(16), dp(10));
        page.addView(head);

        TextView back = text("←", 20, Ui.MUTED, false);
        back.setPadding(dp(6), dp(4), dp(12), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheetBack();
            }
        });
        head.addView(back);

        sheetTitle = text("歌单", 18, Ui.TEXT, true);
        head.addView(sheetTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = text("＋ 新建", 13, Ui.ACCENT, true);
        add.setPadding(dp(10), dp(6), dp(4), dp(6));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newSheetDialog();
            }
        });
        head.addView(add);

        sheetHint = text("", 12, Ui.MUTED, false);
        sheetHint.setPadding(dp(22), 0, dp(22), dp(8));
        page.addView(sheetHint);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sheetRows = new LinearLayout(this);
        sheetRows.setOrientation(LinearLayout.VERTICAL);
        sheetRows.setPadding(0, 0, 0, dp(28));
        sv.addView(sheetRows, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void openSheets() {
        sheetOpen = true;
        openedSheet = null;
        sheetPage.setVisibility(View.VISIBLE);
        sheetPage.setAlpha(0f);
        sheetPage.animate().alpha(1f).setDuration(180).start();
        renderSheets();
    }

    private void hideSheets() {
        sheetOpen = false;
        openedSheet = null;
        if (sheetPage != null) sheetPage.setVisibility(View.GONE);
    }

    private void closeSheets() {
        hideSheets();
        mode = beforeSheet;
        lastTab = beforeSheet;
        updateNav();
        applyQuery(search.getText().toString());
    }

    private void sheetBack() {
        if (openedSheet != null) {
            openedSheet = null;
            renderSheets();
        } else {
            closeSheets();
        }
    }

    private void addSplit() {
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE_SOFT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(20), 0, dp(20), 0);
        sheetRows.addView(line, lp);
    }

    private void renderSheets() {
        if (sheetRows == null) return;
        openedSheet = null;
        sheetRows.removeAllViews();
        sheetTitle.setText("歌单");

        final java.util.List<Playlist.Item> items = Playlist.all(this);
        sheetHint.setText(items.isEmpty() ? "还没有歌单，点右上角「＋ 新建」" : items.size() + " 个歌单");

        if (items.isEmpty()) {
            TextView tip = text("把一首歌长按 →「添加到歌单…」，就能攒起来", 13, Ui.DIM, false);
            tip.setPadding(dp(22), dp(30), dp(22), dp(10));
            sheetRows.addView(tip);
            return;
        }

        for (final Playlist.Item it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(22), dp(14), dp(12), dp(14));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSheet(it);
                }
            });

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(it.name, 15, Ui.TEXT, false);
            TextView cnt = text(Playlist.countLabel(it, all), 11, Ui.MUTED, false);
            cnt.setPadding(0, dp(4), 0, 0);
            txt.addView(name);
            txt.addView(cnt);
            row.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView more = text("⋯", 19, Ui.MUTED, false);
            more.setPadding(dp(14), 0, dp(10), 0);
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSheetMenu(it);
                }
            });
            row.addView(more);

            sheetRows.addView(row);
            addSplit();
        }
    }

    private void openSheet(final Playlist.Item it) {
        if (it == null) return;
        Playlist.Item fresh = Playlist.byId(this, it.id);
        if (fresh == null) {
            renderSheets();
            return;
        }
        openedSheet = fresh;
        sheetRows.removeAllViews();
        sheetTitle.setText(fresh.name);

        final java.util.List<Song> songs = Playlist.songs(fresh, all);
        sheetHint.setText(songs.isEmpty() ? "空歌单" : songs.size() + " 首 · 点歌就播");

        if (!songs.isEmpty()) {
            TextView playAll = text("▶ 播放全部", 13, Ui.ACCENT, true);
            playAll.setPadding(dp(22), dp(14), dp(22), dp(14));
            playAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playSheet(fresh, -1);
                }
            });
            sheetRows.addView(playAll);
            addSplit();
        }

        for (int i = 0; i < songs.size(); i++) {
            final Song s = songs.get(i);
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(22), dp(13), dp(10), dp(13));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playSheet(fresh, idx);
                }
            });

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(s.title == null ? "未命名" : s.title, 14, Ui.TEXT, false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView sub = text(s.subtitle(), 11, Ui.MUTED, false);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(4), 0, 0);
            txt.addView(name);
            txt.addView(sub);
            row.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = text("×", 18, Ui.DIM, false);
            del.setPadding(dp(14), 0, dp(12), 0);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Playlist.removeSong(MainActivity.this, fresh.id, s.id);
                    openSheet(fresh);
                }
            });
            row.addView(del);

            sheetRows.addView(row);
            addSplit();
        }
    }

    private void playSheet(Playlist.Item it, int start) {
        java.util.List<Song> songs = Playlist.songs(it, all);
        if (songs.isEmpty()) {
            Toast.makeText(this, "歌单里没有能放的歌", Toast.LENGTH_SHORT).show();
            return;
        }
        if (svc == null) return;
        int at = start < 0 || start >= songs.size() ? 0 : start;
        svc.setQueue(songs, songs.get(at).id);
        svc.playById(songs.get(at).id);
        openPlayer();
    }

    private void showSheetMenu(final Playlist.Item it) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.setPadding(0, dp(14), 0, dp(6));
        TextView head = text(it.name, 15, Ui.TEXT, true);
        head.setPadding(dp(20), 0, dp(20), dp(6));
        box.addView(head);
        TextView sub = text(Playlist.countLabel(it, all), 11, Ui.MUTED, false);
        sub.setPadding(dp(20), 0, dp(20), dp(8));
        box.addView(sub);

        box.addView(menuRow("播放全部", Ui.TEXT, false, new Runnable() {
            public void run() {
                playSheet(it, -1);
            }
        }, d));
        box.addView(menuRow("重命名", Ui.TEXT, false, new Runnable() {
            public void run() {
                renameSheetDialog(it);
            }
        }, d));
        box.addView(menuRow("删除歌单", danger(), true, new Runnable() {
            public void run() {
                Playlist.delete(MainActivity.this, it.id);
                Toast.makeText(MainActivity.this, "删掉了歌单「" + it.name + "」",
                        Toast.LENGTH_SHORT).show();
                renderSheets();
            }
        }, d));

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void newSheetDialog() {
        sheetNameDialog(null);
    }

    private void renameSheetDialog(final Playlist.Item it) {
        sheetNameDialog(it);
    }

    private void sheetNameDialog(final Playlist.Item it) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text(it == null ? "新建歌单" : "重命名歌单", 15, Ui.TEXT, true));
        final EditText e = field(it == null ? "" : it.name, "歌单名");
        box.addView(e);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text(it == null ? "建立" : "改名", 13, Ui.ACCENT, true);
        ok.setPadding(dp(12), dp(8), dp(12), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = e.getText().toString().trim();
                if (name.length() == 0) {
                    Toast.makeText(MainActivity.this, "名字不能空着", Toast.LENGTH_SHORT).show();
                    return;
                }
                d.dismiss();
                if (it == null) {
                    Playlist.create(MainActivity.this, name);
                    renderSheets();
                } else {
                    Playlist.rename(MainActivity.this, it.id, name);
                    renderSheets();
                }
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void addToSheetDialog(final Song s) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.setPadding(0, dp(14), 0, dp(6));
        TextView head = text("添加到歌单", 15, Ui.TEXT, true);
        head.setPadding(dp(20), 0, dp(20), dp(8));
        box.addView(head);

        final java.util.List<Playlist.Item> items = Playlist.all(this);
        for (final Playlist.Item it : items) {
            boolean has = false;
            for (Long id : it.ids) {
                if (id.longValue() == s.id) {
                    has = true;
                    break;
                }
            }
            final boolean already = has;
            box.addView(menuRow(it.name + (already ? "（已在里面）" : ""),
                    already ? Ui.DIM : Ui.TEXT, false, new Runnable() {
                        public void run() {
                            if (already) {
                                Toast.makeText(MainActivity.this, "这首歌已经在这个歌单里了",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            java.util.List<Long> one = new java.util.ArrayList<Long>();
                            one.add(s.id);
                            Playlist.add(MainActivity.this, it.id, one);
                            Toast.makeText(MainActivity.this,
                                    "已加入「" + it.name + "」", Toast.LENGTH_SHORT).show();
                        }
                    }, d));
        }

        if (!items.isEmpty()) box.addView(hair());
        box.addView(menuRow("＋ 新建歌单…", Ui.ACCENT, true, new Runnable() {
            public void run() {
                newSheetWithSong(s);
            }
        }, d));

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

    private void newSheetWithSong(final Song s) {
        final Dialog d = new Dialog(this);
        LinearLayout box = dialogBox();
        box.addView(text("新建歌单", 15, Ui.TEXT, true));
        final EditText e = field("", "歌单名");
        box.addView(e);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        box.addView(row);

        TextView cancel = text("取消", 13, Ui.MUTED, false);
        cancel.setPadding(dp(12), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        row.addView(cancel);

        TextView ok = text("建立", 13, Ui.ACCENT, true);
        ok.setPadding(dp(12), dp(8), dp(12), dp(8));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = e.getText().toString().trim();
                if (name.length() == 0) {
                    Toast.makeText(MainActivity.this, "名字不能空着", Toast.LENGTH_SHORT).show();
                    return;
                }
                d.dismiss();
                Playlist.Item it = Playlist.create(MainActivity.this, name);
                java.util.List<Long> one = new java.util.ArrayList<Long>();
                one.add(s.id);
                Playlist.add(MainActivity.this, it.id, one);
                Toast.makeText(MainActivity.this, "已建「" + name + "」并加进去",
                        Toast.LENGTH_SHORT).show();
                if (sheetOpen) renderSheets();
            }
        });
        row.addView(ok);

        d.setContentView(box);
        dialogWindow(d);
        d.show();
    }

'''
assert anchor in s
s = s.replace(anchor, methods + anchor, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('android:versionCode="35"', 'android:versionCode="36"')
s = s.replace('android:versionName="0.35"', 'android:versionName="0.36"')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('patched')
