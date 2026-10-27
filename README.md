# 鲸歌 · 本地音乐播放器

本地放歌，联网找歌词和封面。单个 APK，无第三方依赖（不用 AndroidX、不用 Gradle）。

## 功能

- 扫描手机里的音乐（MediaStore）
- **过滤录音**：排除闹钟/通知/铃声，30 秒以下，以及文件名或标题带「录音 / 通话 / 语音备忘 / recording / voice memo」的
- **按文件修改时间倒序**（DATE_MODIFIED DESC，同时间用 DATE_ADDED，再退回标题）
- 搜标题 / 歌手 / 专辑
- **主题**：默认跟随系统深色/浅色；标题栏半圆图标可手动切黑/白，选择记在本地
- **分类**：歌曲 / 专辑 / 艺术家 / 文件夹 四个 tab；点一个分组进去看该组歌曲，返回键回分类
  - 文件夹按文件所在目录名分组（路径拿不到的会落在「其他」）
- 播放、暂停、上一首、下一首、拖动进度；顺序 / 单曲 / 随机
- **歌词**
  - 本地手写歌词优先 → QQ 音乐 → 网易云 → LRCLIB
  - 三个源按「标题 + 歌手 + 时长」打分，避免拿到翻唱
  - 同步滚动、当前行高亮、点一句跳到那句
  - **自己编辑**：编辑页里粘贴歌词，播放时逐句点「打点」记时间，保存成 `files/lyrics/<id>.lrc`
- **封面**
  - 优先级：用户自选 > 音频内嵌 > 网络缓存 > 联网查找
  - 长按歌曲（或播放页封面）：换封面（相册选图）/ 清除自定义 / 重新找 / 编辑歌词
  - 联网找封面走 QQ 音乐的专辑图，存 500×500 到缓存
- 后台播放，通知栏 + 锁屏控制；记住上次听的那首；插拔耳机自动暂停
- 深色极简界面，青色只出现在进度条和正在播放那一行

## 构建

```bash
bash build.sh
```

不用 Gradle：aapt2 → javac → d8 → zipalign → apksigner。
依赖本机 `D:/Android/sdk`（build-tools 34.0.0、platforms/android-34）和 JDK 17。

产物：`build/jingge.apk`

## 安装

```bash
adb install -r build/jingge.apk
```

或者把 APK 传到手机点开安装（需要允许「安装未知来源应用」）。

## 结构

```
src/com/xiaojing/jingge/
  MainActivity.java        列表 + 迷你条 + 播放页 + 长按菜单
  LyricEditorActivity.java 歌词编辑与打轴
  MusicService.java        MediaPlayer + MediaSession + 前台通知
  MusicRepo.java           MediaStore 扫描与过滤 + 内嵌封面
  LyricFinder.java         本地 / QQ / 网易云 / LRCLIB 找歌词
  Lyric.java               LRC 解析
  LyricView.java           同步歌词视图
  LyricStore.java          本地歌词文件
  CoverFinder.java         联网找封面（QQ 专辑图）
  CoverStore.java          封面落盘（用户 / 缓存）
  Http.java                极简 GET
  Match.java               搜索结果匹配打分
  PlayQueue.java           顺序 / 单曲 / 随机
  Song.java                歌曲数据
  VinylView.java           封面圆
  Icons.java               自绘图标
  Ui.java                  颜色与背景
tools/                     队列 / LRC 解析 / 匹配打分 单测
preview/ui.html            界面布局近似稿（不是真机截图）
```

## 验证到什么程度

- 编译、签名（v1/v2/v3）、manifest 与权限：通过
- 单测：队列 24 项、LRC 解析 17 项、匹配打分 11 项，全过
- 接口：QQ 音乐的歌曲搜索、歌词、专辑封面（500×500）在本机 curl 验过；网易云歌词验过；
  LRCLIB 在本机很慢，只放最后兜底
- **没有在真机/模拟器上跑过** —— 本机没连接设备，也没装模拟器。
  封面/歌词/选图都依赖系统行为，第一次装如果哪里闪退，把机型和 Android 版本给我。
