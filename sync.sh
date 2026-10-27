#!/usr/bin/env bash
# 把本地仓库推到 GitHub：没建仓就建，有改动就提交
set -u
cd "$(dirname "$0")"
VER=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | head -1 | cut -d'"' -f2)
git add -A
if ! git diff --cached --quiet; then
  git -c user.name=JMX-4869 -c user.email=JMX-4869@users.noreply.github.com \
    commit -q -m "v$VER" && git tag -f "v$VER"
  echo "committed v$VER"
fi
if ! gh repo view JMX-4869/jingge >/dev/null 2>&1; then
  gh repo create jingge --private --source=. --remote=origin --push && exit 0
fi
git push -u origin HEAD --follow-tags
