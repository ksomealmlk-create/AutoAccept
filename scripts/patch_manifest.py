p = "android/app/src/main/AndroidManifest.xml"
s = open(p, encoding="utf-8").read()
svc = """
        <service
            android:name=".AutoAcceptService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_config" />
        </service>
"""
if "AutoAcceptService" not in s:
    s = s.replace("</application>", svc + "    </application>", 1)
open(p, "w", encoding="utf-8").write(s)
print("manifest patched")
