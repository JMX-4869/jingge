#!/usr/bin/env bash
# 把当前 build/jingge.apk 传成 GitHub Release 附件
set -u
cd "$(dirname "$0")"

VER=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | head -1 | cut -d'"' -f2)
APK=build/jingge.apk
REPO=JMX-4869/jingge
TAG="v$VER"
ASSET="jingge-$VER.apk"
CURL="curl -sS --ssl-no-revoke"

[ -f "$APK" ] || { echo "没有 $APK，先编译"; exit 1; }

TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')
[ -n "${TOKEN:-}" ] || { echo "没有 GitHub 凭证"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

echo "版本 $VER，上传 $ASSET"

# 1. 找或建 release
$CURL -H "$AUTH" "https://api.github.com/repos/$REPO/releases/tags/$TAG" -o /tmp/rel.json -w "%{http_code}" > /tmp/rel.code
CODE=$(cat /tmp/rel.code)
if [ "$CODE" != "200" ]; then
  cat > /tmp/relbody.json <<JSON
{"tag_name":"$TAG","name":"鲸歌 $VER","body":"鲸歌 $VER\\n\\n- 侧边栏：歌曲/专辑/艺术家/文件夹 + 设置/关于\\n- 6 套内置主题，可自制、导入、长按复制分享\\n- 编辑页 AI 写词（OpenAI 兼容接口）\\n- 封面 / 歌词 / 分类 / 深浅色"}
JSON
  $CURL -H "$AUTH" -H "Accept: application/vnd.github+json" \
    -X POST --data-binary @/tmp/relbody.json \
    "https://api.github.com/repos/$REPO/releases" -o /tmp/rel.json -w "create=%{http_code}\n"
fi

ID=$(sed -n 's/.*"id": *\([0-9]*\).*/\1/p' /tmp/rel.json | head -1)
[ -n "$ID" ] || { echo "拿不到 release id"; head -c 300 /tmp/rel.json; exit 2; }

# 2. 同名附件先删
$CURL -H "$AUTH" "https://api.github.com/repos/$REPO/releases/$ID/assets" -o /tmp/assets.json
OLD=$(sed -n 's/.*"id": *\([0-9]*\), *"node_id"[^}]*"name": *"'"$ASSET"'".*/\1/p' /tmp/assets.json | head -1)
if [ -n "$OLD" ]; then
  $CURL -X DELETE -H "$AUTH" "https://api.github.com/repos/$REPO/releases/assets/$OLD" -o /dev/null -w "del=%{http_code}\n"
fi

# 3. 上传
$CURL -H "$AUTH" -H "Content-Type: application/vnd.android.package-archive" \
  -X POST --data-binary @"$APK" \
  "https://uploads.github.com/repos/$REPO/releases/$ID/assets?name=$ASSET" \
  -o /tmp/up.json -w "upload=%{http_code}\n"
grep -o '"browser_download_url": *"[^"]*"' /tmp/up.json | head -1
