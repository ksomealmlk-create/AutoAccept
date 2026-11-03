package com.example.uiautomation;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** يدير كل البيانات المحفوظة محليًا: القفل، إعداد المسافة، العدادات، وسجل آخر 5 ساعات */
public class Prefs {

    private static final String PREFS_NAME = "sakkar_prefs";

    private static final String KEY_UNLOCKED = "unlocked";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_MAX_DISTANCE = "max_distance_km";
    private static final String KEY_AUTO_ACCEPT = "auto_accept";
    private static final String KEY_AUTO_REJECT = "auto_reject";
    private static final String KEY_AUTO_CONFIRM_DIALOG = "auto_confirm_dialog";
    private static final String KEY_STATS_ENABLED = "stats_enabled";
    private static final String KEY_ACCEPTED_COUNT = "accepted_count";
    private static final String KEY_REJECTED_COUNT = "rejected_count";
    private static final String KEY_STATS_DATE = "stats_date";
    private static final String KEY_LOG = "order_log";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_NEIGH_ENABLED = "neighborhood_filter_enabled";
    private static final String KEY_NEIGH_SELECTED = "neighborhood_selected";

    private static final long LOG_WINDOW_MILLIS = 5L * 60 * 60 * 1000; // 5 ساعات

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ================= القفل عند أول فتح =================

    public static boolean isUnlocked(Context ctx) {
        return prefs(ctx).getBoolean(KEY_UNLOCKED, false);
    }

