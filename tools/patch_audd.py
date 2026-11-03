# -*- coding: utf-8 -*-
"""0.53：识曲只留 ACRCloud；「测一下」按返回码说人话（3001 = 签名其实过了）"""
import io, re

def rw(p, fn):
    s = io.open(p, encoding='utf-8').read()
    s2 = fn(s)
    assert s2 != s, 'no change: ' + p
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s2)

# ─────────── Recognize.java ───────────
def rec(s):
    # 头注释
    s = s.replace(''' * 两个服务商，都在设置里填，别的不需要：
 *   ACRCloud —— host / access key / secret（每家免费额度看官网）
 *   AudD     —— api token
 * 两家都是"服务端听指纹"，本地不算指纹，所以必须联网 + 必须自己的 key。''',
''' * 服务商：ACRCloud —— host / access key / secret（额度看官网）。
 * "服务端听指纹"，本地不算指纹，所以必须联网 + 必须自己的 key。''')

    s = s.replace('''    static final String P_ACR = "acr";
    static final String P_AUDD = "audd";
''', '''    static final String P_ACR = "acr";
''')

    # provider 相关整段删掉（含 setProvider）
    s = re.sub(r'    static String provider\(Context c\) \{.*?\n    \}\n\n    static void setProvider\(Context c, String p\) \{.*?\n    \}\n\n',
               '', s, count=1, flags=re.S)

    # audd 保存/读取
    s = re.sub(r'    static void saveAudd\(Context c, String token\) \{.*?\n    \}\n\n', '', s, count=1, flags=re.S)
    s = re.sub(r'    static String auddToken\(Context c\) \{.*?\n    \}\n\n', '', s, count=1, flags=re.S)

    # configured
    s = s.replace('''    static boolean configured(Context c) {
        if (P_AUDD.equals(provider(c))) return auddToken(c).length() > 8;
        return acrHost(c).length() > 3 && acrKey(c).length() > 3 && acrSecret(c).length() > 3;
    }''','''    static boolean configured(Context c) {
        return acrHost(c).length() > 3 && acrKey(c).length() > 3 && acrSecret(c).length() > 3;
    }''')

    # identify 里的分支
    s = s.replace('''        final String p = provider(ctx);
        if (!configured(ctx)) {
            cb.onTrack(null, P_AUDD.equals(p)
                    ? "还没填 AudD token（设置 → 听音识曲）"
                    : "还没填 ACRCloud 的 host / key / secret（设置 → 听音识曲）");
            return;
        }''','''        if (!configured(ctx)) {
            cb.onTrack(null, "还没填 ACRCloud 的 host / key / secret（设置 → 听音识曲）");
            return;
        }''')
    s = s.replace('''                    if (P_AUDD.equals(p)) {
                        t = audd(ctx, wav(pcm, sampleRate));
                    } else {
                        t = acr(ctx, pcm, sampleRate);
                    }''','''                    t = acr(ctx, pcm, sampleRate);''')

    # 整个 AudD 实现删掉
    s = re.sub(r'    // ───────────────── AudD ─────────────────\n.*?    // ───────────────── 工具 ─────────────────',
               '    // ───────────────── 工具 ─────────────────', s, count=1, flags=re.S)
    s = re.sub(r'    /\*\* 给 AudD 用：PCM16 mono → wav \*/\n    static byte\[\] wav\(.*?\n    \}\n\n', '', s, count=1, flags=re.S)

    # 返回码 → 中文；3001 其实是签名过了
    s = s.replace('''            case 3001:
                return "这段音频解不开";''','''            case 3001:
                return "这段音频算不出指纹（太安静或太短，靠近点再录）";''')
    s = s.replace('''            default:
                return (msg == null || msg.length() == 0) ? ("返回码 " + code) : msg;''','''            default:
                return "识别服务报错（返回码 " + code + "）";''')

    # 测一下：3001/3003 都算「签名通了」
    s = s.replace('''                } catch (ApiErr e) {
                    err = e.code == 3003
                            ? null
                            : describe(e.code, e.getMessage());''','''                } catch (ApiErr e) {
                    // 3001 算不出指纹、3003 没匹配 —— 都说明签名和 key 已经过了（错 key 会先报 100x）
                    err = (e.code == 3001 || e.code == 3003)
                            ? null
                            : describe(e.code, e.getMessage());''')
    s = s.replace('''cb.onTrack(fe == null ? new Track() : null,
                                fe == null ? "配对成功，签名过了（服务端只是没听出歌）" : fe);''','''cb.onTrack(fe == null ? new Track() : null,
                                fe == null ? "配对成功：签名和 key 都过了（没听出歌是正常的，测的是静音）" : fe);''')
    return s

