package com.example.autoaccept

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoAcceptService : AccessibilityService() {

    private var last = 0L
    private val acceptWords = setOf("تطابق", "اقبل", "accept")
    private val rejectWords = setOf("لا شكرا")
    private val kmRe = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:كلم|كم|km)""")

    private fun sp() = getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private fun on(k: String, d: Boolean) = (sp().getString(k, if (d) "1" else "0") ?: "0") == "1"

    /** توحيد النص: تشكيل، همزات، ياء، أرقام عربية */
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

    private class Item(val node: AccessibilityNodeInfo, val text: String)

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Item>) {
        if (n == null) return
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank()) out.add(Item(n, t))
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = n
        while (x != null) {
            if (x.isClickable && x.isEnabled) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            x = x.parent
        }
        return false
    }

    private fun inc(k: String) {
        if (!on("stats", true)) return
        val v = (sp().getString(k, "0") ?: "0").toIntOrNull() ?: 0
        sp().edit().putString(k, (v + 1).toString()).apply()
    }

    private fun beep3() {
        if (!on("sound", false)) return
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        val h = Handler(Looper.getMainLooper())
        for (i in 0..2) h.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }, i * 220L)
        h.postDelayed({ tg.release() }, 900)
    }

    /** حي الانطلاق: أول نص فيه فاصلتين أو أكثر ويخص النجف */
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!on("enabled", true)) return
        val now = System.currentTimeMillis()
        if (now - last < 700) return
        val root = rootInActiveWindow ?: return
        val items = ArrayList<Item>()
        collect(root, items)
        if (items.isEmpty()) return

        // 1) إغلاق رسالة «موافق»
        if (on("closeOk", true)) {
            items.firstOrNull { cmp(it.text) == "موافق" }?.let {
                if (click(it.node)) { last = now; return }
            }
        }

        // 2) زر القبول
        val acceptBtn = items.firstOrNull { cmp(it.text) in acceptWords } ?: return

        // 3) المسافة من نص «على بُعد X كلم»
        val maxKm = (sp().getString("maxKm", "3") ?: "3").toDoubleOrNull() ?: 3.0
        var okDistance = true
        if (maxKm >= 0) {
            val d = items.map { norm(it.text) }
                .filter { it.contains("بعد") }
                .firstNotNullOfOrNull { kmRe.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            okDistance = d != null && d <= maxKm
        }
        val ok = okDistance && pickupAllowed(items)

        if (ok && on("accept", true)) {
            if (click(acceptBtn.node)) { last = now; inc("stat_accept"); beep3() }
        } else if (!ok && on("reject", false)) {
            val rej = items.firstOrNull { cmp(it.text) in rejectWords }
            if (rej != null && click(rej.node)) { last = now; inc("stat_reject") }
        }
    }

    override fun onInterrupt() {}
}
