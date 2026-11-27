package com.example.uiautomation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LockActivity extends AppCompatActivity {

    // ===== غيّر هذا الرقم إلى رمز الدخول الذي تريده =====
    private static final String APP_PIN = "20021124";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);

        // إذا سبق وأدخل المستخدم الرمز الصحيح من قبل، تخطَّ هذه الشاشة نهائيًا
        if (Prefs.isUnlocked(this)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_lock);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPin = findViewById(R.id.etPin);
        Button btnUnlock = findViewById(R.id.btnUnlock);

        btnUnlock.setOnClickListener(v -> {
            String name = etUsername.getText().toString().trim();
            String enteredPin = etPin.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "يرجى كتابة اسمك", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!enteredPin.equals(APP_PIN)) {
                Toast.makeText(this, "رمز غير صحيح", Toast.LENGTH_SHORT).show();
                etPin.setText("");
                return;
            }

            Prefs.setUsername(this, name);
            Prefs.setUnlocked(this);
            goToMain();
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}