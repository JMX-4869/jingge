#!/usr/bin/env bash
# 不用 Gradle：aapt2 → javac → d8 → zipalign → apksigner
set -euo pipefail
cd "$(dirname "$0")"

SDK=D:/Android/sdk
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
PY="C:/Users/asus/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/python.exe"
# R8 8.2 在 JDK 21+ 上会崩，固定用 17
JDK="/c/Program Files/Java/jdk-17.0.2/bin"
OUT=build

echo "[1/6] 图标"
"$PY" make_icon.py

echo "[2/6] 清空 build/"
rm -rf $OUT/classes $OUT/dex $OUT/gen $OUT/res.zip $OUT/base.apk $OUT/aligned.apk
mkdir -p $OUT/classes $OUT/dex

echo "[3/6] aapt2 compile + link"
"$BT/aapt2.exe" compile --dir res -o $OUT/res.zip
"$BT/aapt2.exe" link -o $OUT/base.apk -I "$AJ" \
  --manifest AndroidManifest.xml \
  $OUT/res.zip \
  --java $OUT/gen \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0

echo "[4/6] javac + d8"
"$JDK/javac.exe" -nowarn -source 8 -target 8 -encoding UTF-8 -classpath "$AJ" \
  -d $OUT/classes $(find src -name '*.java') $(find $OUT/gen -name '*.java')

if [ -f "$BT/lib/d8.jar" ]; then
  "$JDK/java.exe" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$AJ" --min-api 21 --release \
    --output $OUT/dex $(find $OUT/classes -name '*.class')
else
  "$BT/d8.bat" --lib "$AJ" --min-api 21 --release --output $OUT/dex $(find $OUT/classes -name '*.class')
fi

echo "[5/6] 塞 dex 进 apk"
"$PY" pack.py

echo "[6/6] zipalign + 签名"
if [ ! -f debug.jks ]; then
  "$JDK/keytool.exe" -genkeypair -keystore debug.jks -alias jingge -keyalg RSA -keysize 2048 \
    -validity 10000 -storetype JKS -storepass android -keypass android \
    -dname "CN=JingGe,O=dev,C=CN" >/dev/null 2>&1
fi
"$BT/zipalign.exe" -f 4 $OUT/base.apk $OUT/aligned.apk
"$BT/apksigner.bat" sign --ks debug.jks --ks-pass pass:android --key-pass pass:android \
  --out $OUT/jingge.apk $OUT/aligned.apk
"$BT/apksigner.bat" verify $OUT/jingge.apk
echo "OK -> $OUT/jingge.apk"
ls -la $OUT/jingge.apk

# [8] 自动提交并同步到 GitHub（没有网络/凭证就跳过，不影响构建）
if git rev-parse --git-dir >/dev/null 2>&1; then
  VER=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | head -1 | cut -d'"' -f2)
  git add -A 2>/dev/null
  if ! git diff --cached --quiet 2>/dev/null; then
    git -c user.name=JMX-4869 -c user.email=JMX-4869@users.noreply.github.com       commit -q -m "v$VER" 2>/dev/null || true
    git tag -f "v$VER" >/dev/null 2>&1 || true
    echo "committed v$VER"
  fi
  if git remote get-url origin >/dev/null 2>&1; then
    if git push -q origin HEAD --follow-tags 2>/dev/null; then
      echo "synced -> github"
    else
      echo "github 没推上去（网络/凭证），本地已提交 v$VER"
    fi
  else
    echo "还没配 origin（仓库没建），本地已提交 v$VER"
  fi
fi

# [9] 把 APK 传到 GitHub Release（没有网络就跳过）
if [ -x publish_release.sh ] || [ -f publish_release.sh ]; then
  bash publish_release.sh || echo "release 没传上去（网络/凭证）"
fi