rw('src/com/xiaojing/jingge/Recognize.java', rec)

# ─────────── MainActivity.java ───────────
def ma(s):
    s = s.replace('private static final String VER = "0.52";', 'private static final String VER = "0.53";')

    s = s.replace('''        TextView tip = text("录 8 秒交给服务商认指纹。两家都要自己去注册拿 key，"
                + "额度按他们的规则；识别片段会上传到那一家。", 11, Ui.MUTED, false);''',
'''        TextView tip = text("录 8 秒交给 ACRCloud 认指纹。key 要自己去控制台拿，"
                + "额度按他们的规则；识别片段会上传到 ACRCloud。", 11, Ui.MUTED, false);''')

    # 删服务商选择行
    s = re.sub(r'        final String cur = Recognize\.provider\(this\);\n        LinearLayout prow = new LinearLayout\(this\);\n'
               r'        prow\.setOrientation\(LinearLayout\.HORIZONTAL\);\n        box\.addView\(prow\);\n'
               r'        prow\.addView\(recChip\(d, "ACRCloud", Recognize\.P_ACR, cur\)\);\n'
               r'        prow\.addView\(recChip\(d, "AudD", Recognize\.P_AUDD, cur\)\);\n\n', '', s, count=1)

    # 删 AudD 注册入口
    s = re.sub(r'        TextView openAudd = text\("去 AudD 注册 ↗".*?\n        openRow\.addView\(openAudd\);\n\n', '', s, count=1, flags=re.S)

    # 删 AudD 输入框
    s = re.sub(r'        TextView tl2 = text\("AudD", 11, Ui\.DIM, false\);\n        tl2\.setPadding\(0, dp\(12\), 0, dp\(6\)\);\n'
               r'        box\.addView\(tl2\);\n        final EditText token = field\(Recognize\.auddToken\(this\), "AudD api_token"\);\n'
               r'        box\.addView\(token\);\n\n', '', s, count=1)

    # 删 recChip（只剩 ACRCloud，没得选）
    s = re.sub(r'    private View recChip\(final Dialog d, final String name, final String id, String cur\) \{.*?\n    \}\n\n',
               '', s, count=1, flags=re.S)

    s = s.replace('"识别服务 · ACRCloud / AudD"', '"识别服务 · ACRCloud"')
    return s

rw('src/com/xiaojing/jingge/MainActivity.java', ma)

# ─────────── RecognizeActivity.java ───────────
def ra(s):
    return s.replace('''            hint.setText(Recognize.P_AUDD.equals(Recognize.provider(this))
                    ? "设置 → 听音识曲 → 填 AudD token"
                    : "设置 → 听音识曲 → 填 ACRCloud 的 host / key / secret");''',
'''            hint.setText("设置 → 听音识曲 → 填 ACRCloud 的 host / key / secret");''')

rw('src/com/xiaojing/jingge/RecognizeActivity.java', ra)

# ─────────── Manifest ───────────
def mn(s):
    return (s.replace('android:versionCode="52"', 'android:versionCode="53"')
             .replace('android:versionName="0.52"', 'android:versionName="0.53"'))

rw('AndroidManifest.xml', mn)
print('patched 0.53')
