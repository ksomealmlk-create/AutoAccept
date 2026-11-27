package com.example.uiautomation;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * لقطة شاشة لكل رحلة. تُحفظ داخل تخزين التطبيق الخاص (ليس بمعرض الهاتف)
 * وتظهر بقائمة «لقطات الرحلات» داخل الإحصائيات.
 * الالتقاط والحفظ كله بخيط خلفي حتى ما يتأخر القبول.
 */
public final class ShotHelper {

    private static final int MAX_FILES = 300;
    private static final int OUT_WIDTH = 540;
    private static final int QUALITY = 60;

    private static final Executor IO = Executors.newSingleThreadExecutor();

    private ShotHelper() {}

    /** طلب لقطة: الصورة والرقم يوصلون بأي ترتيب، ويُحفظ الملف لما يكتملون */
    public static final class Req {
        Bitmap bmp;
        long tripId;
    }

    public static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "shots");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File fileFor(Context c, long tripId) {
        return new File(dir(c), tripId + ".jpg");
    }

    /** يطلب اللقطة (غير حاجز). يرجع null إذا غير مدعومة أو مطفية. */
    public static Req begin(AccessibilityService svc, boolean want) {
        if (!want || Build.VERSION.SDK_INT < 30) return null;
        final Req req = new Req();
        try {
            Api30.take(svc, req, svc.getApplicationContext());
        } catch (Throwable t) {
            return null;
        }
        return req;
    }

    /** بعد ما نسجّل الرحلة بالإحصائيات: نربط اللقطة برقمها */
    public static void attach(Context c, Req req, long tripId) {
        if (req == null || tripId <= 0) return;
        boolean ready;
        synchronized (req) {
            req.tripId = tripId;
            ready = req.bmp != null;
        }
        if (ready) save(c.getApplicationContext(), req);
    }

    private static void onBitmap(Context app, Req req, Bitmap bmp) {
        if (bmp == null) return;
        boolean ready;
        synchronized (req) {
            req.bmp = bmp;
            ready = req.tripId > 0;
        }
        if (ready) save(app, req);
    }

    private static void save(final Context app, final Req req) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b;
                long id;
                synchronized (req) {
                    b = req.bmp;
                    id = req.tripId;
                    req.bmp = null;
                }
                if (b == null || id <= 0) return;
                try {
                    int w = b.getWidth();
                    int h = b.getHeight();
                    Bitmap out = b;
                    if (w > OUT_WIDTH) {
                        int nh = Math.round(h * (OUT_WIDTH / (float) w));
                        out = Bitmap.createScaledBitmap(b, OUT_WIDTH, nh, true);
                    }
                    FileOutputStream fos = new FileOutputStream(fileFor(app, id));
                    try {
                        out.compress(Bitmap.CompressFormat.JPEG, QUALITY, fos);
                    } finally {
                        fos.close();
                    }
                    if (out != b) out.recycle();
                    b.recycle();
                    prune(app);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** نحتفظ بآخر MAX_FILES لقطة فقط حتى ما يمتلي التخزين */
    private static void prune(Context app) {
        File[] fs = dir(app).listFiles();
        if (fs == null || fs.length <= MAX_FILES) return;
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_FILES; i < fs.length; i++) {
            fs[i].delete();
        }
    }

    @RequiresApi(30)
    private static final class Api30 {
        static void take(AccessibilityService svc, final Req req, final Context app) {
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, IO,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult r) {
                            Bitmap soft = null;
                            try {
                                HardwareBuffer hb = r.getHardwareBuffer();
                                Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                                if (hw != null) {
                                    soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                                    hw.recycle();
                                }
                                hb.close();
                            } catch (Throwable ignored) {
                            }
                            onBitmap(app, req, soft);
                        }

                        @Override
                        public void onFailure(int errorCode) {
                        }
                    });
        }
    }
}
