package com.aldef.power;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

/**
 * Mesin fitur yang hidup di dalam layanan aksesibilitas ALDEF:
 * Flying Ball Shortcut dan pemicu Animate Charging.
 */
final class FeatureHub implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final AccessibilityService svc;
    private final FloatingBall ball;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_POWER_CONNECTED.equals(i.getAction()) && Features.charging(c)) {
                ChargingActivity.show(c);
            }
        }
    };

    FeatureHub(AccessibilityService svc) {
        this.svc = svc;
        this.ball = new FloatingBall(svc);
    }

    void start() {
        svc.registerReceiver(receiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        Features.prefs(svc).registerOnSharedPreferenceChangeListener(this);
        applyBall();
    }

    void stop() {
        Features.prefs(svc).unregisterOnSharedPreferenceChangeListener(this);
        try {
            svc.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        ball.hide();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Features.KEY_BALL.equals(key)) applyBall();
    }

    private void applyBall() {
        if (Features.ball(svc)) ball.show();
        else ball.hide();
    }
}
