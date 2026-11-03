package com.example.uiautomation;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {
    private ThemeManager() {}

    public static void apply(Context context) {
        int mode = Prefs.getThemeMode(context);
        if (mode == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mode == 2) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
