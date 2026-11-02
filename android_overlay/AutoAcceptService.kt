package com.example.autoaccept

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoAcceptService : AccessibilityService() {

    private var lastClick = 0L

    private fun targets(): List<String> {
        val s = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("words", "اقبل,تطابق") ?: "اقبل,تطابق"
        return s.split(",", "،").map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        for (word in targets()) {
            for (node in root.findAccessibilityNodeInfosByText(word)) {
                if (tryClick(node)) return
            }
        }
    }

    private fun tryClick(start: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClick < 700) return false
        var n: AccessibilityNodeInfo? = start
        while (n != null) {
            if (n.isClickable && n.isEnabled) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                lastClick = now
                return true
            }
            n = n.parent
        }
        return false
    }

    override fun onInterrupt() {}
}
