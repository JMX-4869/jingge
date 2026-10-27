#!/usr/bin/env bash
# 停掉 Steam++（它把 github 写回 127.0.0.1，而它的 443 没起），清 hosts，建仓，推
set -u
cd "$(dirname "$0")"
PY="C:/Users/asus/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/python.exe"

echo "== 1. 停 Steam++ =="
taskkill //IM "Steam++.exe" //F 2>&1 | head -3 || true
sleep 3

echo "== 2. 清 hosts =="
"$PY" tools/fix_hosts.py
grep -c "^127.0.0.1 github.com" /c/Windows/System32/drivers/etc/hosts 2>/dev/null || echo "0 (good)"

echo "== 3. 通不通 =="
timeout 15 curl -sS -o /dev/null -w "github=%{http_code}\n" https://github.com | tail -1

echo "== 4. 拿凭证 =="
TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')
if [ -z "${TOKEN:-}" ]; then echo "没有凭证，停"; exit 2; fi
echo "token len=${#TOKEN}"

echo "== 5. 建仓 =="
cat > /tmp/repo.json <<'JSON'
{"name":"jingge","private":true,"description":"鲸歌 · 本地音乐播放器"}
JSON
timeout 30 curl -sS -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  --data-binary @/tmp/repo.json \
  https://api.github.com/user/repos -o /tmp/repo.out -w "create=%{http_code}\n"
head -c 300 /tmp/repo.out; echo

echo "== 6. 推 =="
GIT_TERMINAL_PROMPT=0 timeout 120 git push -u origin HEAD --follow-tags 2>&1 | tail -5

echo "== 7. 验 =="
timeout 20 git ls-remote origin 2>&1 | head -3
