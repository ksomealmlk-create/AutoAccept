package com.example.uiautomation;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** إعدادات Uber أمريكا: تشغيل/إيقاف، أقصى مسافة، أقصى وقت وصول */
public class UsSettingsActivity extends AppCompatActivity {

    private Switch swEnabled, swAccept, swReject, swStats;
    private RadioGroup rgDistance, rgMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_us_settings);

        swEnabled = findViewById(R.id.swUsEnabled);
        swAccept = findViewById(R.id.swUsAccept);
        swReject = findViewById(R.id.swUsReject);
        swStats = findViewById(R.id.swUsStats);
        rgDistance = findViewById(R.id.rgUsDistance);
        rgMinutes = findViewById(R.id.rgUsMinutes);
        Button btnSave = findViewById(R.id.btnSaveUs);

        // ---- القيم الحالية ----
        swEnabled.setChecked(Prefs.isUsEnabled(this));
        swAccept.setChecked(Prefs.isUsAutoAcceptEnabled(this));
        swReject.setChecked(Prefs.isUsAutoRejectEnabled(this));
        swStats.setChecked(Prefs.isUsStatsEnabled(this));

        float km = Prefs.getUsMaxDistanceKm(this);
        if (km < 0) rgDistance.check(R.id.rbUsAny);
        else if (km <= 1.0f) rgDistance.check(R.id.rbUs1km);
        else if (km <= 2.0f) rgDistance.check(R.id.rbUs2km);
        else if (km <= 2.5f) rgDistance.check(R.id.rbUs25km);
        else if (km <= 2.75f) rgDistance.check(R.id.rbUs275km);
        else rgDistance.check(R.id.rbUs3km);

        int min = Prefs.getUsMaxMinutes(this);
        if (min < 0) rgMinutes.check(R.id.rbUsMinAny);
        else if (min <= 1) rgMinutes.check(R.id.rbUsMin1);
        else if (min == 2) rgMinutes.check(R.id.rbUsMin2);
        else if (min == 3) rgMinutes.check(R.id.rbUsMin3);
        else if (min == 4) rgMinutes.check(R.id.rbUsMin4);
        else if (min == 5) rgMinutes.check(R.id.rbUsMin5);
        else rgMinutes.check(R.id.rbUsMin6);

        updateEnabledSummary();
        updateOptionSummaries();
        updateDistanceSummary();
        updateTimeSummary();

        // ---- تنحفظ فورًا عند أي تغيير ----
        swEnabled.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setUsEnabled(this, checked);
            updateEnabledSummary();
        });

        swAccept.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setUsAutoAcceptEnabled(this, checked);
            updateOptionSummaries();
        });

        swReject.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setUsAutoRejectEnabled(this, checked);
            updateOptionSummaries();
        });

        swStats.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setUsStatsEnabled(this, checked);
            updateOptionSummaries();
        });

        rgDistance.setOnCheckedChangeListener((g, id) -> {
            Prefs.setUsMaxDistanceKm(this, selectedKm());
            updateDistanceSummary();
        });

        rgMinutes.setOnCheckedChangeListener((g, id) -> {
            Prefs.setUsMaxMinutes(this, selectedMinutes());
            updateTimeSummary();
        });

        // ---- فتح وسد الأقسام ----
        setupToggle(R.id.usDistHeader, R.id.usDistPanel, R.id.usDistArrow);
        setupToggle(R.id.usTimeHeader, R.id.usTimePanel, R.id.usTimeArrow);

        btnSave.setOnClickListener(v -> {
            Prefs.setUsEnabled(this, swEnabled.isChecked());
            Prefs.setUsAutoAcceptEnabled(this, swAccept.isChecked());
            Prefs.setUsAutoRejectEnabled(this, swReject.isChecked());
            Prefs.setUsStatsEnabled(this, swStats.isChecked());
            Prefs.setUsMaxDistanceKm(this, selectedKm());
            Prefs.setUsMaxMinutes(this, selectedMinutes());
            Toast.makeText(this, "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String d = Prefs.getUsDiag(this);
        ((TextView) findViewById(R.id.tvUsDiag)).setText(
                d.isEmpty() ? "ما صار أي فحص لطلب أمريكا لحد الآن" : d);
    }

    private float selectedKm() {
        int id = rgDistance.getCheckedRadioButtonId();
        if (id == R.id.rbUs1km) return 1.0f;
        if (id == R.id.rbUs2km) return 2.0f;
        if (id == R.id.rbUs25km) return 2.5f;
        if (id == R.id.rbUs275km) return 2.75f;
        if (id == R.id.rbUs3km) return 3.0f;
        return -1f;
    }

    private int selectedMinutes() {
        int id = rgMinutes.getCheckedRadioButtonId();
        if (id == R.id.rbUsMin1) return 1;
        if (id == R.id.rbUsMin2) return 2;
        if (id == R.id.rbUsMin3) return 3;
        if (id == R.id.rbUsMin4) return 4;
        if (id == R.id.rbUsMin5) return 5;
        if (id == R.id.rbUsMin6) return 6;
        return -1;
    }

    private void updateOptionSummaries() {
        ((TextView) findViewById(R.id.tvUsAcceptSummary))
                .setText(swAccept.isChecked() ? "شغّال" : "مطفي");
        ((TextView) findViewById(R.id.tvUsRejectSummary))
                .setText(swReject.isChecked() ? "شغّال" : "مطفي");
        ((TextView) findViewById(R.id.tvUsStatsSummary))
                .setText(swStats.isChecked() ? "شغّال" : "مطفي");
    }

    private void updateEnabledSummary() {
        ((TextView) findViewById(R.id.tvUsEnabledSummary))
                .setText(swEnabled.isChecked() ? "شغّالة" : "مطفية");
    }

    private void updateDistanceSummary() {
        float km = selectedKm();
        String s;
        if (km < 0) s = "بدون حد للمسافة";
        else if (km == 2.5f) s = "حتى 2.5 كم";
        else if (km == 2.75f) s = "حتى 2.75 كم";
        else s = "حتى " + (int) km + " كم";
        ((TextView) findViewById(R.id.tvUsDistanceSummary)).setText(s);
    }

    private void updateTimeSummary() {
        int m = selectedMinutes();
        String s = m < 0 ? "بلا تحديد للوقت" : "حتى " + m + "د";
        ((TextView) findViewById(R.id.tvUsTimeSummary)).setText(s);
    }

    private void setupToggle(int headerId, int panelId, int arrowId) {
        View header = findViewById(headerId);
        View panel = findViewById(panelId);
        TextView arrow = findViewById(arrowId);
        header.setOnClickListener(v -> {
            boolean open = panel.getVisibility() == View.VISIBLE;
            panel.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.setText(open ? "▾" : "▴");
        });
    }
}
