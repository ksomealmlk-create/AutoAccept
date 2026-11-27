package com.example.uiautomation;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private SeekBar sbDistance;
    private Switch swNoLimit;
    private Switch swAutoAccept, swAutoReject, swAutoConfirm, swStats, swShots, swNeighborhood, swSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sbDistance = findViewById(R.id.sbDistance);
        swNoLimit = findViewById(R.id.swNoLimit);
        swAutoAccept = findViewById(R.id.swAutoAccept);
        swAutoReject = findViewById(R.id.swAutoReject);
        swAutoConfirm = findViewById(R.id.swAutoConfirm);
        swStats = findViewById(R.id.swStats);
        swShots = findViewById(R.id.swShots);
        swNeighborhood = findViewById(R.id.swNeighborhood);
        swSound = findViewById(R.id.swSound);
        RadioGroup rgTheme = findViewById(R.id.rgTheme);
        Button btnSave = findViewById(R.id.btnSaveSettings);

        swAutoAccept.setChecked(Prefs.isAutoAcceptEnabled(this));
        swAutoReject.setChecked(Prefs.isAutoRejectEnabled(this));
        swAutoConfirm.setChecked(Prefs.isAutoConfirmDialogEnabled(this));
        swStats.setChecked(Prefs.isStatsEnabled(this));
        swShots.setChecked(Prefs.isShotsEnabled(this));
        swNeighborhood.setChecked(Prefs.isNeighborhoodFilterEnabled(this));
        swSound.setChecked(Prefs.isSoundOnAcceptEnabled(this));

        int themeMode = Prefs.getThemeMode(this);
        if (themeMode == 1) rgTheme.check(R.id.rbThemeLight);
        else if (themeMode == 2) rgTheme.check(R.id.rbThemeDark);
        else rgTheme.check(R.id.rbThemeSystem);

        // تُحفظ مفاتيح التشغيل/الإيقاف فورًا عند تغييرها.
        swAutoAccept.setOnCheckedChangeListener((button, checked) -> Prefs.setAutoAcceptEnabled(this, checked));
        swAutoReject.setOnCheckedChangeListener((button, checked) -> Prefs.setAutoRejectEnabled(this, checked));
        swAutoConfirm.setOnCheckedChangeListener((button, checked) -> Prefs.setAutoConfirmDialogEnabled(this, checked));
        swStats.setOnCheckedChangeListener((button, checked) -> Prefs.setStatsEnabled(this, checked));
        swShots.setOnCheckedChangeListener((button, checked) -> Prefs.setShotsEnabled(this, checked));
        swNeighborhood.setOnCheckedChangeListener((button, checked) -> Prefs.setNeighborhoodFilterEnabled(this, checked));
        swSound.setOnCheckedChangeListener((button, checked) -> Prefs.setSoundOnAcceptEnabled(this, checked));
        setupDistance();
        setupNeighborhoods();
        setupInfoButtons();

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId == R.id.rbThemeLight ? 1 : checkedId == R.id.rbThemeDark ? 2 : 0;
            if (Prefs.getThemeMode(this) != mode) {
                Prefs.setThemeMode(this, mode);
                ThemeManager.apply(this);
                recreate();
            }
        });

        btnSave.setOnClickListener(v -> {
            float selected = selectedKm();

            Prefs.setMaxDistanceKm(this, selected);
            Prefs.setAutoAcceptEnabled(this, swAutoAccept.isChecked());
            Prefs.setAutoRejectEnabled(this, swAutoReject.isChecked());
            Prefs.setAutoConfirmDialogEnabled(this, swAutoConfirm.isChecked());
            Prefs.setStatsEnabled(this, swStats.isChecked());
            Prefs.setShotsEnabled(this, swShots.isChecked());
            Prefs.setNeighborhoodFilterEnabled(this, swNeighborhood.isChecked());
            Prefs.setSoundOnAcceptEnabled(this, swSound.isChecked());
            int selectedTheme = rgTheme.getCheckedRadioButtonId() == R.id.rbThemeLight ? 1
                    : rgTheme.getCheckedRadioButtonId() == R.id.rbThemeDark ? 2 : 0;
            Prefs.setThemeMode(this, selectedTheme);
            Toast.makeText(this, "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    // ================= قسم المسافة (يفتح ويسد مثل الفلتر) =================

    private void toggle(View panel, TextView arrow) {
        boolean open = panel.getVisibility() == View.VISIBLE;
        panel.setVisibility(open ? View.GONE : View.VISIBLE);
        arrow.setText(open ? "▾" : "▴");
    }

    /* الشريط: خطوة 0.1 كم من 0.1 إلى 4.0 (القيمة = (progress+1) / 10) */
    private float selectedKm() {
        if (swNoLimit.isChecked()) return -1f;
        return Math.round((sbDistance.getProgress() + 1)) / 10f;
    }

    private String kmText(float km) {
        return String.format(java.util.Locale.US, "%.1f", km);
    }

    private void updateDistanceSummary() {
        TextView tv = findViewById(R.id.tvDistanceSummary);
        TextView big = findViewById(R.id.tvDistanceValue);
        float km = selectedKm();
        String s = km < 0 ? "بدون حد للمسافة" : "حتى " + kmText(km) + " كم";
        tv.setText(s);
        big.setText(s);
        sbDistance.setEnabled(km >= 0);
        sbDistance.setAlpha(km >= 0 ? 1f : 0.4f);
    }

    private void setupDistance() {
        View header = findViewById(R.id.distHeader);
        View panel = findViewById(R.id.distPanel);
        TextView arrow = findViewById(R.id.distArrow);

        float current = Prefs.getMaxDistanceKm(this);
        if (current < 0) {
            swNoLimit.setChecked(true);
            sbDistance.setProgress(29);
        } else {
            int p = Math.round(current * 10f) - 1;
            sbDistance.setProgress(Math.max(0, Math.min(39, p)));
        }
        updateDistanceSummary();

        // تُحفظ القيمة فورًا عند تحريك الشريط أو المفتاح.
        sbDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Prefs.setMaxDistanceKm(SettingsActivity.this, selectedKm());
                updateDistanceSummary();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        swNoLimit.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setMaxDistanceKm(this, selectedKm());
            updateDistanceSummary();
        });

        header.setOnClickListener(v -> toggle(panel, arrow));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String d = Prefs.getIqDiag(this);
        ((TextView) findViewById(R.id.tvIqDiag)).setText(
                d.isEmpty() ? "لا يوجد قرار بعد" : d);
    }

    // ================= قسم الأحياء =================

    private final List<CheckBox> neighBoxes = new ArrayList<>();

    private void setupNeighborhoods() {
        View header = findViewById(R.id.neighHeader);
        View panel = findViewById(R.id.neighPanel);
        TextView arrow = findViewById(R.id.neighArrow);
        LinearLayout container = findViewById(R.id.neighContainer);
        Button all = findViewById(R.id.btnSelectAll);
        Button none = findViewById(R.id.btnClearAll);

        Set<String> selected = Prefs.getSelectedNeighborhoods(this);
        int h = (int) (46 * getResources().getDisplayMetrics().density);

        for (String name : NeighborhoodFilter.ALL) {
            CheckBox cb = new CheckBox(this);
            cb.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, h));
            cb.setText(name);
            cb.setTextSize(14);
            cb.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            cb.setButtonTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_teal)));
            cb.setTag(name);
            cb.setChecked(selected.contains(name));
            cb.setOnCheckedChangeListener((btn, checked) -> saveNeighborhoods());
            neighBoxes.add(cb);
            container.addView(cb);
        }

        all.setOnClickListener(v -> { for (CheckBox cb : neighBoxes) cb.setChecked(true); });
        none.setOnClickListener(v -> { for (CheckBox cb : neighBoxes) cb.setChecked(false); });
        header.setOnClickListener(v -> toggle(panel, arrow));
    }

    private void saveNeighborhoods() {
        Set<String> s = new HashSet<>();
        for (CheckBox cb : neighBoxes) {
            if (cb.isChecked()) s.add((String) cb.getTag());
        }
        Prefs.setSelectedNeighborhoods(this, s);
    }

    // ================= أزرار الشرح (i) =================

    private void bindInfo(int viewId, String title, String message) {
        findViewById(viewId).setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("تمام", null)
                    .create();
            dialog.show();
            dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        });
    }

    private void setupInfoButtons() {
        bindInfo(R.id.infoAccept, "قبول الرحلات ضمن المسافة",
                "إذا شغّلته، التطبيق يسحب المقبض الأصفر نحو ✓ تلقائيًا على أي طلب مسافته ضمن الحد اللي محدده (وحيّه مسموح إذا فلتر الأحياء شغّال).\n\nإذا طفيته، التطبيق ما يقبل أي طلب وتقبل بنفسك.");

        bindInfo(R.id.infoReject, "تجاهل الرحلات خارج المسافة",
                "يسحب المقبض نحو ✗ تلقائيًا على الطلبات البعيدة، أو اللي حيّها مو من الأحياء المختارة، حتى تختفي وتجيك طلبات ثانية.\n\nإذا طفيته، تبقى الطلبات البعيدة ظاهرة وتقرر بنفسك.");

        bindInfo(R.id.infoConfirm, "إغلاق رسالة موافق",
                "أحيانًا تطلع رسالة «قبل سائق آخر هذا الحجز» وبيها زر «موافق».\n\nهذا الخيار يضغط على «موافق» تلقائيًا حتى ترجع لاستقبال طلبات جديدة بدون ما تلمس الشاشة.");

        bindInfo(R.id.infoStats, "تسجيل الإحصائيات",
                "يحفظ عدد الطلبات اللي قبلها وتجاهلها التطبيق ومسافة كل طلب، وتشوفها بشاشة الإحصائيات (عدّاد اليوم وسجل آخر 5 ساعات).\n\nإذا طفيته، ما ينحفظ أي شي.");

        bindInfo(R.id.infoDistance, "أقصى مسافة للطلب",
                "المسافة اللي تبعدها عنك نقطة الزبون (الرقم تحت «إلى الانطلاق»). مثلًا «حتى 2 كم» يعني يقبل الطلب إذا الزبون يبعد 2 كم أو أقل، وغير هيج يتجاهله.\n\n«بدون حد للمسافة» يعني ما يهتم بالمسافة أبدًا.\n\nإذا ما انقرأت المسافة من الشاشة، ما يقبل ولا يرفض الطلب.");

        bindInfo(R.id.infoNeigh, "فلتر أحياء النجف",
                "يقبل الطلب فقط إذا الحي اللي بي الزبون (مكان الانطلاق، مو الوجهة) من الأحياء اللي مختارها، إضافةً لشرط المسافة.\n\nإذا الحي مو من قائمتك أو ما انقرأ، يتبع المسافة فقط.\n\nإذا طفيت المفتاح، الفلتر ما يفحص الأحياء نهائيًا.");

        bindInfo(R.id.infoTheme, "مظهر البرنامج",
                "النظام التلقائي: يتبع وضع الجهاز.\nنهاري: خلفية فاتحة.\nليلي: خلفية داكنة.");
    }
}