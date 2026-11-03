package com.example.uiautomation;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends AppCompatActivity {

    public static final String EXTRA_APP = "app";

    private boolean isUs = false;

    private static class Row {
        boolean isTrip;
        String text;
        int number;
        String time, status, hood, dist, why, dest, mins, lat, ph;
        int color;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        isUs = "us".equals(getIntent().getStringExtra(EXTRA_APP));
        final String app = isUs ? "us" : "iraq";
        ((TextView) findViewById(R.id.tvStatsTitle)).setText(
                isUs ? "إحصائية Uber أمريكا" : "إحصائية Uber العراق");

        TextView tvAcceptedCount = findViewById(R.id.tvAcceptedCount);
        TextView tvLostCount = findViewById(R.id.tvLostCount);
        TextView tvRejectedCount = findViewById(R.id.tvRejectedCount);
        ListView lvLog = findViewById(R.id.lvLog);

        TripStats.Summary s = TripStats.getToday(this, app);

        tvAcceptedCount.setText(String.valueOf(s.accepted));
        tvLostCount.setText(String.valueOf(s.lost));
        tvRejectedCount.setText(String.valueOf(s.rejected));

        SimpleDateFormat timeFormat =
                new SimpleDateFormat("hh:mm a", Locale.US);

        List<Row> rows = new ArrayList<>();

        long mins = s.totalDurationMs / 60000L;

        addText(rows, "📊 ملخص اليوم");
        addText(rows, "🚗 قبلتها: " + s.accepted
                + "   |   أنهيتها: " + s.completed
                + "   |   أخذها كابتن ثاني: " + s.lost
                + "   |   تجاهلتها: " + s.rejected);
        if (!isUs) {
        addText(rows, "🏷️ وفر: " + s.saver
                + "   |   متعدد الوجهات: " + s.multi
                + "   |   عادية: " + s.normal);
        addText(rows, "💰 مجموع الأرباح: "
                + NumberFormat.getInstance(Locale.US).format(s.totalPrice)
                + " د.ع");
        addText(rows, "⏱️ وقت العمل: " + (mins / 60) + " س و " + (mins % 60) + " د");
        }
        addText(rows, "⚡ متوسط سرعة الضغط: "
                + (s.latCount > 0 ? (s.latSum / s.latCount) + " مللي ثانية"
                : "—"));
        addText(rows, "🟢 أول رحلة قبلتها: "
                + (s.firstAcceptAt > 0
                ? timeFormat.format(new Date(s.firstAcceptAt)) : "—"));
        if (!isUs) {
        addText(rows, "🏁 آخر رحلة أنهيتها: "
                + (s.lastEndAt > 0
                ? timeFormat.format(new Date(s.lastEndAt)) : "—"));

        }

        List<JSONObject> entries = TripStats.getTodayEntries(this, app);

        if (entries.isEmpty()) {
            addText(rows, "لا توجد أي رحلات اليوم لحد الآن");
        } else {
            addText(rows, "📋 تفاصيل كل رحلة (" + entries.size() + ")");
        }

        int total = entries.size();
        for (int i = 0; i < total; i++) {
            try {
                // الأحدث فوق: أقدم رحلة = 1
                rows.add(buildTripRow(entries.get(i), timeFormat, total - i));
            } catch (Exception e) {
                // تجاهل أي سجل تالف
            }
        }

        lvLog.setDivider(null);
        lvLog.setDividerHeight(0);
        lvLog.setSelector(android.R.color.transparent);
        lvLog.setAdapter(new LogAdapter(rows));
    }

    private void addText(List<Row> rows, String text) {
        Row r = new Row();
        r.isTrip = false;
        r.text = text;
        rows.add(r);
    }

    private Row buildTripRow(JSONObject o, SimpleDateFormat timeFormat, int number) {

        String st = o.optString("status");
        double distance = o.optDouble("distance", -1);
        double maxKm = o.optDouble("maxKm", -1);
        String hood = o.optString("hood", "");
        String reason = o.optString("reason", "");

        String distStr = distance < 0 ? "غير معروفة" : (fmtKm(distance) + " كم");
        String hoodStr = hood.isEmpty() ? "غير معروف" : hood;
        String maxStr = maxKm < 0 ? "مفتوح" : (fmtKm(maxKm) + " كم");

        Row r = new Row();
        r.isTrip = true;
        r.number = number;
        r.time = timeFormat.format(new Date(o.optLong("t")));
        r.hood = hoodStr;
        r.dist = distStr;

        String phs = o.optString("ph", "");
        if (!phs.isEmpty()) {
            r.ph = phs;
        }

        long latMs = o.optLong("latMs", -1);
        long scanMs = o.optLong("scanMs", -1);
        if (latMs >= 0) {
            r.lat = latMs + " مللي ثانية"
                    + (scanMs >= 0 ? "  (الفحص والضغط: " + scanMs + ")" : "");
        }

        if (isUs) {
            String dest = o.optString("dest", "");
            r.dest = dest.isEmpty() ? "غير معروفة" : dest;
            int mn = o.optInt("minutes", -1);
            r.mins = mn < 0 ? "غير معروف" : (mn + " د");
        }

        if ("rejected".equals(st)) {
            r.status = "❌ تجاهلتها (لا شكرًا)";
            r.color = 0xFFE74C3C;
        } else if ("lost".equals(st)) {
            r.status = "⚠️ قبلتها بس أخذها كابتن ثاني";
            r.color = 0xFFF1C40F;
        } else {
            r.status = "✅ قبلتها ضمن المدى";
            r.color = 0xFF2ECC71;
        }

        if (isUs) {
            r.status = "rejected".equals(st)
                    ? "❌ تجاهلتها (ضغطت X)"
                    : ("lost".equals(st)
                    ? "⚠️ قبلتها بس أخذها كابتن ثاني"
                    : "✅ قبلتها ضمن المدى");
            r.why = buildUsReason(reason, distStr, maxStr, maxKm,
                    r.mins, o.optInt("maxMin", -1));
        } else {
            r.why = buildReason(reason, distStr, maxStr, maxKm, hoodStr);
        }
        return r;
    }

    private String buildUsReason(String reason, String distStr, String maxStr,
                                 double maxKm, String minsStr, int maxMin) {

        String maxMinStr = maxMin < 0 ? "بلا تحديد" : (maxMin + " د");

        switch (reason) {
            case "OK":
                return "السبب: المسافة " + distStr + " (" + maxStr
                        + ") ووقت الوصول " + minsStr + " (" + maxMinStr
                        + ") ضمن المدى";
            case "OUT_KM":
                return "السبب: المسافة " + distStr
                        + " أبعد من المدى المحدد (" + maxStr + ")";
            case "OUT_MIN":
                return "السبب: وقت الوصول " + minsStr
                        + " أكثر من المحدد (" + maxMinStr + ")";
            case "NO_KM":
                return "السبب: ما لقيت المسافة على بطاقة الطلب";
            case "NO_MIN":
                return "السبب: ما لقيت وقت الوصول على بطاقة الطلب";
            default:
                return "";
        }
    }

    private String buildReason(String reason, String distStr,
                               String maxStr, double maxKm, String hoodStr) {

        switch (reason) {
            case "OK":
                if (maxKm < 0) {
                    return "السبب: المدى مفتوح بدون حد";
                }
                return "السبب: المسافة " + distStr
                        + " ضمن المدى المحدد (" + maxStr + ")";
            case "OUT_RANGE":
                return "السبب: المسافة " + distStr
                        + " أبعد من المدى المحدد (" + maxStr + ")";
            case "NO_DIST":
                return "السبب: ما لقيت المسافة على شاشة الطلب";
            case "HOOD":
                return "السبب: الحي (" + hoodStr
                        + ") غير مختار بفلتر أحياء النجف";
            default:
                return "";
        }
    }

    private String fmtKm(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeLine(String text, float sizeSp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        tv.setTextColor(color != 0
                ? color
                : getResources().getColor(R.color.text_primary, getTheme()));
        tv.setPadding(0, dp(3), 0, dp(3));
        return tv;
    }

    private class LogAdapter extends BaseAdapter {

        private final List<Row> rows;

        LogAdapter(List<Row> rows) {
            this.rows = rows;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean isEnabled(int position) { return false; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row r = rows.get(position);

            LinearLayout outer = new LinearLayout(StatsActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (!r.isTrip) {
                outer.setPadding(dp(8), dp(4), dp(8), dp(4));
                outer.addView(makeLine(r.text, 15, false, 0));
                return outer;
            }

            // مسافة بين كل رحلة والثانية
            outer.setPadding(dp(4), dp(8), dp(4), dp(8));

            LinearLayout card = new LinearLayout(StatsActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(0x1A808080);
            bg.setStroke(dp(1), r.color);
            card.setBackground(bg);

            card.addView(makeLine("رحلة رقم " + r.number
                    + "   •   " + r.time, 16, true, 0));
            card.addView(makeLine(r.status, 15, true, r.color));
            if (isUs) {
                card.addView(makeLine("📍 مكان الزبون: " + r.hood, 14, false, 0));
                card.addView(makeLine("🏁 وجهة الزبون: " + r.dest, 14, false, 0));
                card.addView(makeLine("⏱ وقت الوصول: " + r.mins, 14, false, 0));
            } else {
                card.addView(makeLine("📍 الحي: " + r.hood, 14, false, 0));
            }
            card.addView(makeLine("📏 المسافة: " + r.dist, 14, false, 0));
            if (r.lat != null) {
                card.addView(makeLine("⚡ سرعة الضغط: " + r.lat, 14, false, 0));
            }
            if (r.ph != null) {
                TextView ph = makeLine("🔍 " + r.ph, 12, false, 0);
                ph.setAlpha(0.75f);
                card.addView(ph);
            }
            if (r.why != null && !r.why.isEmpty()) {
                TextView why = makeLine(r.why, 13, false, 0);
                why.setAlpha(0.75f);
                card.addView(why);
            }

            outer.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return outer;
        }
    }
}