package com.example.uiautomation;

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Set;

/** فلتر أحياء النجف: يقرأ حي مكان الزبون (الانطلاق) فقط من شاشة الطلب. */
public final class NeighborhoodFilter {

    private NeighborhoodFilter() {}

    /** القائمة الكاملة للأحياء (تظهر بشاشة الاختيار). عدّلها متى ما تريد. */
    public static final String[] ALL = {
            "حي النصر", "حي المهندسين", "حي أبو طالب", "حي الغزي",
            "حي الجمعية", "حي السلام", "حي الجامعة", "حي الوفاء",
            "حي الفرات", "حي الغدير", "حي العدالة", "حي الأمير",
            "حي الاشتراكي", "حي السواق", "حي الحوراء", "حي الزهراء",
            "حي القادسية", "عماد سكر", "حي الأنصار", "حي القدس",
            "مجمع بوابة النجف", "مجمع طيبة", "حي النداء", "حي الجديدة",
            "حي العسكري", "حي المكرمة", "حي الميلاد", "حي الهندية",
            "حي الجزيرة", "حي السلام الجديد", "حي المعارض", "حي النفط",
            "حي العلماء", "حي الحسين", "حي الحنانة", "حي الصحة",
            "حي الإسكان", "حي السعد", "حي عدن", "حي المعلمين",
            "حي الإمام المهدي", "حي الحرفيين", "حي الشرطة",
            "حي العمارات السكنية", "الحي الصناعي"
    };

    /** توحيد النص العربي للمقارنة (همزات، تاء مربوطة، تشكيل، كلمة حي). */
    public static String norm(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\u064B-\\u065F\\u0640]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي')
                .replaceAll("\\s+", " ").trim();
        if (s.startsWith("حي ")) s = s.substring(3).trim();
        return s;
    }

    /** يرجع اسم الحي (من القائمة) لعنوان الانطلاق، أو null إذا غير معروف. */
    public static String matchNeighborhood(String address) {
        if (address == null) return null;
        String first = address.split("[,،]")[0];
        String seg = norm(first);
        if (seg.isEmpty()) return null;
        String best = null;
        int bestLen = -1;
        for (String name : ALL) {
            String n = norm(name);
            if (seg.equals(n) || seg.startsWith(n + " ")) {
                if (n.length() > bestLen) {
                    best = name;
                    bestLen = n.length();
                }
            }
        }
        return best;
    }

    /** يدور على أول نص بالشاشة يشبه العنوان = مكان الزبون (الانطلاق). */
    public static String findPickupAddress(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence t = node.getText();
        if (t != null) {
            String s = t.toString();
            int commas = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ',' || c == '،') commas++;
            }
            if (commas >= 2 && (s.contains("النجف") || s.contains("محافظة") || s.contains("قضاء"))) {
                return s;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            String r = findPickupAddress(child);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * true = الحي مسموح.
     * إذا الفلتر مطفي أو الحي غير معروف => مسموح (والقرار يبقى للمسافة فقط).
     */
    public static boolean isAllowed(Context ctx, AccessibilityNodeInfo root) {
        if (!Prefs.isNeighborhoodFilterEnabled(ctx)) return true;
        String address = findPickupAddress(root);
        String name = matchNeighborhood(address);
        if (name == null) return true;
        Set<String> selected = Prefs.getSelectedNeighborhoods(ctx);
        return selected.contains(name);
    }
}