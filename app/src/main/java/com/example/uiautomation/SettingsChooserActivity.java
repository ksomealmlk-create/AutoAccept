package com.example.uiautomation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** شاشة اختيار الإعدادات: Uber العراق (الحالي) أو Uber أمريكا (الجديد) */
public class SettingsChooserActivity extends AppCompatActivity {

    private TextView tvUsStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_chooser);

        tvUsStatus = findViewById(R.id.tvUsStatus);

        findViewById(R.id.cardIraq).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.cardUs).setOnClickListener(v ->
                startActivity(new Intent(this, UsSettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvUsStatus.setText(Prefs.isUsEnabled(this)
                ? "الأتمتة: شغّالة"
                : "الأتمتة: مطفية");
    }
}
