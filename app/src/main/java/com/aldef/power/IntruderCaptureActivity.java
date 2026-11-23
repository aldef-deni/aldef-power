package com.aldef.power;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * Activity transparan di atas layar kunci untuk memotret penyusup.
 * Android 11 hanya mengizinkan kamera dipakai dari aplikasi yang sedang tampil,
 * jadi activity ini muncul sesaat (tak terlihat) lalu langsung menutup.
 */
public class IntruderCaptureActivity extends Activity {

    private static final String EXTRA_REASON = "reason";
    private static long lastShot;

    static void launch(Context c, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastShot < 3000) return;   // cegah dobel saat beberapa kali salah beruntun
        lastShot = now;
        c.startActivity(new Intent(c, IntruderCaptureActivity.class)
                .putExtra(EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        new IntruderCamera(this, reason == null ? IntruderStore.REASON_LOCK : reason, () -> {
            finish();
            overridePendingTransition(0, 0);
        }).capture();
    }
}
