# -*- coding: utf-8 -*-
import io, re, shutil, time

H = r'C:\Windows\System32\drivers\etc\hosts'
bak = r'D:\RuanJian\jingge\hosts.bak'
shutil.copyfile(H, bak)

lines = io.open(H, encoding='utf-8', errors='replace').read().splitlines()
pat = re.compile(r'^\s*127\.0\.0\.1\s+(\S+)', re.I)
targets = ('github.com', '.github.com', '.githubusercontent.com',
           'githubassets.com', '.githubapp.com', '.github.io', 'github.dev')

out = []
n = 0
for ln in lines:
    m = pat.match(ln)
    if m:
        host = m.group(1).lower()
        hit = host == 'github.com' or any(host.endswith(t) for t in targets)
        if hit:
            out.append('# ' + ln)
            n += 1
            continue
    out.append(ln)

io.open(H, 'w', encoding='utf-8', newline='\r\n').write('\n'.join(out) + '\n')
print('commented', n)
