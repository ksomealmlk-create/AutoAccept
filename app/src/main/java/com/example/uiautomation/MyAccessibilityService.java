package com.example.uiautomation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";

    private static final String CONFIRM_TEXT = "موافق";

    /* زر انتهاء الرحلة (بعد توحيد النص) */
    private static final String[] END_TEXTS = {
            "انهاء مع دفع"
    };

    private static final double MIN_DISTANCE_KM = 0.0;

    private static final Pattern DISTANCE_PATTERN =
            Pattern.compile(
                    "([0-9]+(?:[\\.,][0-9]+)?)\\s*"
                            + "(?:كم|كيلومتر|كيلو\\s*متر|km|KM)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_AFTER =
            Pattern.compile(
                    "([0-9][0-9,\\.]*)\\s*(?:د\\.?\\s*ع|دينار|IQD)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_EARN =
            Pattern.compile("استحصل\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("([0-9]{1,2}):([0-9]{2}):([0-9]{2})");

    /* ===== Uber أمريكا ===== */
    private static final String[] US_ACCEPT_TEXTS = {"تطابق", "اقبل", "أقبل", "Accept"};

    private static final Pattern US_KM_PATTERN =
            Pattern.compile("(?:بعد)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:كلم|كم|km)(?![\\u0621-\\u064A])",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern US_METERS_PATTERN =
            Pattern.compile("(?:بعد)?\\s*([0-9]+)\\s*م(?![\\u0621-\\u064A])");

    private static final Pattern US_MINUTES_PATTERN =
            Pattern.compile("(?:بعد)?\\s*([0-9]+)\\s*(?:دقيقه|دقائق|دقيقة|د)(?![\\u0621-\\u064A])");

    private static final boolean CLICK_IF_NO_DISTANCE_FOUND = false;

    private static final long SCAN_DELAY_MS = 3L;
    private static final long MIN_SCAN_INTERVAL_MS = 3L;

    /* نفس الرحلة خلال هذي المدة تُعتبر مكررة */
    private static final long DUP_WINDOW_MS = 20000L;

    private final Handler scanHandler =
            new Handler(Looper.getMainLooper());

    private long lastScanAt = 0L;
    private String lastHandledSignature = null;
    private long lastHandledAt = 0L;

    /* فحص واحد معلّق فقط: الأحداث الكثيرة (أنيميشن/خريطة) ما تأجّل الفحص */
    private boolean scanPending = false;

    private final Runnable scanRunnable = () -> {
        scanPending = false;
        scanCurrentScreen();
    };

    /* ===== قياس سرعة الضغط (مللي ثانية) ===== */
    private long lastEventUptime = 0L;   // وقت آخر حدث وصل
    private long scanStartUptime = 0L;   // بداية الفحص الحالي
    private long clickLatMs = -1L;       // من الحدث إلى الضغط
    private long clickScanMs = -1L;      // من بداية الفحص إلى الضغط
    private String clickPh = "";         // تفصيل أزمنة المراحل

    private long phRootsMs = 0L, phTextMs = 0L, phBtnMs = 0L;
    private int phWins = 0;

    private void markClick() {
        long n = SystemClock.uptimeMillis();
        clickLatMs = lastEventUptime > 0 ? n - lastEventUptime : -1L;
        clickScanMs = scanStartUptime > 0 ? n - scanStartUptime : -1L;
        long other = Math.max(0L,
                clickScanMs - phRootsMs - phTextMs - phBtnMs);
        clickPh = "نوافذ " + phRootsMs + " | نص " + phTextMs
                + " (" + phWins + " نافذة) | أزرار " + phBtnMs
                + " | أخرى " + other;
    }

    private void onAcceptedClick() {
        if (Prefs.isSoundOnAcceptEnabled(this)) {
            AlertSound.play();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (!FloatingService.isAutomationRunning) {
            return;
        }

        int type = event.getEventType();

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        long et = event.getEventTime();
        lastEventUptime = et > 0 ? et : SystemClock.uptimeMillis();

        if (scanPending) {
            return;
        }
        scanPending = true;
        scanHandler.post(scanRunnable);
    }

    /* كل النوافذ الظاهرة (التطبيق + الإشعارات العائمة) من الأعلى للأسفل */
    private List<AccessibilityNodeInfo> collectRoots() {

        List<AccessibilityNodeInfo> roots = new ArrayList<>();

        try {
            List<AccessibilityWindowInfo> windows = getWindows();

            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {

                    AccessibilityNodeInfo r = w.getRoot();

                    if (r == null) {
                        continue;
                    }

                    /* تجاهل نوافذ تطبيقنا نفسه (الزر العائم والإعدادات) */
                    if (getPackageName().contentEquals(r.getPackageName() == null
                            ? "" : r.getPackageName())) {
                        continue;
                    }

                    roots.add(r);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "getWindows فشل: " + e.getMessage());
        }

        if (roots.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) {
                roots.add(r);
            }
        }

        /* نوافذ Uber أولاً (نفس الترتيب بين الباقي): نتجنب مسح نوافذ النظام قبلها */
        List<AccessibilityNodeInfo> ordered = new ArrayList<>(roots.size());
        for (AccessibilityNodeInfo r : roots) {
            CharSequence pk = r.getPackageName();
            if (pk != null && pk.toString().toLowerCase().contains("uber")) {
                ordered.add(r);
            }
        }
        for (AccessibilityNodeInfo r : roots) {
            if (!ordered.contains(r)) {
                ordered.add(r);
            }
        }

        return ordered;
    }

    private void scanCurrentScreen() {

        if (!FloatingService.isAutomationRunning) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastScanAt < MIN_SCAN_INTERVAL_MS) {
            return;
        }

        lastScanAt = now;
        scanStartUptime = SystemClock.uptimeMillis();
        phRootsMs = 0L;
        phTextMs = 0L;
        phBtnMs = 0L;
        phWins = 0;

        List<AccessibilityNodeInfo> done = new ArrayList<>();

        /* بطاقة العراق: فحص رخيص (بحث واحد) بكل حدث، بدون مرور على الخريطة */
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            phRootsMs = SystemClock.uptimeMillis() - scanStartUptime;

            if (active != null && !isOwnWindow(active)) {
                if (fastSwipeCard(active, now)) {
                    return;
                }
                done.add(active);
            }

            /* الفحص الكامل الثقيل (أمريكا، رسالة الحجز، انتهاء الرحلة): مو بكل حدث،
               حتى ما نشغل الخيط وقت أنيميشن الخريطة ونتأخر عن ظهور البطاقة */
            long sinceHeavy = now - lastHeavyAt;
            if (sinceHeavy < HEAVY_GAP_MS) {
                scheduleRecheck(HEAVY_GAP_MS - sinceHeavy);
                return;
            }
            lastHeavyAt = now;

            if (active != null && !isOwnWindow(active)
                    && scanRoot(active, now)) {
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "فحص النافذة النشطة فشل: " + e.getMessage());
        }

        long tRoots = SystemClock.uptimeMillis();
        List<AccessibilityNodeInfo> allRoots = collectRoots();
        phRootsMs += SystemClock.uptimeMillis() - tRoots;

        for (AccessibilityNodeInfo root : allRoots) {
            if (done.contains(root)) {
                continue;
            }
            if (fastSwipeCard(root, now)) {
                return;
            }
        }

        for (AccessibilityNodeInfo root : allRoots) {
            if (done.contains(root)) {
                continue;
            }
            if (scanRoot(root, now)) {
                break;
            }
        }
    }

    private static final long HEAVY_GAP_MS = 200L;
    private long lastHeavyAt = 0L;

    private boolean isOwnWindow(AccessibilityNodeInfo r) {
        CharSequence pk = r.getPackageName();
        return pk != null && getPackageName().contentEquals(pk);
    }

    /* يرجع true إذا النافذة فيها رحلة/رسالة تم التعامل معها */
    private boolean scanRoot(AccessibilityNodeInfo rootNode, long now) {

        StringBuilder screenText = new StringBuilder(512);
        pickupFound = null;
        long tTxt = SystemClock.uptimeMillis();
        collectAllText(rootNode, screenText);
        phTextMs += SystemClock.uptimeMillis() - tTxt;
        phWins++;

        String normalizedScreen =
                normalizeArabicText(screenText.toString());

        /* ===== Uber أمريكا: يُعرف من زر «تطابق» (منفصل تمامًا عن Uber العراق) ===== */
        AccessibilityNodeInfo usMatchButton = null;
        boolean usOn = Prefs.isUsEnabled(this);

        /* نبض تشخيص: يثبت إن الخدمة شغالة وشايفة بطاقة أمريكا (مرة كل 4 ثواني) */
        if (usOn && now - lastUsHbAt > 4000L) {
            String hbPlain = stripDiacritics(normalizedScreen);
            if (hbPlain.contains("على بعد")) {
                lastUsHbAt = now;
                diag("شفت بطاقة (على بعد) | تطابق=" + hbPlain.contains("تطابق")
                        + " | اقبل=" + hbPlain.contains("اقبل")
                        + " | حزمة=" + rootNode.getPackageName());
            }
        }

        /* نبحث عن أزرار أمريكا فقط إذا نص الشاشة فيه كلماتها (يوفر 4 عمليات بحث) */
        boolean usWords = normalizedScreen.contains("تطابق")
                || normalizedScreen.contains("اقبل")
                || normalizedScreen.toLowerCase().contains("accept");

        if (usOn && usWords) {
            usMatchButton =
                    findClickableByTexts(rootNode, US_ACCEPT_TEXTS);

            /* الشاشة المنبثقة/الإشعار: الزر ممكن ما يطلع قابل للضغط، نكتفي بالنص + بطاقة «على بعد» */
            if (usMatchButton == null
                    && stripDiacritics(normalizedScreen).contains("على بعد")
                    && extractUsMinutes(stripDiacritics(normalizeDigits(
                    screenText.toString()))) != null) {
                usMatchButton = findNodeByTexts(rootNode, US_ACCEPT_TEXTS);
            }
        }

        if (usMatchButton != null) {
            handleUberUs(usMatchButton, rootNode, screenText.toString(),
                    normalizedScreen, now);
            return true;
        }

        /* الزر مخفي عن إمكانية الوصول: نلمس مكانه بالإحداثيات (مثل AutoAccept) */
        if (usOn && usMatchButton == null
                && Prefs.isUsAutoAcceptEnabled(this)
                && stripDiacritics(normalizedScreen).contains("على بعد")
                && handleUsHiddenButton(screenText.toString(),
                normalizedScreen, now)) {
            return true;
        }

        if (usOn && usMatchButton == null
                && stripDiacritics(normalizedScreen).contains("على بعد")
                && (normalizedScreen.contains("تطابق")
                || normalizedScreen.contains("اقبل"))) {
            diag("شفت بطاقة أمريكا بس زر القبول مو قابل للضغط");
        }

        /* ===== رسالة: قبل سائق آخر هذا الحجز ===== */
        if (isPreviousDriverMessage(normalizedScreen)) {

            if (Prefs.isStatsEnabled(this)) {
                TripStats.markLostPending(this);
            }

            if (Prefs.isAutoConfirmDialogEnabled(this)) {

                AccessibilityNodeInfo confirm =
                        findClickableByTexts(
                                rootNode,
                                new String[]{CONFIRM_TEXT});

                if (confirm != null) {
                    boolean result = confirm.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "رسالة الحجز السابق -> موافق: " + result);
                }
            }

            return true;
        }

        /* ===== انتهاء الرحلة: تسجيل الوقت والسعر ===== */
        if (Prefs.isStatsEnabled(this)
                && isTripEnded(normalizedScreen)
                && TripStats.hasPending(this)) {

            long price = extractPrice(screenText.toString());
            long tripMs = extractDurationMs(screenText.toString());
            TripStats.recordEnded(this, price, tripMs);
            Log.d(TAG, "انتهت الرحلة، السعر: " + price);
            return true;
        }

        /* ===== بطاقة الرحلة (Uber العراق): قبول/رفض بالسحب ===== */
        if (!PICKUP_LABEL.matcher(
                stripDiacritics(normalizedScreen)).find()) {
            return false;
        }
        return handleSwipeCard(rootNode, rootNode, now);
    }

    /* ===================== Uber العراق: القبول والرفض بالسحب ===================== */

    /* مكان ✓ و ✗ كنسبة من عرض الشاشة (المقبض بالنص) */
    private static final float SWIPE_ACCEPT_X = 0.83f;
    private static final float SWIPE_REJECT_X = 0.17f;
    private static final long SWIPE_DURATION_MS = 35L;

    /* المقبض الأصفر تحت رقم «إلى الانطلاق» بهذا المقدار من عرض الشاشة (احتياط إذا ما انلقى كعقدة) */
    private static final float HANDLE_BELOW_LABEL_W = 0.26f;

    /* إعادة المحاولة إذا بقيت البطاقة: حتى 5 مرات خلال 4 ثوانٍ */
    private static final int SWIPE_MAX_TRIES = 12;
    private static final long SWIPE_TRY_WINDOW_MS = 4000L;
    private static final long SWIPE_RETRY_GAP_MS = 50L;
    private static final long CARD_GONE_MS = 2500L;

    private String cardKey = null;
    private long cardFirstAt = 0L;
    private long cardLastSeenAt = 0L;
    private long cardLastSwipeAt = 0L;
    private int cardTries = 0;

    private static final Pattern PICKUP_LABEL =
            Pattern.compile("ال[ىي]\\s*الانطلاق");

    /* يقرأ بُعد الزبون فقط (الرقم تحت «إلى الانطلاق») ويرجع مكان العنوان */
    private static class CardInfo {
        Rect label = new Rect();
        Double pickupKm = null;
        Rect value = null;
        String text = "";   // نص البطاقة (للبصمة والتصنيف)
    }

    private CardInfo readSwipeCard(AccessibilityNodeInfo root) {

        List<TNode> all = new ArrayList<>();
        collectTNodes(root, all);

        TNode labelNode = null;
        for (TNode t : all) {
            String c = stripDiacritics(normalizeArabicText(t.text));
            if (PICKUP_LABEL.matcher(c).find()) {
                labelNode = t;
                break;
            }
        }

        if (labelNode == null) {
            return null;
        }

        CardInfo info = new CardInfo();
        info.label.set(labelNode.r);
        StringBuilder sbText = new StringBuilder();
        for (TNode t : all) {
            sbText.append(t.text).append(' ');
        }
        info.text = normalizeArabicText(sbText.toString());

        /* حالة: العنوان والرقم بنفس العقدة («إلى الانطلاق 0.1 كم») */
        Double same = extractDistanceKm(labelNode.text);
        if (same != null) {
            info.pickupKm = same;
            info.value = new Rect(labelNode.r);
            return info;
        }

        /* الرقم الواقع تحت العنوان وبنفس عموده: الأقرب أفقياً ثم رأسياً */
        TNode best = null;
        int bestScore = Integer.MAX_VALUE;
        int lx = labelNode.r.centerX();
        int ly = labelNode.r.centerY();
        int lh = Math.max(1, labelNode.r.height());

        for (TNode t : all) {
            if (t == labelNode) {
                continue;
            }
            if (extractDistanceKm(t.text) == null) {
                continue;
            }
            int dy = t.r.centerY() - ly;
            if (dy <= 0 || dy > lh * 6) {
                continue;
            }
            int dx = Math.abs(t.r.centerX() - lx);
            int score = dx * 3 + dy;
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }

        if (best != null) {
            /* لازم يكون فعلاً بنفس عمود العنوان (مو عمود «مسافة الرحلة») */
            int tol = Math.max(labelNode.r.width(), best.r.width());
            if (Math.abs(best.r.centerX() - lx) <= tol) {
                info.pickupKm = extractDistanceKm(best.text);
                info.value = new Rect(best.r);
            }
        }

        return info;
    }

    /* يدور على المقبض الأصفر (عقدة شبه مربعة بنص الشاشة تحت العنوان) */
    private Rect findHandleRect(AccessibilityNodeInfo node, Rect screen,
                                Rect label) {
        if (node == null) {
            return null;
        }
        Rect r = new Rect();
        node.getBoundsInScreen(r);

        int w = screen.width();
        if (r.width() >= w * 0.12f && r.width() <= w * 0.30f
                && Math.abs(r.width() - r.height()) <= r.width() * 0.25f
                && Math.abs(r.centerX() - screen.centerX()) <= w * 0.08f
                && r.top > label.bottom) {
            return r;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c == null) {
                continue;
            }
            Rect found = findHandleRect(c, screen, label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /* السحبة الجديدة تلغي اللي قبلها إذا لسه شغالة: نعيد فقط بعد ما تنتهي */
    private volatile boolean swipeInFlight = false;
    private volatile long swipeStartedAt = 0L;

    private boolean swipe(int x0, int y, int x1) {

        if (swipeInFlight
                && SystemClock.uptimeMillis() - swipeStartedAt < 400L) {
            return false;
        }

        if (Build.VERSION.SDK_INT < 24 || x0 <= 0 || y <= 0 || x1 <= 0) {
            return false;
        }

        try {
            Path path = new Path();
            path.moveTo(x0, y);
            path.lineTo(x1, y);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(
                            path, 0, SWIPE_DURATION_MS);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke).build();

            swipeInFlight = true;
            swipeStartedAt = SystemClock.uptimeMillis();
            boolean sent = dispatchGesture(gesture,
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription g) {
                            swipeInFlight = false;
                        }

                        @Override
                        public void onCancelled(GestureDescription g) {
                            swipeInFlight = false;
                        }
                    }, null);
            if (!sent) {
                swipeInFlight = false;
            }
            return sent;

        } catch (Exception e) {
            swipeInFlight = false;
            Log.d(TAG, "swipe فشل: " + e.getMessage());
            return false;
        }
    }

    private long unreadFirstAt = 0L;
    private long unreadLastAt = 0L;

    private String iqDiagMsg = "";

    /* سطر التشخيص + إشعار على الشاشة: انطلاق=0.1 كم | الحد=3.0 | قبول أو تجاهل */
    private void iqReport(String pickup, String limit, String decision,
                          boolean toast) {
        String msg = "انطلاق=" + pickup + " | الحد=" + limit
                + " | " + decision;
        if (msg.equals(iqDiagMsg)) {
            return;
        }
        iqDiagMsg = msg;
        try {
            String time = new java.text.SimpleDateFormat("HH:mm:ss",
                    java.util.Locale.US).format(new java.util.Date());
            Prefs.setIqDiag(this, time + "  " + msg);
        } catch (Exception ignored) {
        }
        if (toast) {
            try {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        }
    }

    private String fmtKm(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /* مسار سريع: بحث واحد عن «الانطلاق» ثم قراءة البطاقة فقط (بدون المرور على الخريطة كلها) */
    private boolean fastSwipeCard(AccessibilityNodeInfo root, long now) {
        try {
            List<AccessibilityNodeInfo> found =
                    root.findAccessibilityNodeInfosByText("الانطلاق");
            if (found == null || found.isEmpty()) {
                return false;
            }

            AccessibilityNodeInfo label = null;
            for (AccessibilityNodeInfo n : found) {
                if (n == null) {
                    continue;
                }
                CharSequence t = n.getText() != null
                        ? n.getText() : n.getContentDescription();
                if (t == null) {
                    continue;
                }
                String c = stripDiacritics(normalizeArabicText(t.toString()));
                if (PICKUP_LABEL.matcher(c).find()) {
                    label = n;
                    break;
                }
            }
            if (label == null) {
                return false;
            }

            Rect screen = new Rect();
            root.getBoundsInScreen(screen);
            if (screen.height() <= 0) {
                screen.set(0, 0,
                        getResources().getDisplayMetrics().widthPixels,
                        getResources().getDisplayMetrics().heightPixels);
            }

            /* نطلع من العنوان لحد حاوية البطاقة (أقل من 55% من ارتفاع الشاشة) */
            AccessibilityNodeInfo scope = label;
            AccessibilityNodeInfo cur = label.getParent();
            Rect r = new Rect();
            for (int i = 0; i < 6 && cur != null; i++) {
                cur.getBoundsInScreen(r);
                if (r.height() >= screen.height() * 0.55f) {
                    break;
                }
                scope = cur;
                cur = cur.getParent();
            }

            pickupFound = null;
            return handleSwipeCard(root, scope, now);

        } catch (Exception e) {
            Log.d(TAG, "fastSwipeCard فشل: " + e.getMessage());
            return false;
        }
    }

    /** true = البطاقة موجودة وتم التعامل معها */
    private boolean handleSwipeCard(AccessibilityNodeInfo root,
                                    AccessibilityNodeInfo scope,
                                    long now) {

        CardInfo card = readSwipeCard(scope);
        if (card == null) {
            return false;
        }
        String normalizedScreen = card.text;

        double maxKm = Prefs.getMaxDistanceKm(this);
        boolean unlimited = maxKm < 0;
        Double km = card.pickupKm;

        String pickupTxt = km == null ? "؟" : fmtKm(km) + " كم";
        String limitTxt = unlimited ? "بدون حد" : fmtKm(maxKm);

        /* القرار */
        boolean accept;
        if (unlimited) {
            accept = true;
        } else if (km == null) {
            /* الرقم غير مقروء: لا قبول ولا رفض (حتى ما ترفض رحلة بقراءة ناقصة) */
            iqReport(pickupTxt, limitTxt, "تجاهل (غير مقروء)", true);
            /* الرقم ممكن يتأخر يظهر بالشجرة: نعيد الفحص بسرعة لمدة 3 ثوانٍ */
            if (unreadFirstAt == 0L || now - unreadLastAt > 1500L) {
                unreadFirstAt = now;
            }
            unreadLastAt = now;
            if (now - unreadFirstAt < 3000L) {
                scheduleRecheck(100L);
            }
            return true;
        } else {
            accept = km >= MIN_DISTANCE_KM && km <= maxKm + 1e-6;
        }
        unreadFirstAt = 0L;

        /* نفس البطاقة؟ (بدون أرقام حتى ما يؤثر عدّاد الوقت) */
        String key = buildSignature(normalizedScreen, km);
        if (!key.equals(cardKey) || now - cardLastSeenAt > CARD_GONE_MS) {
            cardKey = key;
            cardFirstAt = now;
            cardTries = 0;
            cardLastSwipeAt = 0L;
        }
        cardLastSeenAt = now;

        if (accept && !Prefs.isAutoAcceptEnabled(this)) {
            iqReport(pickupTxt, limitTxt, "قبول (القبول التلقائي مطفي)", false);
            return true;
        }
        if (!accept && !Prefs.isAutoRejectEnabled(this)) {
            iqReport(pickupTxt, limitTxt, "تجاهل (الرفض التلقائي مطفي)", false);
            return true;
        }

        /* انتهت المحاولات */
        if (cardTries >= SWIPE_MAX_TRIES
                || now - cardFirstAt > SWIPE_TRY_WINDOW_MS) {
            if (cardTries > 0) {
                return true;
            }
        }

        /* فاصل بين المحاولات */
        if (cardTries > 0 && now - cardLastSwipeAt < SWIPE_RETRY_GAP_MS) {
            scheduleRecheck(SWIPE_RETRY_GAP_MS - (now - cardLastSwipeAt));
            return true;
        }

        /* لقطة واضحة للرحلات المتجاهلة فقط: ننتظر البطاقة تكتمل */
        if (!accept && cardTries == 0 && wantShot()
                && mustWaitForShot(key, now)) {
            return true;
        }

        /* مكان السحب */
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        if (screen.width() <= 0) {
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            screen.set(0, 0, w, h);
        }

        Rect handle = null;
        try {
            handle = findHandleRect(scope, screen, card.label);
        } catch (Exception ignored) {
        }

        int startX;
        int y;
        if (handle != null) {
            startX = handle.centerX();
            y = handle.centerY();
        } else {
            startX = screen.centerX();
            y = card.label.centerY()
                    + (int) (screen.width() * HANDLE_BELOW_LABEL_W);
        }

        int endX = screen.left + (int) (screen.width()
                * (accept ? SWIPE_ACCEPT_X : SWIPE_REJECT_X));

        /* سحبة سابقة لسه شغالة: ننتظرها (بدون ما نحسبها محاولة) */
        if (swipeInFlight
                && SystemClock.uptimeMillis() - swipeStartedAt < 400L) {
            scheduleRecheck(50L);
            return true;
        }

        /* القبول: السحب أول شي بدون أي تأخير، واللقطة بعده */
        ShotHelper.Req shot = null;
        if (!accept && cardTries == 0) {
            shot = ShotHelper.begin(this, wantShot());
        }

        boolean ok = swipe(startX, y, endX);
        markClick();

        if (accept && cardTries == 0) {
            shot = ShotHelper.begin(this, wantShot());
        }
        cardTries++;
        cardLastSwipeAt = now;

        Log.d(TAG, "سحب " + (accept ? "قبول ✓" : "رفض ✗")
                + " | انطلاق=" + pickupTxt + " | نجح=" + ok
                + " | محاولة " + cardTries);

        iqReport(pickupTxt, limitTxt, accept ? "قبول" : "تجاهل",
                cardTries == 1);

        if (cardTries == 1 && ok) {
            if (accept) {
                onAcceptedClick();
            }
            if (Prefs.isStatsEnabled(this)) {
                String address = pickupFound;
                String matched = address != null
                        ? NeighborhoodFilter.matchNeighborhood(address) : null;
                String hood = matched != null
                        ? matched : firstAddressPart(address);
                String category = detectCategory(normalizedScreen);
                double kmLog = km != null ? km : -1;
                if (accept) {
                    Prefs.incrementAccepted(this);
                    Prefs.addLogEntry(this, kmLog, true);
                    ShotHelper.attach(this, shot, TripStats.recordAccepted(
                            this, kmLog, category, hood, maxKm,
                            clickLatMs, clickScanMs, clickPh));
                } else {
                    Prefs.incrementRejected(this);
                    Prefs.addLogEntry(this, kmLog, false);
                    ShotHelper.attach(this, shot, TripStats.recordRejected(
                            this, kmLog, category, hood,
                            km == null ? "NO_DIST" : "OUT_RANGE", maxKm,
                            clickLatMs, clickScanMs, clickPh));
                }
            }
        }

        /* إذا بقيت البطاقة نعيد المحاولة */
        if (cardTries < SWIPE_MAX_TRIES) {
            scheduleRecheck(SWIPE_RETRY_GAP_MS);
        }
        return true;
    }

    private void scheduleRecheck(long delayMs) {
        scanHandler.removeCallbacks(scanRunnable);
        scanPending = true;
        scanHandler.postDelayed(scanRunnable, Math.max(50L, delayMs));
    }

    /* ===================== Uber أمريكا ===================== */

    private static final long SHOT_DELAY_MS = 500L;
    private String shotWaitSig = null;
    private long shotWaitStart = 0L;

    /** true = لسه ننتظر حتى تكتمل البطاقة قبل اللقطة والتجاهل */
    private boolean mustWaitForShot(String sig, long now) {
        if (!sig.equals(shotWaitSig) || now - shotWaitStart > 5000L) {
            shotWaitSig = sig;
            shotWaitStart = now;
        }
        long left = SHOT_DELAY_MS - (now - shotWaitStart);
        if (left > 0) {
            scanHandler.removeCallbacks(scanRunnable);
            scanPending = true;
            scanHandler.postDelayed(scanRunnable, left);
            return true;
        }
        return false;
    }

    private boolean wantShot() {
        return Prefs.isStatsEnabled(this) && Prefs.isShotsEnabled(this);
    }

    private boolean wantUsShot() {
        return Prefs.isUsStatsEnabled(this) && Prefs.isShotsEnabled(this);
    }

    private long lastUsTapAt = 0L;
    private long lastUsHbAt = 0L;
    private static final long US_TAP_COOLDOWN_MS = 1500L;

    private String lastDiagMsg = "";

    /* يحفظ آخر 6 أسطر تشخيص (تظهر بإعدادات Uber أمريكا) */
    private void diag(String msg) {
        if (msg.equals(lastDiagMsg)) {
            return;
        }
        lastDiagMsg = msg;
        try {
            String time = new java.text.SimpleDateFormat("HH:mm:ss",
                    java.util.Locale.US).format(new java.util.Date());
            String old = Prefs.getUsDiag(this);
            String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
            StringBuilder sb = new StringBuilder(time + "  " + msg);
            for (int i = 0; i < lines.length && i < 5; i++) {
                sb.append("\n").append(lines[i]);
            }
            Prefs.setUsDiag(this, sb.toString());
        } catch (Exception ignored) {
        }
    }

    /* زر القبول مو ظاهر لخدمة إمكانية الوصول: نقرأ البطاقة ونلمس مكان الزر */
    private boolean handleUsHiddenButton(String rawText,
                                         String normalizedScreen,
                                         long now) {
        try {
            String clean = stripDiacritics(normalizeDigits(rawText));

            Double km = extractUsKm(clean);
            Integer minutes = extractUsMinutes(clean);

            if (km == null && minutes == null) {
                return false;
            }

            if (now - lastUsTapAt < 3000L) {
                return false;
            }

            float maxKm = Prefs.getUsMaxDistanceKm(this);
            int maxMinutes = Prefs.getUsMaxMinutes(this);

            boolean kmOk = maxKm < 0 || (km != null && km <= maxKm);
            boolean minutesOk = maxMinutes < 0
                    || (minutes != null && minutes <= maxMinutes);

            if (!kmOk || !minutesOk) {
                return false;
            }

            String signature = "US|" + buildSignature(normalizedScreen, km);
            if (signature.equals(lastHandledSignature)
                    && now - lastHandledAt < DUP_WINDOW_MS) {
                return false;
            }

            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE))
                    .getDefaultDisplay().getRealMetrics(dm);

            float yPct = Prefs.getUsTapYPercent(this);
            int x = (int) (dm.widthPixels * 0.5f);
            int y = (int) (dm.heightPixels * (yPct / 100f));

            boolean sent = tapAt(x, y);
            lastTapMethod = sent ? "لمس بالإحداثيات" : "فشل";
            markClick();

            diag("الزر مخفي، لمست بالإحداثيات (" + x + "," + y + ") أُرسلت=" + sent
                    + " | كم=" + km + " وقت=" + minutes
                    + " | الحد: كم=" + maxKm + " وقت=" + maxMinutes);

            if (sent) {
                lastUsTapAt = now;
                onAcceptedClick();
                startUsBurst();
                lastHandledSignature = signature;
                lastHandledAt = now;
            }

            return sent;

        } catch (Exception e) {
            Log.d(TAG, "US hidden خطأ: " + e);
            return false;
        }
    }

    private void handleUberUs(AccessibilityNodeInfo matchButton,
                              AccessibilityNodeInfo rootNode,
                              String rawText,
                              String normalizedScreen,
                              long now) {
        try {
            handleUberUsInner(matchButton, rootNode, rawText,
                    normalizedScreen, now);
        } catch (Exception e) {
            Log.d(TAG, "US خطأ: " + e);
            diag("⚠ خطأ: " + e);
        }
    }

    private void handleUberUsInner(AccessibilityNodeInfo matchButton,
                              AccessibilityNodeInfo rootNode,
                              String rawText,
                              String normalizedScreen,
                              long now) {

        if (!Prefs.isUsEnabled(this)) {
            diag("أتمتة Uber أمريكا مطفية من الإعدادات");
            return;
        }

        String clean = stripDiacritics(normalizeDigits(rawText));

        Double km = extractUsKm(clean);
        Integer minutes = extractUsMinutes(clean);

        Log.d(TAG, "US نص الشاشة: "
                + clean.substring(0, Math.min(300, clean.length())));
        Log.d(TAG, "US قراءة: كم=" + km + " دقايق=" + minutes);

        float maxKm = Prefs.getUsMaxDistanceKm(this);
        int maxMinutes = Prefs.getUsMaxMinutes(this);

        boolean kmOk = maxKm < 0 || (km != null && km <= maxKm);
        boolean minutesOk = maxMinutes < 0
                || (minutes != null && minutes <= maxMinutes);

        if (now - lastUsTapAt < US_TAP_COOLDOWN_MS) {
            return;
        }

        String signature = "US|" + buildSignature(normalizedScreen, km);

        if (signature.equals(lastHandledSignature)
                && now - lastHandledAt < DUP_WINDOW_MS) {
            return;
        }

        String reason;
        if (!kmOk) {
            reason = (km == null) ? "NO_KM" : "OUT_KM";
        } else if (!minutesOk) {
            reason = (minutes == null) ? "NO_MIN" : "OUT_MIN";
        } else {
            reason = "OK";
        }

        diag("قراءة: كم=" + km + " وقت=" + minutes
                + " | الحد: كم=" + maxKm + " وقت=" + maxMinutes
                + " | النتيجة=" + reason);

        /* مكان الزبون + الوجهة من نصوص البطاقة */
        String[] places = extractUsPlaces(rootNode, matchButton);
        double kmLog = km != null ? km : -1;
        int minLog = minutes != null ? minutes : -1;

        if (!reason.equals("OK")) {

            /* ما قدرت أقرا المسافة/الوقت: ما أرفض طلب مجهول */
            if (reason.startsWith("NO_")) {
                diag("ما قدرت أقرا المسافة/الوقت (" + reason + ") ما سوّيت شي");
                return;
            }

            /* خارج الشروط: نضغط X (إذا الميزة شغّالة) */
            if (!Prefs.isUsAutoRejectEnabled(this)) {
                diag("خارج المطلوب (" + reason + ") بس التجاهل التلقائي مطفي");
                return;
            }

            /* لقطة واضحة للرحلات المتجاهلة فقط: ننتظر البطاقة تكتمل */
            if (wantUsShot() && mustWaitForShot(signature, now)) {
                return;
            }

            ShotHelper.Req shotRej = ShotHelper.begin(this, wantUsShot());

            AccessibilityNodeInfo dismiss =
                    findUsDismissButton(rootNode, matchButton);

            boolean closed;
            String how;

            if (dismiss != null) {
                closed = tapNode(dismiss);
                how = "زر X";
            } else {
                int[] pt = usDismissFallbackPoint(rootNode, matchButton);
                if (pt == null) {
                    diag("ما لقيت زر X (" + reason + ")");
                    return;
                }
                closed = tapAt(pt[0], pt[1]);
                lastTapMethod = closed ? "لمس" : "فشل";
                how = "X بالإحداثيات";
            }

            markClick();
            Log.d(TAG, "Uber أمريكا -> X (" + reason + "): " + closed);
            diag("ضغطت " + how + " (" + reason + ") طريقة=" + lastTapMethod
                    + " نجح=" + closed);

            if (closed) {
                lastUsTapAt = now;
                if (Prefs.isUsStatsEnabled(this)) {
                    ShotHelper.attach(this, shotRej, TripStats.recordUs(
                            this, "rejected", kmLog, minLog,
                            places[0], places[1], reason, maxKm, maxMinutes,
                            clickLatMs, clickScanMs, clickPh));
                }
                lastHandledSignature = signature;
                lastHandledAt = now;
            }
            return;
        }

        if (!Prefs.isUsAutoAcceptEnabled(this)) {
            diag("الطلب مناسب بس القبول التلقائي مطفي");
            return;
        }

        AccessibilityNodeInfo tapTarget =
                findNodeByTexts(rootNode, US_ACCEPT_TEXTS);
        if (tapTarget == null) {
            tapTarget = matchButton;
        }

        ShotHelper.Req shotAcc = ShotHelper.begin(this, wantUsShot());

        boolean result = tapNode(tapTarget);
        markClick();

        Log.d(TAG, "Uber أمريكا -> تطابق: " + result
                + " | كم=" + km + " | دقايق=" + minutes);
        diag("ضغطت زر القبول طريقة=" + lastTapMethod + " نجح=" + result);

        if (result) {
            lastUsTapAt = now;
            onAcceptedClick();
            startUsBurst();
            if (Prefs.isUsStatsEnabled(this)) {
                ShotHelper.attach(this, shotAcc, TripStats.recordUs(
                        this, "accepted", kmLog, minLog,
                        places[0], places[1], "OK", maxKm, maxMinutes,
                        clickLatMs, clickScanMs, clickPh));
            }
            lastHandledSignature = signature;
            lastHandledAt = now;
        }
    }

    /* ===== ضغطات سريعة متتالية حتى يختفي زر القبول (Uber أمريكا) ===== */
    private static final long US_BURST_GAP_MS = 100L;
    private static final long US_BURST_MAX_MS = 3500L;
    private static final int US_BURST_MAX_TAPS = 30;
    private final AtomicBoolean usBurstOn = new AtomicBoolean(false);

    private boolean hasAnyText(AccessibilityNodeInfo root, String t) {
        List<AccessibilityNodeInfo> n = root.findAccessibilityNodeInfosByText(t);
        return n != null && !n.isEmpty();
    }

    private void startUsBurst() {
        if (!usBurstOn.compareAndSet(false, true)) {
            return;
        }
        final Handler h = scanHandler;
        final long start = android.os.SystemClock.uptimeMillis();
        final int[] taps = {0};

        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean again = false;
                try {
                    if (FloatingService.isAutomationRunning
                            && android.os.SystemClock.uptimeMillis() - start
                            < US_BURST_MAX_MS
                            && taps[0] < US_BURST_MAX_TAPS) {

                        AccessibilityNodeInfo root = getRootInActiveWindow();
                        CharSequence pk = root != null
                                ? root.getPackageName() : null;

                        if (root != null && pk != null
                                && pk.toString().startsWith("com.ubercab")) {

                            AccessibilityNodeInfo btn =
                                    findNodeByTexts(root, US_ACCEPT_TEXTS);

                            if (btn != null) {
                                tapNode(btn);
                                taps[0]++;
                                again = true;
                            } else if (hasAnyText(root, "على بعد")) {
                                /* الزر مخفي: نكرر اللمس بالإحداثيات */
                                android.util.DisplayMetrics dm =
                                        new android.util.DisplayMetrics();
                                ((android.view.WindowManager)
                                        getSystemService(WINDOW_SERVICE))
                                        .getDefaultDisplay().getRealMetrics(dm);
                                float yPct = Prefs.getUsTapYPercent(
                                        MyAccessibilityService.this);
                                tapAt((int) (dm.widthPixels * 0.5f),
                                        (int) (dm.heightPixels * (yPct / 100f)));
                                taps[0]++;
                                again = true;
                            }
                            /* غير هيك: الزر والبطاقة اختفوا = انقبلت الرحلة */
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "US burst خطأ: " + e);
                } finally {
                    if (again) {
                        h.postDelayed(this, US_BURST_GAP_MS);
                    } else {
                        diag("انتهت الضغطات السريعة، عدد الضغطات=" + taps[0]);
                        usBurstOn.set(false);
                    }
                }
            }
        }, US_BURST_GAP_MS);
    }

    private static class TNode {
        String text;
        Rect r = new Rect();
    }

    private void collectTNodes(AccessibilityNodeInfo node, List<TNode> out) {

        if (node == null) {
            return;
        }

        CharSequence t = node.getText() != null
                ? node.getText() : node.getContentDescription();

        if (t != null && t.toString().trim().length() > 0) {
            TNode n = new TNode();
            n.text = t.toString().trim();
            node.getBoundsInScreen(n.r);
            out.add(n);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTNodes(child, out);
            }
        }
    }

    private String cleanUs(String t) {
        return stripDiacritics(normalizeDigits(t));
    }

    /* يرجع {مكان الزبون، الوجهة}: النصين بين سطر الوقت/المسافة وزر «تطابق» */
    private String[] extractUsPlaces(AccessibilityNodeInfo root,
                                     AccessibilityNodeInfo matchButton) {

        List<TNode> all = new ArrayList<>();
        collectTNodes(root, all);

        Rect btn = new Rect();
        matchButton.getBoundsInScreen(btn);

        /* أسفل نقطة لسطر الوقت/المسافة فوق الزر */
        int anchorBottom = Integer.MIN_VALUE;
        for (TNode t : all) {
            String c = cleanUs(t.text);
            boolean info = US_KM_PATTERN.matcher(c).find()
                    || US_MINUTES_PATTERN.matcher(c).find();
            if (info && t.r.bottom <= btn.top && t.r.bottom > anchorBottom) {
                anchorBottom = t.r.bottom;
            }
        }

        List<TNode> cand = new ArrayList<>();
        for (TNode t : all) {
            if (anchorBottom != Integer.MIN_VALUE && t.r.top < anchorBottom - 2) continue;
            if (t.r.bottom > btn.top + 2) continue;
            if (t.text.length() < 3) continue;
            if (cleanUs(t.text).matches("[0-9\\s.,:]+")) continue;
            boolean isButton = false;
            for (String b : US_ACCEPT_TEXTS) {
                if (t.text.contains(b)) isButton = true;
            }
            if (isButton) continue;
            cand.add(t);
        }

        java.util.Collections.sort(cand, (a, b) -> a.r.top - b.r.top);

        String pickup = cand.size() > 0 ? tidyPlace(cand.get(0).text) : "";
        String dest = cand.size() > 1 ? tidyPlace(cand.get(1).text) : "";

        return new String[]{pickup, dest};
    }

    /* يشيل الرقم اللي بأول العنوان (مثل «9, الكوفة» -> «الكوفة») */
    private String tidyPlace(String t) {
        String c = t.replaceFirst("^[0-9\\u0660-\\u0669]+\\s*[,،]\\s*", "").trim();
        return c.length() > 60 ? c.substring(0, 60) : c;
    }

    /* زر X (إغلاق/رفض الطلب) بأعلى يسار البطاقة */
    private AccessibilityNodeInfo findUsDismissButton(
            AccessibilityNodeInfo root, AccessibilityNodeInfo matchButton) {

        Rect btn = new Rect();
        matchButton.getBoundsInScreen(btn);

        List<TNode> all = new ArrayList<>();
        collectTNodes(root, all);

        /* نص «على بعد X د» الكبير: نقيس عليه مكان X */
        Rect minR = null;
        for (TNode t : all) {
            String c = cleanUs(t.text);
            if (US_MINUTES_PATTERN.matcher(c).find()
                    && !US_KM_PATTERN.matcher(c).find()
                    && t.r.bottom <= btn.top
                    && (minR == null || t.r.height() > minR.height())) {
                minR = t.r;
            }
        }

        List<AccessibilityNodeInfo> clickables = new ArrayList<>();
        collectClickables(root, clickables);

        AccessibilityNodeInfo byDesc = null;
        AccessibilityNodeInfo best = null;
        int bestLeft = Integer.MAX_VALUE;

        for (AccessibilityNodeInfo n : clickables) {

            if (n.equals(matchButton)) {
                continue;
            }

            Rect r = new Rect();
            n.getBoundsInScreen(r);

            if (r.width() <= 0 || r.height() <= 0) continue;
            /* نتجاهل صف الأزرار السفلي (تطابق + الخريطة) */
            if (r.top >= btn.top - 10) continue;

            int cy = r.centerY();
            if (minR != null) {
                if (cy < minR.top - 2 * minR.height() || cy > minR.bottom) continue;
            } else {
                if (cy < btn.top - 6 * btn.height()) continue;
            }

            String sub = subtreeText(n).trim();
            String low = sub.toLowerCase();
            boolean closeWord = low.contains("اغلاق") || low.contains("إغلاق")
                    || low.contains("close") || low.contains("dismiss")
                    || low.contains("رفض") || low.contains("تجاهل");

            if (!sub.isEmpty() && !closeWord) continue;

            if (closeWord) {
                byDesc = n;
                break;
            }

            if (r.left < bestLeft) {
                bestLeft = r.left;
                best = n;
            }
        }

        return byDesc != null ? byDesc : best;
    }

    /* أي عقدة فيها النص (حتى لو مو قابلة للضغط) */
    private AccessibilityNodeInfo findNodeByTexts(
            AccessibilityNodeInfo root, String[] texts) {

        for (String text : texts) {

            List<AccessibilityNodeInfo> nodes =
                    root.findAccessibilityNodeInfosByText(text);

            if (nodes == null) {
                continue;
            }

            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    return node;
                }
            }
        }

        return null;
    }

    private String lastTapMethod = "";

    /* ضغط على عقدة: لمس حقيقي بالإحداثيات أولاً، وإذا ما نفع ACTION_CLICK */
    private boolean tapNode(AccessibilityNodeInfo node) {

        Rect r = new Rect();
        node.getBoundsInScreen(r);

        if (tapAt(r.centerX(), r.centerY())) {
            lastTapMethod = "لمس";
            return true;
        }

        AccessibilityNodeInfo clickable = findClickableParent(node);

        if (clickable != null
                && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            lastTapMethod = "كليك";
            return true;
        }

        lastTapMethod = "فشل";
        return false;
    }

    private boolean tapAt(int x, int y) {

        if (Build.VERSION.SDK_INT < 24 || x <= 0 || y <= 0) {
            return false;
        }

        try {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 50);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke).build();

            return dispatchGesture(gesture, null, null);

        } catch (Exception e) {
            Log.d(TAG, "tapAt فشل: " + e.getMessage());
            return false;
        }
    }

    /* مكان X التقريبي (أعلى يسار البطاقة) إذا ما لقينا عقدة قابلة للضغط */
    private int[] usDismissFallbackPoint(AccessibilityNodeInfo root,
                                         AccessibilityNodeInfo matchButton) {

        Rect btn = new Rect();
        matchButton.getBoundsInScreen(btn);

        List<TNode> all = new ArrayList<>();
        collectTNodes(root, all);

        Rect minR = null;
        for (TNode t : all) {
            String c = cleanUs(t.text);
            if (US_MINUTES_PATTERN.matcher(c).find()
                    && !US_KM_PATTERN.matcher(c).find()
                    && t.r.bottom <= btn.top
                    && (minR == null || t.r.height() > minR.height())) {
                minR = t.r;
            }
        }

        if (minR == null) {
            return null;
        }

        int w = getResources().getDisplayMetrics().widthPixels;
        int x = (int) (w * 0.142f);
        int y = minR.centerY() - (int) (minR.height() * 0.8f);

        return new int[]{x, y};
    }

    private void collectClickables(AccessibilityNodeInfo node,
                                   List<AccessibilityNodeInfo> out) {

        if (node == null) {
            return;
        }

        if (node.isClickable()) {
            out.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectClickables(child, out);
            }
        }
    }

    private String subtreeText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        collectAllText(node, sb);
        return sb.toString();
    }

    private void collectTextList(AccessibilityNodeInfo node, List<String> out) {

        if (node == null) {
            return;
        }

        if (node.getText() != null) {
            out.add(node.getText().toString());
        } else if (node.getContentDescription() != null) {
            out.add(node.getContentDescription().toString());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTextList(child, out);
                child.recycle();
            }
        }
    }

    private Double extractUsKm(String text) {

        try {
            Matcher m = US_KM_PATTERN.matcher(text);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }

            Matcher meters = US_METERS_PATTERN.matcher(text);
            if (meters.find()) {
                return Double.parseDouble(meters.group(1)) / 1000.0;
            }
        } catch (NumberFormatException ignored) {
        }

        return null;
    }

    private Integer extractUsMinutes(String text) {

        try {
            Matcher m = US_MINUTES_PATTERN.matcher(text);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (NumberFormatException ignored) {
        }

        return null;
    }

    /* يشيل التشكيل (مثل الضمة بكلمة بُعد) */
    private String stripDiacritics(String input) {
        return input.replaceAll("[\\u064B-\\u065F\\u0670]", "");
    }

    /* أول جزء من العنوان (اسم الحي/المنطقة) إذا الحي مو من القائمة */
    private String firstAddressPart(String address) {

        if (address == null) {
            return "";
        }

        String first = address.split("[,،]")[0].trim();

        return first.length() > 40 ? first.substring(0, 40) : first;
    }

    /* بصمة ثابتة: بدون أرقام حتى ما يؤثر عدّاد الوقت */
    private String buildSignature(String screenText, Double distance) {

        String compact = screenText
                .replaceAll("[0-9\u0660-\u0669\u06F0-\u06F9]", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (compact.length() > 400) {
            compact = compact.substring(0, 400);
        }

        return (distance == null ? "no-distance" : String.valueOf(distance))
                + "|" + compact;
    }

    private String detectCategory(String normalized) {

        String t = " " + normalized + " ";

        if (t.contains("متعدد الوجهات")
                || t.contains("متعده الوجهات")
                || t.contains("وجهات متعدده")) {
            return "MULTI";
        }

        if (t.contains(" وفر ")) {
            return "SAVER";
        }

        return "NORMAL";
    }

    private boolean isTripEnded(String normalized) {

        for (String s : END_TEXTS) {
            if (normalized.contains(s)) {
                return true;
            }
        }

        return false;
    }

    private long extractPrice(String text) {

        String t = normalizeDigits(text);

        try {
            Matcher e = PRICE_EARN.matcher(t);
            if (e.find()) {
                return Math.round(Double.parseDouble(
                        e.group(1).replace(",", "")));
            }

            double max = 0;
            Matcher m = PRICE_AFTER.matcher(t);
            while (m.find()) {
                double v = Double.parseDouble(
                        m.group(1).replace(",", ""));
                if (v > max) max = v;
            }
            return Math.round(max);

        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long extractDurationMs(String text) {

        Matcher m = DURATION_PATTERN.matcher(normalizeDigits(text));

        if (m.find()) {
            long h = Long.parseLong(m.group(1));
            long mi = Long.parseLong(m.group(2));
            long s = Long.parseLong(m.group(3));
            return ((h * 60 + mi) * 60 + s) * 1000L;
        }

        return 0L;
    }

    private boolean isPreviousDriverMessage(String text) {

        return text.contains("قبل سائق اخر هذا الحجز")
                || (text.contains("سائق اخر")
                && text.contains("هذا الحجز"));
    }

    private AccessibilityNodeInfo findClickableByTexts(
            AccessibilityNodeInfo root, String[] texts) {

        for (String text : texts) {

            List<AccessibilityNodeInfo> nodes =
                    root.findAccessibilityNodeInfosByText(text);

            if (nodes == null) {
                continue;
            }

            for (AccessibilityNodeInfo node : nodes) {

                if (node == null) {
                    continue;
                }

                AccessibilityNodeInfo clickable =
                        findClickableParent(node);

                if (clickable != null) {
                    return clickable;
                }
            }
        }

        return null;
    }

    /* عنوان الزبون: يُلتقط أثناء نفس المرور على الشجرة (بدل مرور ثانٍ) */
    private String pickupFound = null;

    private static boolean looksLikePickupAddress(CharSequence t) {
        String s = t.toString();
        int commas = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '\u060C') commas++;
        }
        return commas >= 2 && (s.contains("النجف")
                || s.contains("محافظة") || s.contains("قضاء"));
    }

    private void collectAllText(
            AccessibilityNodeInfo node, StringBuilder out) {

        if (node == null) {
            return;
        }

        if (node.getText() != null) {
            out.append(node.getText()).append(" ");
            if (pickupFound == null && looksLikePickupAddress(node.getText())) {
                pickupFound = node.getText().toString();
            }
        }

        if (node.getContentDescription() != null) {
            out.append(node.getContentDescription()).append(" ");
        }

        int childCount = node.getChildCount();

        for (int i = 0; i < childCount; i++) {

            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                collectAllText(child, out);
                child.recycle();
            }
        }
    }

    private Double extractDistanceKm(String text) {

        String normalized = normalizeDigits(text);

        Matcher matcher = DISTANCE_PATTERN.matcher(normalized);

        if (matcher.find()) {

            String rawNumber = matcher.group(1).replace(",", ".");

            try {
                return Double.parseDouble(rawNumber);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String normalizeDigits(String input) {

        StringBuilder sb = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); i++) {

            char c = input.charAt(i);

            if (c >= '\u0660' && c <= '\u0669') {
                sb.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                sb.append((char) ('0' + (c - '\u06F0')));
            } else if (c == '\u066B') {
                sb.append('.');
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private String normalizeArabicText(String input) {

        return input
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('،', ' ')
                .replace(',', ' ')
                .replace(':', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private AccessibilityNodeInfo findClickableParent(
            AccessibilityNodeInfo node) {

        AccessibilityNodeInfo current = node;

        while (current != null) {

            if (current.isClickable()) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    @Override
    public void onInterrupt() {
        scanHandler.removeCallbacks(scanRunnable);
        scanPending = false;
        Log.d(TAG, "تم مقاطعة الخدمة");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        scanHandler.removeCallbacks(scanRunnable);
        scanPending = false;
        return super.onUnbind(intent);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "تم الاتصال بخدمة Accessibility بنجاح");
    }
}
