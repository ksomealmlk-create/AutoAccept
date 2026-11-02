#!/usr/bin/env python3
"""يحط لوگو البرنامج واسمه (صگار كريم). شغّله من جذر المشروع بعد flutter create."""
import os, re, shutil, glob

here = os.path.dirname(os.path.abspath(__file__))
for d in glob.glob(os.path.join(here, "res", "mipmap-*")):
    dst = os.path.join("android", "app", "src", "main", "res", os.path.basename(d))
    os.makedirs(dst, exist_ok=True)
    shutil.copy(os.path.join(d, "ic_launcher.png"), os.path.join(dst, "ic_launcher.png"))

m = os.path.join("android", "app", "src", "main", "AndroidManifest.xml")
t = open(m, encoding="utf-8").read()
t = re.sub(r'android:label="[^"]*"', 'android:label="صگار كريم"', t, count=1)
t = t.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@mipmap/ic_launcher"')
open(m, "w", encoding="utf-8").write(t)
print("تم: اللوگو والاسم")
