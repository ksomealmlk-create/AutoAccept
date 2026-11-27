package com.example.uiautomation;

import android.app.Dialog;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** قائمة لقطات الرحلات: صورة شاشة لكل رحلة مع تفاصيلها، اضغط على الصورة تكبر */
public class ShotsActivity extends AppCompatActivity {

    private static class Item {
        File file;
        String title, status, line1, line2;
        int color;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shots);

        TextView tvEmpty = findViewById(R.id.tvShotsEmpty);
        ListView lv = findViewById(R.id.lvShots);

        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM   hh:mm a", Locale.US);
        List<Item> items = new ArrayList<>();

        for (JSONObject o : TripStats.getAllEntries(this)) {
            try {
                long t = o.optLong("t");
                File f = ShotHelper.fileFor(this, t);
                if (!f.exists()) continue;

                boolean us = "us".equals(o.optString("app", "iraq"));
                String st = o.optString("status");
                double dist = o.optDouble("distance", -1);
                String hood = o.optString("hood", "");

                Item it = new Item();
                it.file = f;
                it.title = (us ? "Uber أمريكا" : "Uber العراق")
                        + "   •   " + fmt.format(new Date(t));

                if ("rejected".equals(st)) {
                    it.status = us ? "❌ تجاهلتها (ضغطت X)" : "❌ تجاهلتها (لا شكرًا)";
                    it.color = 0xFFE74C3C;
                } else if ("lost".equals(st)) {
                    it.status = "⚠️ قبلتها بس أخذها كابتن ثاني";
                    it.color = 0xFFF1C40F;
                } else {
                    it.status = "✅ قبلتها";
                    it.color = 0xFF2ECC71;
                }

                it.line1 = (us ? "📍 " : "📍 الحي: ")
                        + (hood.isEmpty() ? "غير معروف" : hood);
                String distStr = dist < 0 ? "غير معروفة" : (dist + " كم");
                if (us) {
                    String dest = o.optString("dest", "");
                    it.line2 = "🏁 " + (dest.isEmpty() ? "غير معروفة" : dest)
                            + "   |   📏 " + distStr;
                } else {
                    it.line2 = "📏 المسافة: " + distStr;
                }
                items.add(it);
            } catch (Exception ignored) {
            }
        }

        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lv.setVisibility(View.GONE);
            return;
        }

        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(android.R.color.transparent);
        lv.setAdapter(new ShotsAdapter(items));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView line(String text, float sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextColor(color != 0 ? color
                : getResources().getColor(R.color.text_primary, getTheme()));
        tv.setPadding(0, dp(2), 0, dp(2));
        return tv;
    }

    private void showFull(File f) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setBackgroundColor(0xFF000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        d.show();
    }

    private class ShotsAdapter extends BaseAdapter {

        private final List<Item> items;

        ShotsAdapter(List<Item> items) {
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Item it = items.get(position);

            LinearLayout outer = new LinearLayout(ShotsActivity.this);
            outer.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            outer.setPadding(dp(4), dp(8), dp(4), dp(8));

            LinearLayout card = new LinearLayout(ShotsActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(0x1A808080);
            bg.setStroke(dp(1), it.color);
            card.setBackground(bg);

            ImageView iv = new ImageView(ShotsActivity.this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 2;
            iv.setImageBitmap(BitmapFactory.decodeFile(it.file.getAbsolutePath(), opt));
            iv.setOnClickListener(v -> showFull(it.file));
            card.addView(iv, new LinearLayout.LayoutParams(dp(84), dp(150)));

            LinearLayout col = new LinearLayout(ShotsActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.setMarginStart(dp(12));
            col.addView(line(it.title, 14, true, 0));
            col.addView(line(it.status, 14, true, it.color));
            col.addView(line(it.line1, 13, false, 0));
            col.addView(line(it.line2, 13, false, 0));
            card.addView(col, cp);

            card.setOnClickListener(v -> showFull(it.file));

            outer.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return outer;
        }
    }
}
