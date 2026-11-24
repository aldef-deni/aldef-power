package com.aldef.power;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {

    private View statusDot;
    private TextView tvStatusTitle, tvStatusSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusDot = findViewById(R.id.statusDot);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusSub = findViewById(R.id.tvStatusSub);

        findViewById(R.id.btnRestart).setOnClickListener(v ->
                RebootHelper.confirmAndRestart(this, this::refreshStatus));
        findViewById(R.id.btnLock).setOnClickListener(v -> {
            if (!RebootHelper.lockScreen(this)) RebootHelper.needAccessibility(this);
        });
        findViewById(R.id.btnAccessibility).setOnClickListener(v ->
                RebootHelper.openAccessibilitySettings(this));
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> openAppInfo());

        // Fitur rahasia: ketuk 5x
        findViewById(R.id.tvFooter).setOnClickListener(secretTap(() -> openVault(VaultActivity.MODE_MANAGE)));
        findViewById(R.id.ivLogo).setOnClickListener(secretTap(() -> PinActivity.openGate(this)));
        findViewById(R.id.btnFeatures).setOnClickListener(v ->
                startActivity(new Intent(this, FeaturesActivity.class)));
        findViewById(R.id.btnRemote).setOnClickListener(v ->
                startActivity(new Intent(this, RemoteActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        new Thread(() -> AppHider.rehideTemporary(this)).start();
    }

    private void openVault(int mode) {
        startActivity(new Intent(this, VaultActivity.class).putExtra(VaultActivity.EXTRA_MODE, mode));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** Listener yang menjalankan aksi setelah diketuk 5x berturut-turut (jeda maks 700 ms). */
    private static View.OnClickListener secretTap(Runnable action) {
        return new View.OnClickListener() {
            private int count;
            private long last;

            @Override
            public void onClick(View v) {
                long now = SystemClock.uptimeMillis();
                if (now - last > 700) count = 0;
                last = now;
                if (++count >= 5) {
                    count = 0;
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    action.run();
                }
            }
        };
    }

    private void refreshStatus() {
        boolean active = RebootHelper.bestMethod(this) != RebootHelper.Method.NONE;
        int color = getColor(active ? R.color.ok : R.color.danger);
        statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        tvStatusTitle.setText(active ? "Layanan aktif" : "Layanan belum aktif");
        tvStatusSub.setText(active ? "Siap digunakan" : "Aktifkan Aksesibilitas di bawah ini");
    }

    private void openAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }
}
