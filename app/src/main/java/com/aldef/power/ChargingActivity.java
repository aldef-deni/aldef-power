package com.aldef.power;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import java.util.Locale;

/**
 * Layar animasi cas neon ALDEF: muncul saat charger dicolok (juga di atas layar kunci)
 * dan tetap tampil (layar tetap menyala) sampai diketuk. Otomatis tertutup jika charger dicabut.
 */
public class ChargingActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ChargingView view;

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction())) {
                close();
            } else {
                apply(i);
            }
        }
    };

    static void show(Context c) {
        c.startActivity(new Intent(c, ChargingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Tampil terus sampai diketuk, dan layar dijaga tetap menyala selama animasi tampil.
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new ChargingView(this);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        view.setOnClickListener(v -> close());
        view.setAlpha(0f);
        setContentView(view);
        view.animate().alpha(1f).setDuration(350).start();

        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        Intent sticky = registerReceiver(battery, f);
        if (sticky != null) apply(sticky);
    }

    private void apply(Intent i) {
        int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? Math.round(lvl * 100f / scale) : lvl;
        int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int state = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);

        String status;
        if (state == BatteryManager.BATTERY_STATUS_FULL || pct >= 100) {
            status = "Baterai penuh";
        } else {
            status = "Mengisi daya";
            if (Build.VERSION.SDK_INT >= 28) {
                long ms = ((BatteryManager) getSystemService(BATTERY_SERVICE)).computeChargeTimeRemaining();
                if (ms > 0) {
                    long m = ms / 60000;
                    status += " · penuh dalam " + (m >= 60 ? (m / 60) + " j " + (m % 60) + " m" : m + " m");
                }
            }
        }
        String source = plugged == BatteryManager.BATTERY_PLUGGED_AC ? "Charger"
                : plugged == BatteryManager.BATTERY_PLUGGED_USB ? "USB"
                : plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? "Nirkabel" : "";
        view.setBattery(pct, status,
                temp > 0 ? String.format(Locale.US, "%.1f°C", temp / 10f) : "",
                volt > 0 ? String.format(Locale.US, "%.2f V", volt / 1000f) : "",
                source);
    }

    private void close() {
        handler.removeCallbacksAndMessages(null);
        if (isFinishing()) return;
        view.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            finish();
            overridePendingTransition(0, 0);
        }).start();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(battery);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
