#!/usr/bin/env python3
"""يخلي كل نسخة تنبني توقّع بنفس المفتاح، حتى يتحدّث التطبيق فوق القديم بدون حذف.
شغّله مرة وحدة من جذر المشروع بعد flutter create وبعد نسخ android_overlay."""
import os, shutil, sys

here = os.path.dirname(os.path.abspath(__file__))
app = os.path.join("android", "app")
shutil.copy(os.path.join(here, "fixed.keystore"), os.path.join(app, "fixed.keystore"))

kts = os.path.join(app, "build.gradle.kts")
groovy = os.path.join(app, "build.gradle")
if os.path.exists(kts):
    with open(kts, "a", encoding="utf-8") as f:
        f.write('''
android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("fixed.keystore")
            storePassword = "autoaccept123"
            keyAlias = "autoaccept"
            keyPassword = "autoaccept123"
        }
    }
}
''')
elif os.path.exists(groovy):
    with open(groovy, "a", encoding="utf-8") as f:
        f.write('''
android {
    signingConfigs {
        debug {
            storeFile file("fixed.keystore")
            storePassword "autoaccept123"
            keyAlias "autoaccept"
            keyPassword "autoaccept123"
        }
    }
}
''')
else:
    sys.exit("ما لقيت android/app/build.gradle")
print("تم: التوقيع الثابت مفعّل")
