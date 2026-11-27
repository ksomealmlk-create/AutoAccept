package com.example.uiautomation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private Button btnOverlayPermission;
    private Button btnAccessibilityPermission;
    private Button btnStartFloating;
    private Button btnSettings;
    private Button btnStats;
    private TextView tvStatus;
    private TextView tvWelcome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnOverlayPermission = findViewById(R.id.btnOverlayPermission);
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission);
        btnStartFloating = findViewById(R.id.btnStartFloating);
        btnSettings = findViewById(R.id.btnSettings);
        btnStats = findViewById(R.id.btnStats);
        tvStatus = findViewById(R.id.tvStatus);
        tvWelcome = findViewById(R.id.tvWelcome);

        String username = Prefs.getUsername(this);
        tvWelcome.setText("الصگار: " + username);

        btnOverlayPermission.setOnClickListener(v -> requestOverlayPermission());
        btnAccessibilityPermission.setOnClickListener(v -> openAccessibilitySettings());
        btnStartFloating.setOnClickListener(v -> startFloatingService());

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsChooserActivity.class)));

        btnStats.setOnClickListener(v ->
                startActivity(new Intent(this, StatsChooserActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusText();
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "صلاحية النافذة العائمة ممنوحة بالفعل", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this,
                "فعّل \"صكار الطلبات\" من قائمة خدمات إمكانية الوصول",
                Toast.LENGTH_LONG).show();
    }

    private void startFloatingService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "يرجى منح صلاحية النافذة العائمة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingService.class);
        serviceIntent.setAction("SHOW_FLOATING");
        startService(serviceIntent);
        Toast.makeText(this, "تم تشغيل الزر العائم", Toast.LENGTH_SHORT).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + MyAccessibilityService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (TextUtils.isEmpty(enabledServices)) return false;

        for (String s : enabledServices.split(":")) {
            if (s.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }

    private void updateStatusText() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean accessibility = isAccessibilityServiceEnabled();
        tvStatus.setText("صلاحية النافذة العائمة: " + (overlay ? "ممنوحة ✔" : "غير ممنوحة ✘")
                + "\nخدمة Accessibility: " + (accessibility ? "مفعّلة ✔" : "غير مفعّلة ✘"));
    }
}