package com.example.uiautomation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

public class FloatingService extends Service {

    public static boolean isAutomationRunning = false;

    private WindowManager windowManager;
    private View floatingView;
    private ImageView floatingBtn;
    private WindowManager.LayoutParams params;
    private Animation pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isHidden = false;

    private static final String CHANNEL_ID = "floating_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification("صكار الطلبات يعمل"));
        createFloatingButton();
    }

    private void createFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = LayoutInflater.from(this)
                .inflate(R.layout.floating_button, null);

        floatingBtn = floatingView.findViewById(R.id.floating_btn);
        floatingBtn.setImageResource(R.mipmap.ic_launcher);

        // ✂️ قص اللوقو بشكل دائري
        floatingBtn.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        floatingBtn.setClipToOutline(true);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 250;

        pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
        updateButtonAppearance();

        // 👆 ضغطة قصيرة = تشغيل/إيقاف + اختفاء ثانيتين
        floatingBtn.setOnClickListener(v -> {
            isAutomationRunning = !isAutomationRunning;
            updateButtonAppearance();
            Toast.makeText(this,
                    isAutomationRunning ? "✅ تم تشغيل الأتمتة" : "⏸️ تم إيقاف الأتمتة",
                    Toast.LENGTH_SHORT).show();
            hideForTwoSeconds();
        });

        // السحب + الضغط المطول
        floatingBtn.setOnTouchListener(new DragTouchListener());

        windowManager.addView(floatingView, params);
    }

    // 🫥 اختفاء ثانيتين ثم عودة
    private void hideForTwoSeconds() {
        if (isHidden) return;
        isHidden = true;

        floatingView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    floatingView.setVisibility(View.GONE);

                    handler.postDelayed(() -> {
                        floatingView.setVisibility(View.VISIBLE);
                        floatingView.setAlpha(0f);
                        floatingView.animate().alpha(1f).setDuration(200);
                        updateButtonAppearance();
                        isHidden = false;
                    }, 2000);
                })
                .start();
    }

    private void updateButtonAppearance() {
        if (isAutomationRunning) {
            floatingBtn.setColorFilter(null);
            floatingBtn.startAnimation(pulse);
        } else {
            floatingBtn.clearAnimation();
            floatingBtn.setColorFilter(
                    Color.parseColor("#99000000"),
                    PorterDuff.Mode.MULTIPLY
            );
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "SHOW_FLOATING".equals(intent.getAction()) && floatingView != null) {
            floatingView.setVisibility(View.VISIBLE);
            floatingView.setAlpha(1f);
            floatingView.setScaleX(1f);
            floatingView.setScaleY(1f);
            isHidden = false;
            updateButtonAppearance();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            floatingView = null;
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("صكار الطلبات")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // السحب + الضغط المطول
    private class DragTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        private long downTime;
        private final Handler longPressHandler = new Handler(Looper.getMainLooper());
        private boolean isLongPress = false;
        private boolean isDragging = false;
        private boolean isClosng = false;

        // 🛑 الضغط المطول لثانيتين = إطفاء الأتمتة وإخفاء الزر فقط
        private final Runnable longPressRunnable = () -> {
            if (isClosng) return;
            isClosng = true;
            isLongPress = true;

            // لا نغلق التطبيق ولا نوقف خدمة الأتمتة. فقط نطفئ الأتمتة ونخفي الزر.
            isAutomationRunning = false;
            updateButtonAppearance();

            Toast.makeText(FloatingService.this,
                    "⏸️ تم إيقاف الأتمتة وإخفاء الزر", Toast.LENGTH_SHORT).show();

            floatingView.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        floatingView.setVisibility(View.GONE);
                        floatingView.setAlpha(1f);
                        floatingView.setScaleX(1f);
                        floatingView.setScaleY(1f);
                        isHidden = true;
                        isClosng = false;
                    }).start();
        };

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    if (isClosng) return true;
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    downTime = System.currentTimeMillis();
                    isLongPress = false;
                    isDragging = false;

                    // ⏱️ بدء عدّ ثانيتين
                    longPressHandler.postDelayed(longPressRunnable, 2000);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isClosng) return true;
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true;
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }

                    params.x = initialX + (int) dx;
                    params.y = initialY + (int) dy;
                    windowManager.updateViewLayout(floatingView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    if (isClosng) return true;
                    longPressHandler.removeCallbacks(longPressRunnable);

                    float moveX = event.getRawX() - initialTouchX;
                    float moveY = event.getRawY() - initialTouchY;
                    long pressDuration = System.currentTimeMillis() - downTime;

                    if (!isLongPress && !isDragging
                            && Math.abs(moveX) < 10
                            && Math.abs(moveY) < 10
                            && pressDuration < 500) {
                        v.performClick();
                    }
                    return true;
            }
            return false;
        }
    }
}