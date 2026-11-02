package com.example.autoaccept

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoAcceptService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var lastAction = 0L
    private var lastScan = 0L
    private var lastDiag = ""
    private var lastHb = 0L
    private var lastFallback = 0L
    private var fab: TextView? = null

    private val acceptWords = setOf("تطابق", "اقبل", "accept")
    private val rejectWords = setOf("لا شكرا")
    private val kmNear = Regex("""بعد\s*([0-9]+(?:\.[0-9]+)?)\s*(?:كلم|كم|km)(?![\u0621-\u064A])""")
    private val kmAny = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:كلم|كم|km)(?![\u0621-\u064A])""")

    private fun sp(): SharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private fun on(k: String, d: Boolean) = (sp().getString(k, if (d) "1" else "0") ?: "0") == "1"

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "floating" || key == "enabled") main.post { refreshFab() }
    }

    private val poll = object : Runnable {
        override fun run() {
            scan()
            main.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sp().registerOnSharedPreferenceChangeListener(prefListener)
        refreshFab()
        main.removeCallbacks(poll)
        main.post(poll)
    }

    override fun onDestroy() {
        main.removeCallbacks(poll)
        sp().unregisterOnSharedPreferenceChangeListener(prefListener)
        removeFab()
        super.onDestroy()
    }

    // ---------------- الزر العائم ----------------
    private fun removeFab() {
        val v = fab ?: return
        try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
        fab = null
    }

    private fun refreshFab() {
        if (!on("floating", true)) { removeFab(); return }
        if (fab == null) createFab()
        val v = fab ?: return
        val enabled = on("enabled", true)
        (v.background as? GradientDrawable)?.setColor(Color.parseColor(if (enabled) "#16A34A" else "#DC2626"))
        v.text = if (enabled) "⚡" else "⏸"
    }

    private fun createFab() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val size = (52 * d).toInt()
        val v = TextView(this)
        v.gravity = Gravity.CENTER
        v.textSize = 22f
        v.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setStroke((2 * d).toInt(), Color.parseColor("#D4AF37"))
        v.background = bg
        v.alpha = 0.9f

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (12 * d).toInt()
        lp.y = (260 * d).toInt()

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (Math.abs(dx) > 12 * d || Math.abs(dy) > 12 * d) moved = true
                    if (moved) {
                        lp.x = ox + dx.toInt()
                        lp.y = oy + dy.toInt()
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    sp().edit().putString("enabled", if (on("enabled", true)) "0" else "1").apply()
                }
            }
            true
        }
        try { wm.addView(v, lp); fab = v } catch (_: Exception) {}
    }

    // ---------------- أدوات النص ----------------
    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch in '\u064B'..'\u065F' || ch == '\u0640' -> {}
                ch == 'أ' || ch == 'إ' || ch == 'آ' -> sb.append('ا')
                ch == 'ة' -> sb.append('ه')
                ch == 'ى' -> sb.append('ي')
                ch in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                ch == '٫' -> sb.append('.')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun cmp(s: String) = norm(s).replace(Regex("[,،.:]"), "").replace(Regex("\\s+"), " ").trim()

    private class Item(val node: AccessibilityNodeInfo, val text: String, val pkg: String)

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Item>, pkg: String) {
        if (n == null) return
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank()) out.add(Item(n, t, pkg))
        for (i in 0 until n.childCount) collect(n.getChild(i), out, pkg)
    }

    /** نتجاهل نوافذ النظام والكيبورد وتطبيقنا */
    private fun skipPkg(p: String) =
        p == packageName || p == "com.android.systemui" || p.contains("inputmethod") || p.isEmpty()

    private fun isAccept(t: String): Boolean {
        val c = cmp(t)
        return c.length <= 14 && acceptWords.any { c == it || c.contains(it) }
    }

    private fun isReject(t: String): Boolean {
        val c = cmp(t)
        return c.length <= 16 && c.contains("لا شكر")
    }

    // ---------------- الضغط ----------------
    private fun tap(n: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val path = Path()
        path.moveTo(r.exactCenterX(), r.exactCenterY())
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun clickUp(n: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = n
        while (x != null) {
            if (x.isClickable && x.isEnabled && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            x = x.parent
        }
        return false
    }

    /** tapFirst=true: لمسة حقيقية بالإحداثيات أولاً (تطبيق Uber يتجاهل ACTION_CLICK أحيانًا) */
    private fun press(n: AccessibilityNodeInfo, tapFirst: Boolean): String {
        if (tapFirst) {
            if (tap(n)) return "لمس"
            if (clickUp(n)) return "كليك"
        } else {
            if (clickUp(n)) return "كليك"
            if (tap(n)) return "لمس"
        }
        return "fail"
    }

    private fun inc(k: String) {
        if (!on("stats", true)) return
        val v = (sp().getString(k, "0") ?: "0").toIntOrNull() ?: 0
        sp().edit().putString(k, (v + 1).toString()).apply()
    }

    private fun beep3() {
        if (!on("sound", false)) return
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        for (i in 0..2) main.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }, i * 220L)
        main.postDelayed({ tg.release() }, 900)
    }

    private fun ts() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun diag(s: String) {
        if (s != lastDiag) {
            lastDiag = s
            sp().edit().putString("diag", s).apply()
        }
    }

    private fun pickupAllowed(items: List<Item>): Boolean {
        if (!on("nbFilter", false)) return true
        val addr = items.map { it.text }.firstOrNull { t ->
            t.count { it == ',' || it == '،' } >= 2 &&
                (t.contains("النجف") || t.contains("محافظة") || t.contains("قضاء"))
        } ?: return true
        val seg = cmp(addr.split(',', '،')[0]).removePrefix("حي ").trim()
        val all = (sp().getString("nbAll", "") ?: "").split('|').filter { it.isNotEmpty() }
        val known = all.firstOrNull {
            val n = cmp(it).removePrefix("حي ").trim()
            seg == n || seg.startsWith("$n ")
        } ?: return true
        val selRaw = sp().getString("nbSelected", null) ?: return true
        return selRaw.split('|').contains(known)
    }

    // ---------------- الفحص ----------------
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() - lastScan < 120) return
        scan()
    }

    private fun scan() {
        if (!on("enabled", true)) return
        val now = System.currentTimeMillis()
        lastScan = now
        if (now - lastAction < 500) return

        val items = ArrayList<Item>()
        try {
            val ws = windows
            if (ws != null) {
                for (w in ws) {
                    val r = w.root ?: continue
                    val pk = r.packageName?.toString() ?: ""
                    if (skipPkg(pk)) continue
                    collect(r, items, pk)
                }
            }
        } catch (_: Exception) {}
        try {
            val ar = rootInActiveWindow
            val apk = ar?.packageName?.toString() ?: ""
            if (ar != null && !skipPkg(apk) && items.none { it.pkg == apk }) collect(ar, items, apk)
        } catch (_: Exception) {}
        if (now - lastHb > 2000) {
            lastHb = now
            sp().edit().putString("hb", "${ts()} | نصوص التطبيقات: ${items.size} | " + items.map { it.pkg }.distinct().joinToString(",")).apply()
        }
        if (items.isEmpty()) return

        // إغلاق رسالة «موافق»
        if (on("closeOk", true)) {
            val ok = items.firstOrNull { cmp(it.text) == "موافق" }
            if (ok != null && press(ok.node, false) != "fail") { lastAction = now; return }
        }

        val joined = items.joinToString(" | ") { norm(it.text) }
        val dist = kmNear.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: kmAny.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()

        val acceptBtn = items.firstOrNull { isAccept(it.text) }

        val maxKm = (sp().getString("maxKm", "3") ?: "3").toDoubleOrNull() ?: 3.0
        val okDistance = maxKm < 0 || (dist != null && dist <= maxKm)
        val okNb = pickupAllowed(items)
        val ok = okDistance && okNb

        if (acceptBtn == null) {
            if (joined.contains("بعد") && dist != null) {
                if (ok && on("accept", true) && on("tapFallback", true) && now - lastFallback > 1500) {
                    // الزر مخفي عن سهولة الوصول: نلمس مكانه المعتاد بأسفل البطاقة
                    lastFallback = now
                    lastAction = now
                    val dm = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
                    val yPct = (sp().getString("tapY", "87.3") ?: "87.3").toFloatOrNull() ?: 87.3f
                    val x = dm.widthPixels * 0.5f
                    val y = dm.heightPixels * (yPct / 100f)
                    val path = Path()
                    path.moveTo(x, y)
                    val sent = dispatchGesture(
                        GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, 40)).build(),
                        null, null
                    )
                    beep3()
                    diag("${ts()} الزر مخفي، ضغطت بالإحداثيات (${x.toInt()}, ${y.toInt()}) أُرسلت=$sent | المسافة: $dist كم")
                } else {
                    diag("${ts()} ظهر طلب لكن ما لقيت زر القبول. النصوص: " +
                        items.take(25).joinToString(" / ") { "${it.pkg.substringAfterLast('.')}: ${it.text}" })
                }
            }
            return
        }

        if (ok && on("accept", true)) {
            val how = press(acceptBtn.node, true)
            lastAction = now
            if (how != "fail") { inc("stat_accept"); beep3() }
            diag("${ts()} المسافة: ${dist ?: "؟"} كم | الحد: ${if (maxKm < 0) "بدون" else maxKm} | قرار: قبول | الضغط: $how")
        } else if (!ok && on("reject", false)) {
            val rej = items.firstOrNull { isReject(it.text) }
            if (rej != null) {
                val how = press(rej.node, true)
                lastAction = now
                if (how != "fail") inc("stat_reject")
                diag("${ts()} المسافة: ${dist ?: "؟"} كم | الحد: $maxKm | قرار: تجاهل | الضغط: $how")
            } else {
                diag("${ts()} المسافة: ${dist ?: "؟"} كم خارج الحد، لكن ما لقيت زر «لا شكرًا» على الشاشة")
            }
        } else {
            diag("${ts()} المسافة: ${dist ?: "؟"} كم | الحد: ${if (maxKm < 0) "بدون" else maxKm} | ما صار شي (خارج الحد أو القبول مطفي)")
        }
    }

    override fun onInterrupt() {}
}
