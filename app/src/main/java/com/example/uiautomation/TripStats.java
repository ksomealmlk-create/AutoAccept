package com.example.uiautomation;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TripStats {

    private static final String FILE = "trip_stats";
    private static final String KEY = "trips";
    private static final long KEEP_MS = 60L * 24 * 60 * 60 * 1000;
    private static final long PENDING_MAX_MS = 3L * 60 * 60 * 1000;

    public static class Summary {
        public int accepted, rejected, completed, lost;
        public int saver, multi, normal;
        public long totalPrice;
        public long totalDurationMs;
        public long firstAcceptAt, lastEndAt;
        public long latSum, latCount;
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static JSONArray load(Context c) {
        try {
            return new JSONArray(sp(c).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(Context c, JSONArray arr) {
        try {
            long limit = System.currentTimeMillis() - KEEP_MS;
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("t") >= limit) out.put(o);
            }
            sp(c).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized long recordAccepted(
            Context c, double distance, String cat,
            String hood, double maxKm, long latMs, long scanMs, String ph) {
        try {
            JSONArray arr = load(c);
            JSONObject o = new JSONObject();
            long now = System.currentTimeMillis();
            o.put("t", now);
            o.put("status", "accepted");
            o.put("acceptedAt", now);
            o.put("distance", distance);
            o.put("cat", cat);
            o.put("hood", hood == null ? "" : hood);
            o.put("reason", "OK");
            o.put("maxKm", maxKm);
            o.put("latMs", latMs);
            o.put("scanMs", scanMs);
            o.put("ph", ph == null ? "" : ph);
            arr.put(o);
            save(c, arr);
            return o.optLong("t");
        } catch (Exception ignored) {
        }
        return 0L;
    }

    /** reason: OUT_RANGE أو NO_DIST أو HOOD */
    public static synchronized long recordRejected(
            Context c, double distance, String cat,
            String hood, String reason, double maxKm,
            long latMs, long scanMs, String ph) {
        try {
            JSONArray arr = load(c);
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("status", "rejected");
            o.put("distance", distance);
            o.put("cat", cat);
            o.put("hood", hood == null ? "" : hood);
            o.put("reason", reason);
            o.put("maxKm", maxKm);
            o.put("latMs", latMs);
            o.put("scanMs", scanMs);
            o.put("ph", ph == null ? "" : ph);
            arr.put(o);
            save(c, arr);
            return o.optLong("t");
        } catch (Exception ignored) {
        }
        return 0L;
    }

    /** Uber أمريكا: status = accepted أو rejected. reason: OK / OUT_KM / OUT_MIN / NO_KM / NO_MIN */
    public static synchronized long recordUs(
            Context c, String status, double km, int minutes,
            String pickup, String dest, String reason,
            double maxKm, int maxMin, long latMs, long scanMs, String ph) {
        try {
            JSONArray arr = load(c);
            JSONObject o = new JSONObject();
            long now = System.currentTimeMillis();
            o.put("t", now);
            o.put("app", "us");
            o.put("status", status);
            if ("accepted".equals(status)) o.put("acceptedAt", now);
            o.put("distance", km);
            o.put("minutes", minutes);
            o.put("cat", "NORMAL");
            o.put("hood", pickup == null ? "" : pickup);
            o.put("dest", dest == null ? "" : dest);
            o.put("reason", reason);
            o.put("maxKm", maxKm);
            o.put("maxMin", maxMin);
            o.put("latMs", latMs);
            o.put("scanMs", scanMs);
            o.put("ph", ph == null ? "" : ph);
            arr.put(o);
            save(c, arr);
            return o.optLong("t");
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static int findPending(JSONArray arr, long maxAge) {
        long now = System.currentTimeMillis();
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && "accepted".equals(o.optString("status"))) {
                return (now - o.optLong("acceptedAt") <= maxAge) ? i : -1;
            }
        }
        return -1;
    }

    public static synchronized boolean hasPending(Context c) {
        return findPending(load(c), PENDING_MAX_MS) >= 0;
    }

    public static synchronized void markLostPending(Context c) {
        try {
            JSONArray arr = load(c);
            int i = findPending(arr, 60000L);
            if (i >= 0) {
                arr.getJSONObject(i).put("status", "lost");
                save(c, arr);
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void recordEnded(
            Context c, long price, long tripMs) {
        try {
            JSONArray arr = load(c);
            int i = findPending(arr, PENDING_MAX_MS);
            if (i >= 0) {
                JSONObject o = arr.getJSONObject(i);
                o.put("status", "completed");
                o.put("endedAt", System.currentTimeMillis());
                o.put("price", price);
                o.put("tripMs", tripMs);
                save(c, arr);
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized Summary getToday(Context c) {
        return getToday(c, "iraq");
    }

    public static synchronized Summary getToday(Context c, String app) {
        Summary s = new Summary();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();

        JSONArray arr = load(c);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optLong("t") < start) continue;
            if (!app.equals(o.optString("app", "iraq"))) continue;

            String st = o.optString("status");
            String cat = o.optString("cat");

            long lm = o.optLong("latMs", -1);
            if (lm >= 0) {
                s.latSum += lm;
                s.latCount++;
            }

            if ("rejected".equals(st)) {
                s.rejected++;
                continue;
            }
            if ("lost".equals(st)) {
                s.lost++;
                continue;
            }

            s.accepted++;
            if ("SAVER".equals(cat)) s.saver++;
            else if ("MULTI".equals(cat)) s.multi++;
            else s.normal++;

            long a = o.optLong("acceptedAt");
            if (s.firstAcceptAt == 0 || a < s.firstAcceptAt) s.firstAcceptAt = a;

            if ("completed".equals(st)) {
                s.completed++;
                long e = o.optLong("endedAt");
                s.totalPrice += o.optLong("price");
                long tm = o.optLong("tripMs");
                s.totalDurationMs += tm > 0 ? tm : Math.max(0, e - a);
                if (e > s.lastEndAt) s.lastEndAt = e;
            }
        }
        return s;
    }

    /** كل رحلات اليوم، الأحدث أولاً */
    public static synchronized List<JSONObject> getTodayEntries(Context c) {
        return getTodayEntries(c, "iraq");
    }

    public static synchronized List<JSONObject> getTodayEntries(Context c, String app) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();

        List<JSONObject> out = new ArrayList<>();
        JSONArray arr = load(c);
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optLong("t") >= start
                    && app.equals(o.optString("app", "iraq"))) out.add(o);
        }
        return out;
    }

    /** كل السجلات (العراق وأمريكا)، الأحدث أولاً */
    public static synchronized List<JSONObject> getAllEntries(Context c) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray arr = load(c);
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }
}
