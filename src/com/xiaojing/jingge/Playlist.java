package com.xiaojing.jingge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自建歌单：存在 SharedPreferences 里的一段 JSON。
 * 只存歌曲 id，歌曲本身仍然由 MusicRepo 扫描得到——歌被删掉，歌单里那条自动失效。
 */
final class Playlist {
    static final class Item {
        String id;
        String name;
        final List<Long> ids = new ArrayList<Long>();

        int size() {
            return ids.size();
        }
    }

    private Playlist() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jingge_sheets", Context.MODE_PRIVATE);
    }

    static List<Item> all(Context c) {
        List<Item> out = new ArrayList<Item>();
        String raw = prefs(c).getString("items", null);
        if (raw == null || raw.length() == 0) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = new Item();
                it.id = o.optString("id", "");
                it.name = o.optString("name", "未命名");
                JSONArray ids = o.optJSONArray("ids");
                if (ids != null) {
                    for (int j = 0; j < ids.length(); j++) it.ids.add(ids.optLong(j));
                }
                if (it.id.length() > 0) out.add(it);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("name", it.name);
                JSONArray ids = new JSONArray();
                for (Long v : it.ids) ids.put(v.longValue());
                o.put("ids", ids);
                arr.put(o);
            }
        } catch (Throwable ignored) {
        }
        prefs(c).edit().putString("items", arr.toString()).apply();
    }

    static Item byId(Context c, String id) {
        if (id == null) return null;
        for (Item it : all(c)) if (id.equals(it.id)) return it;
        return null;
    }

    static Item create(Context c, String name) {
        List<Item> items = all(c);
        Item it = new Item();
        it.id = "l" + System.currentTimeMillis();
        it.name = name == null || name.trim().length() == 0 ? "新歌单" : name.trim();
        items.add(it);
        save(c, items);
        return it;
    }

    static void rename(Context c, String id, String name) {
        List<Item> items = all(c);
        for (Item it : items) {
            if (it.id.equals(id)) {
                it.name = name == null || name.trim().length() == 0 ? it.name : name.trim();
                break;
            }
        }
        save(c, items);
    }

    static void delete(Context c, String id) {
        List<Item> items = all(c);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                break;
            }
        }
        save(c, items);
    }

    /** 加歌：已经在里面的不重复加。返回真正加进去的条数。 */
    static int add(Context c, String id, List<Long> songIds) {
        List<Item> items = all(c);
        int added = 0;
        for (Item it : items) {
            if (!it.id.equals(id)) continue;
            for (Long sid : songIds) {
                if (sid == null) continue;
                boolean has = false;
                for (Long old : it.ids) {
                    if (old.longValue() == sid.longValue()) {
                        has = true;
                        break;
                    }
                }
                if (!has) {
                    it.ids.add(sid);
                    added++;
                }
            }
            break;
        }
        save(c, items);
        return added;
    }

    static void removeSong(Context c, String id, long songId) {
        List<Item> items = all(c);
        for (Item it : items) {
            if (!it.id.equals(id)) continue;
            for (int i = 0; i < it.ids.size(); i++) {
                if (it.ids.get(i).longValue() == songId) {
                    it.ids.remove(i);
                    break;
                }
            }
            break;
        }
        save(c, items);
    }

    /** 按 id 把歌单里的歌捞出来；库里没有的（文件删了）跳过。 */
    static List<Song> songs(Item it, List<Song> library) {
        List<Song> out = new ArrayList<Song>();
        if (it == null || library == null) return out;
        for (Long id : it.ids) {
            for (Song s : library) {
                if (s.id == id.longValue()) {
                    out.add(s);
                    break;
                }
            }
        }
        return out;
    }

    static String countLabel(Item it, List<Song> library) {
        int n = songs(it, library).size();
        int total = it == null ? 0 : it.size();
        if (total > n) return n + " 首（还有 " + (total - n) + " 首找不到文件）";
        return n == 0 ? "空歌单" : n + " 首";
    }
}
