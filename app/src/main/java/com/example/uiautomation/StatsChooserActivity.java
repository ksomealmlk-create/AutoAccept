package com.example.uiautomation;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** شاشة اختيار الإحصائيات: Uber العراق أو Uber أمريكا */
public class StatsChooserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats_chooser);

        findViewById(R.id.cardStatsIraq).setOnClickListener(v ->
                startActivity(new Intent(this, StatsActivity.class)
                        .putExtra(StatsActivity.EXTRA_APP, "iraq")));

        findViewById(R.id.cardStatsUs).setOnClickListener(v ->
                startActivity(new Intent(this, StatsActivity.class)
                        .putExtra(StatsActivity.EXTRA_APP, "us")));

        findViewById(R.id.cardShots).setOnClickListener(v ->
                startActivity(new Intent(this, ShotsActivity.class)));
    }
}
