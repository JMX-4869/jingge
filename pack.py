import os
import zipfile
import shutil

# 把 d8 产出的 classes.dex 塞进 aapt2 打出来的 base.apk（dex 必须在包根）
base = "build/base.apk"
dex = "build/dex/classes.dex"
tmp = "build/_tmp.apk"

if not os.path.exists(dex):
    raise SystemExit("缺 dex: " + dex)
if not os.path.exists(base):
    raise SystemExit("缺 apk: " + base)

shutil.copyfile(base, tmp)
with zipfile.ZipFile(tmp, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
os.replace(tmp, base)

with zipfile.ZipFile(base) as z:
    names = z.namelist()
print("entries:", len(names))
print("has classes.dex:", "classes.dex" in names)
print("has manifest:", "AndroidManifest.xml" in names)
