package com.aldef.power;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Pengaturan Fitur Ekstra. */
public class FeaturesActivity extends Activity {

    private static final int REQ_CAMERA = 51;
    private static final int REQ_ADMIN = 52;

    private Switch swTap, swBall, swIntruder, swCharging;
    private View tapOptions, accWarning;
    private TextView chipLow, chipMed, chipHigh, tvTapTest, tvIntruderStatus;
    private BackTapDetector tester;
    private boolean binding;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_features);
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        swTap = findViewById(R.id.swTap);
        swBall = findViewById(R.id.swBall);
        swIntruder = findViewById(R.id.swIntruder);
        swCharging = findViewById(R.id.swCharging);
        tapOptions = findViewById(R.id.tapOptions);
        accWarning = findViewById(R.id.accWarning);
        chipLow = findViewById(R.id.chipLow);
        chipMed = findViewById(R.id.chipMed);
        chipHigh = findViewById(R.id.chipHigh);
        tvTapTest = findViewById(R.id.tvTapTest);
        tvIntruderStatus = findViewById(R.id.tvIntruderStatus);

        accWarning.setOnClickListener(v -> RebootHelper.openAccessibilitySettings(this));
        swTap.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            Features.set(this, Features.KEY_BACK_TAP, on);
            refresh();
        });
        swBall.setOnCheckedChangeListener((b, on) -> {
            if (!binding) Features.set(this, Features.KEY_BALL, on);
        });
        swCharging.setOnCheckedChangeListener((b, on) -> {
            if (!binding) Features.set(this, Features.KEY_CHARGING, on);
        });
        swIntruder.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            if (on) enableIntruder();
            else disableIntruder();
        });
        chipLow.setOnClickListener(v -> setSens(Features.SENS_LOW));
        chipMed.setOnClickListener(v -> setSens(Features.SENS_MEDIUM));
        chipHigh.setOnClickListener(v -> setSens(Features.SENS_HIGH));
        findViewById(R.id.btnPreviewCharging).setOnClickListener(v -> ChargingActivity.show(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refresh();
    }

    @Override
    protected void onPause() {
        resumed = false;
        stopTester();
        super.onPause();
    }

    private void refresh() {
        binding = true;
        swTap.setChecked(Features.backTap(this));
        swBall.setChecked(Features.ball(this));
        swCharging.setChecked(Features.charging(this));
        boolean intruderOn = Features.intruder(this) && intruderReady();
        if (Features.intruder(this) && !intruderOn) Features.set(this, Features.KEY_INTRUDER, false);
        swIntruder.setChecked(intruderOn);
        binding = false;

        accWarning.setVisibility(RebootHelper.isAccessibilityOn() ? View.GONE : View.VISIBLE);
        tapOptions.setVisibility(Features.backTap(this) ? View.VISIBLE : View.GONE);
        int s = Features.backTapSensitivity(this);
        styleChip(chipLow, s == Features.SENS_LOW);
        styleChip(chipMed, s == Features.SENS_MEDIUM);
        styleChip(chipHigh, s == Features.SENS_HIGH);
        tvIntruderStatus.setText(intruderOn
                ? "Aktif · " + IntruderStore.count(this) + " foto di Ruang Privat › Log Penyusup"
                : "Butuh izin kamera & admin perangkat (pantau PIN salah)");
        if (Features.backTap(this) && resumed) startTester();
        else stopTester();
    }

    // ------------------------------------------------------------ ketuk punggung

    private void setSens(int s) {
        Features.setInt(this, Features.KEY_BACK_TAP_SENS, s);
        if (tester != null) tester.setSensitivity(s);
        refresh();
    }

    private void startTester() {
        if (tester != null) return;
        BackTapDetector.testing = true;
        tester = new BackTapDetector(this, Features.backTapSensitivity(this), new BackTapDetector.Listener() {
            @Override
            public void onTap(int soFar) {
                tvTapTest.setText(soFar + "×");
                tvTapTest.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                tvTapTest.animate().cancel();
                tvTapTest.setScaleX(1.25f);
                tvTapTest.setScaleY(1.25f);
                tvTapTest.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            }

            @Override
            public void onTaps(int count) {
                String act = count == 2 ? "Kunci layar" : count == 3 ? "Buka ALDEF POWER"
                        : count == 4 ? "Buka Aldef System" : "Tidak ada aksi";
                tvTapTest.setText(count + "× · " + act);
            }
        });
        if (!tester.start()) {
            tvTapTest.setText("Sensor gerak tidak tersedia");
            tester = null;
        } else {
            tvTapTest.setText("Coba ketuk punggung HP…");
        }
    }

    private void stopTester() {
        if (tester != null) tester.stop();
        tester = null;
        BackTapDetector.testing = false;
    }

    // ------------------------------------------------------------ selfie penyusup

    private boolean intruderReady() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && RebootHelper.dpm(this).isAdminActive(RebootHelper.admin(this));
    }

    private void enableIntruder() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        if (!RebootHelper.dpm(this).isAdminActive(RebootHelper.admin(this))) {
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, RebootHelper.admin(this))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Dibutuhkan agar ALDEF POWER tahu saat PIN layar kunci salah, lalu memotret "
                                    + "penyusup dengan kamera depan.");
            startActivityForResult(i, REQ_ADMIN);
            return;
        }
        Features.set(this, Features.KEY_INTRUDER, true);
        Toast.makeText(this, "Selfie Penyusup aktif", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void disableIntruder() {
        Features.set(this, Features.KEY_INTRUDER, false);
        DevicePolicyManager dpm = RebootHelper.dpm(this);
        if (dpm.isAdminActive(RebootHelper.admin(this)) && !RebootHelper.isDeviceOwner(this)) {
            dpm.removeActiveAdmin(RebootHelper.admin(this));
        }
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                enableIntruder();
            } else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show();
                refresh();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_ADMIN) {
            if (RebootHelper.dpm(this).isAdminActive(RebootHelper.admin(this))) enableIntruder();
            else refresh();
        }
    }

    private void styleChip(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        tv.setTextColor(getColor(active ? R.color.text_primary : R.color.text_secondary));
    }
}
