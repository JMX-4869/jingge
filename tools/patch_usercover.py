# -*- coding: utf-8 -*-
import io

p = 'src/com/xiaojing/jingge/MainActivity.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('private static final String VER = "0.32";', 'private static final String VER = "0.33";')

# 列表取封面：用户自己传的优先
old = '''                Bitmap bm = MusicRepo.cover(MainActivity.this, s, px);
                if (bm == null) bm = CoverStore.loadCache(MainActivity.this, key, px);'''
new = '''                Bitmap bm = CoverStore.loadUser(MainActivity.this, key, px);
                if (bm == null) bm = MusicRepo.cover(MainActivity.this, s, px);
                if (bm == null) bm = CoverStore.loadCache(MainActivity.this, key, px);'''
assert old in s
s = s.replace(old, new, 1)

# 换封面/清封面之后，把缓存里的旧图清掉，列表才会重新读
old = '''    private void resetCoverIfCurrent(long id) {
        if (coverId != id) return;'''
new = '''    private void resetCoverIfCurrent(long id) {
        smallCovers.remove(id);
        albumCovers.remove(id);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (coverId != id) return;'''
assert old in s
s = s.replace(old, new, 1)

# 存完自定义封面也清一次
old = '''                        if (svc != null && svc.currentSong() != null
                                && svc.currentSong().id == id) {
                            coverId = -1;
                            refreshCover(svc.currentSong());
                        }'''
new = '''                        smallCovers.remove(id);
                        albumCovers.remove(id);
                        if (adapter != null) adapter.notifyDataSetChanged();
                        if (svc != null && svc.currentSong() != null
                                && svc.currentSong().id == id) {
                            coverId = -1;
                            refreshCover(svc.currentSong());
                        }'''
assert old in s
s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)

p = 'AndroidManifest.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('android:versionCode="32"', 'android:versionCode="33"')
s = s.replace('android:versionName="0.32"', 'android:versionName="0.33"')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('ok')
