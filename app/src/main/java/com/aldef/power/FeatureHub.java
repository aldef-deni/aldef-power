package com.aldef.power;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.widget.Toast;

/**
 * Mesin Fitur Ekstra yang hidup di dalam layanan aksesibilitas ALDEF:
 * ketuk punggung, bola power melayang, dan pemicu animasi cas.
 */
final class FeatureHub implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final AccessibilityService svc;
    private final FloatingBall ball;
    private BackTapDetector tap;
    private boolean screenOn;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                screenOn = false;
                applyBackTap();
            } else if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_USER_PRESENT.equals(a)) {
                screenOn = true;
                applyBackTap();
            } else if (Intent.ACTION_POWER_CONNECTED.equals(a) && Features.charging(c)) {
                ChargingActivity.show(c);
            }
        }
    };

    FeatureHub(AccessibilityService svc) {
        this.svc = svc;
        this.ball = new FloatingBall(svc);
    }

    void start() {
        PowerManager pm = (PowerManager) svc.getSystemService(Context.POWER_SERVICE);
        screenOn = pm == null || pm.isInteractive();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        svc.registerReceiver(receiver, f);
        Features.prefs(svc).registerOnSharedPreferenceChangeListener(this);
        applyBackTap();
        applyBall();
    }

    void stop() {
        Features.prefs(svc).unregisterOnSharedPreferenceChangeListener(this);
        try {
            svc.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (tap != null) tap.stop();
        tap = null;
        ball.hide();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Features.KEY_BACK_TAP.equals(key) || Features.KEY_BACK_TAP_SENS.equals(key)) applyBackTap();
        if (Features.KEY_BALL.equals(key)) applyBall();
    }

    private void applyBackTap() {
        boolean want = Features.backTap(svc) && screenOn;
        if (want && tap == null) {
            tap = new BackTapDetector(svc, Features.backTapSensitivity(svc), this::onTaps);
            if (!tap.start()) tap = null;
        } else if (!want && tap != null) {
            tap.stop();
            tap = null;
        } else if (tap != null) {
            tap.setSensitivity(Features.backTapSensitivity(svc));
        }
    }

    private void applyBall() {
        if (Features.ball(svc)) ball.show();
        else ball.hide();
    }

    private void onTaps(int count) {
        if (BackTapDetector.testing) return;
        switch (count) {
            case 2:
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                break;
            case 3:
                svc.startActivity(new Intent(svc, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                break;
            case 4:
                Intent li = svc.getPackageManager().getLaunchIntentForPackage(Features.ALDEF_SYSTEM_PKG);
                if (li != null) {
                    svc.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    Toast.makeText(svc, "Aldef System tidak terpasang", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }
}