    public static void setUnlocked(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_UNLOCKED, true).apply();
    }

    // ================= اسم المستخدم =================

    public static String getUsername(Context ctx) {
        return prefs(ctx).getString(KEY_USERNAME, "");
    }

    public static void setUsername(Context ctx, String name) {
        prefs(ctx).edit().putString(KEY_USERNAME, name).apply();
    }

    // ================= الحد الأقصى للمسافة =================

    public static float getMaxDistanceKm(Context ctx) {
        return prefs(ctx).getFloat(KEY_MAX_DISTANCE, 3.0f); // افتراضيًا 3 كم
    }

    public static void setMaxDistanceKm(Context ctx, float value) {
        prefs(ctx).edit().putFloat(KEY_MAX_DISTANCE, value).apply();
    }

    // ================= إعدادات الأتمتة =================

    public static boolean isAutoAcceptEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_ACCEPT, true);
    }

    public static void setAutoAcceptEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_ACCEPT, value).apply();
    }

    public static boolean isAutoRejectEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_REJECT, true);
    }

    public static void setAutoRejectEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_REJECT, value).apply();
    }

    public static boolean isAutoConfirmDialogEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_CONFIRM_DIALOG, true);
    }

    public static void setAutoConfirmDialogEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_CONFIRM_DIALOG, value).apply();
    }

    public static boolean isStatsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_STATS_ENABLED, true);
    }

    public static void setStatsEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_STATS_ENABLED, value).apply();
    }

    /** لقطة شاشة لكل رحلة (تظهر بقائمة لقطات الرحلات) */
    public static boolean isShotsEnabled(Context ctx) {
        return prefs(ctx).getBoolean("shots_enabled", true);
    }

    public static void setShotsEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("shots_enabled", value).apply();
    }

    /** تنبيه صوتي (3 نغمات) عند قبول الطلب */
    public static boolean isSoundOnAcceptEnabled(Context ctx) {
        return prefs(ctx).getBoolean("sound_on_accept", true);
    }

    public static void setSoundOnAcceptEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("sound_on_accept", value).apply();
    }

    // ================= فلتر الأحياء =================

    public static boolean isNeighborhoodFilterEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NEIGH_ENABLED, false);
    }

    public static void setNeighborhoodFilterEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_NEIGH_ENABLED, value).apply();
    }

    /** الأحياء المختارة. إذا لم يختر المستخدم شيئًا بعد => كل الأحياء مختارة */
    public static Set<String> getSelectedNeighborhoods(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.contains(KEY_NEIGH_SELECTED)) {
            return new HashSet<>(Arrays.asList(NeighborhoodFilter.ALL));
        }
        Set<String> saved = p.getStringSet(KEY_NEIGH_SELECTED, new HashSet<String>());
        return new HashSet<>(saved);
    }

    public static void setSelectedNeighborhoods(Context ctx, Set<String> value) {
        prefs(ctx).edit().putStringSet(KEY_NEIGH_SELECTED, new HashSet<>(value)).apply();
    }

    // ================= Uber أمريكا (منفصل عن Uber العراق) =================

    private static final String KEY_US_ENABLED = "us_enabled";
    private static final String KEY_US_MAX_DISTANCE = "us_max_distance_km";
    private static final String KEY_US_MAX_MINUTES = "us_max_minutes";

    /** تشغيل/إيقاف أتمتة Uber أمريكا */
    public static boolean isUsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_US_ENABLED, true);
    }

    public static void setUsEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_US_ENABLED, value).apply();
    }

    private static final String KEY_US_AUTO_ACCEPT = "us_auto_accept";
    private static final String KEY_US_AUTO_REJECT = "us_auto_reject";
    private static final String KEY_US_STATS = "us_stats_enabled";

    private static final String KEY_US_DIAG = "us_diag";

    /** آخر نتائج فحص Uber أمريكا (للتشخيص) */
    public static String getUsDiag(Context ctx) {
        return prefs(ctx).getString(KEY_US_DIAG, "");
    }

    public static void setUsDiag(Context ctx, String value) {
        prefs(ctx).edit().putString(KEY_US_DIAG, value).apply();
    }

    /** قبول تلقائي (زر تطابق) */
    public static boolean isUsAutoAcceptEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_US_AUTO_ACCEPT, true);
    }

    public static void setUsAutoAcceptEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_US_AUTO_ACCEPT, value).apply();
    }

    /** تجاهل تلقائي (زر X) */
    public static boolean isUsAutoRejectEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_US_AUTO_REJECT, true);
    }

    public static void setUsAutoRejectEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_US_AUTO_REJECT, value).apply();
    }

    /** تسجيل إحصائية Uber أمريكا */
    public static boolean isUsStatsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_US_STATS, true);
    }

    public static void setUsStatsEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_US_STATS, value).apply();
    }

    /** أقصى مسافة (كم) لـ Uber أمريكا. -1 = بدون حد */
    public static float getUsMaxDistanceKm(Context ctx) {
        return prefs(ctx).getFloat(KEY_US_MAX_DISTANCE, 3.0f);
    }

    public static void setUsMaxDistanceKm(Context ctx, float value) {
        prefs(ctx).edit().putFloat(KEY_US_MAX_DISTANCE, value).apply();
    }

    /** ارتفاع نقطة اللمس (٪ من الشاشة) إذا زر القبول مخفي عن إمكانية الوصول */
    public static float getUsTapYPercent(Context ctx) {
        return prefs(ctx).getFloat("us_tap_y_percent", 87.3f);
    }

    public static void setUsTapYPercent(Context ctx, float value) {
        prefs(ctx).edit().putFloat("us_tap_y_percent", value).apply();
    }

    /** أقصى وقت وصول بالدقائق (1 إلى 6). -1 = بلا تحديد */
    public static int getUsMaxMinutes(Context ctx) {
        return prefs(ctx).getInt(KEY_US_MAX_MINUTES, 4);
    }

    public static void setUsMaxMinutes(Context ctx, int value) {
        prefs(ctx).edit().putInt(KEY_US_MAX_MINUTES, value).apply();
    }

    // ================= الثيم =================

    /** 0 = نظام الهاتف، 1 = نهاري، 2 = ليلي */
    public static int getThemeMode(Context ctx) {
        return prefs(ctx).getInt(KEY_THEME_MODE, 0);
    }

    public static void setThemeMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    // ================= العدادات اليومية =================

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** يصفّر العدادات تلقائيًا إذا تغيّر التاريخ (يوم جديد) */
    private static void resetCountersIfNewDay(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String savedDate = p.getString(KEY_STATS_DATE, "");
        String todayStr = today();
        if (!todayStr.equals(savedDate)) {
            p.edit()
                    .putString(KEY_STATS_DATE, todayStr)
                    .putInt(KEY_ACCEPTED_COUNT, 0)
                    .putInt(KEY_REJECTED_COUNT, 0)
                    .apply();
        }
    }

    public static int getAcceptedCount(Context ctx) {
        resetCountersIfNewDay(ctx);
        return prefs(ctx).getInt(KEY_ACCEPTED_COUNT, 0);
    }

    public static int getRejectedCount(Context ctx) {
        resetCountersIfNewDay(ctx);
        return prefs(ctx).getInt(KEY_REJECTED_COUNT, 0);
    }

    public static void incrementAccepted(Context ctx) {
        resetCountersIfNewDay(ctx);
        SharedPreferences p = prefs(ctx);
        int current = p.getInt(KEY_ACCEPTED_COUNT, 0);
        p.edit().putInt(KEY_ACCEPTED_COUNT, current + 1).apply();
    }

    public static void incrementRejected(Context ctx) {
        resetCountersIfNewDay(ctx);
        SharedPreferences p = prefs(ctx);
        int current = p.getInt(KEY_REJECTED_COUNT, 0);
        p.edit().putInt(KEY_REJECTED_COUNT, current + 1).apply();
    }

    // ================= سجل آخر 5 ساعات =================

    /** يضيف سجل جديد، ويحذف تلقائيًا أي سجل أقدم من 5 ساعات. استخدم distanceKm = -1 إذا لم تُكتشف مسافة */
    public static void addLogEntry(Context ctx, double distanceKm, boolean accepted) {
        SharedPreferences p = prefs(ctx);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY_LOG, "[]"));
            JSONObject entry = new JSONObject();
            entry.put("time", System.currentTimeMillis());
            entry.put("distance", distanceKm);
            entry.put("accepted", accepted);
            arr.put(entry);

            long cutoff = System.currentTimeMillis() - LOG_WINDOW_MILLIS;
            JSONArray pruned = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getLong("time") >= cutoff) {
                    pruned.put(o);
                }
            }

            p.edit().putString(KEY_LOG, pruned.toString()).apply();
        } catch (Exception e) {
            // تجاهل أي خطأ بالتحليل حتى لا تتوقف الخدمة بسببه
        }
    }

    /** يرجع سجلات آخر 5 ساعات، الأحدث أولًا */
    public static List<JSONObject> getLogEntries(Context ctx) {
        List<JSONObject> result = new ArrayList<>();
        SharedPreferences p = prefs(ctx);
        try {
            JSONArray arr = new JSONArray(p.getString(KEY_LOG, "[]"));
            long cutoff = System.currentTimeMillis() - LOG_WINDOW_MILLIS;
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getLong("time") >= cutoff) {
                    result.add(o);
                }
            }
        } catch (Exception e) {
            // تجاهل
        }
        return result;
    }
}