package com.aldef.power;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.net.Uri;
import android.widget.EditText;
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

    private Switch swTap, swBall, swIntruder, swCharging, swMail;
    private View tapOptions, accWarning, mailOptions;
    private TextView chipLow, chipMed, chipHigh, tvTapTest, tvIntruderStatus, tvMailStatus;
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
        swMail = findViewById(R.id.swMail);
        mailOptions = findViewById(R.id.mailOptions);
        tvMailStatus = findViewById(R.id.tvMailStatus);
        findViewById(R.id.btnMail).setOnClickListener(v -> openMailSetup());
        swMail.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            if (on && !IntruderMailer.configured(this)) {
                openMailSetup();
            } else {
                IntruderMailer.setEnabled(this, on);
                if (on) IntruderMailer.flush(this);
            }
            refresh();
        });

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
        swMail.setChecked(IntruderMailer.enabled(this));
        binding = false;

        mailOptions.setVisibility(intruderOn ? View.VISIBLE : View.GONE);
        String ms;
        if (!IntruderMailer.configured(this)) {
            ms = "Ketuk untuk atur akun Gmail pengirim";
        } else if (!IntruderMailer.enabled(this)) {
            ms = "Nonaktif · ke " + IntruderMailer.to(this);
        } else {
            String st = IntruderMailer.status(this);
            int pend = IntruderMailer.pendingCount(this);
            ms = "Ke " + IntruderMailer.to(this)
                    + (pend > 0 ? "\n" + pend + " foto menunggu dikirim" : "")
                    + (st != null ? "\n" + st : "");
        }
        tvMailStatus.setText(ms);

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

    // ------------------------------------------------------------ email penyusup

    private void openMailSetup() {
        View form = getLayoutInflater().inflate(R.layout.dialog_mail, null);
        EditText etTo = form.findViewById(R.id.etTo);
        EditText etUser = form.findViewById(R.id.etUser);
        EditText etPass = form.findViewById(R.id.etPass);
        etTo.setText(IntruderMailer.to(this));
        String user = IntruderMailer.user(this);
        etUser.setText(user.isEmpty() ? IntruderMailer.DEFAULT_TO : user);
        final boolean hasPass = IntruderMailer.hasPassword(this);
        if (hasPass) etPass.setHint("•••• tersimpan (kosongkan = tetap)");
        form.findViewById(R.id.btnAppPassword).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://myaccount.google.com/apppasswords")));
            } catch (Exception e) {
                Toast.makeText(this, "Buka myaccount.google.com/apppasswords di browser", Toast.LENGTH_LONG).show();
            }
        });

        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_AldefPower_Dialog)
                .setTitle("Kirim Selfie Penyusup ke Email")
                .setView(form)
                .setPositiveButton("Simpan", null)
                .setNeutralButton("Kirim Uji", null)
                .setNegativeButton("Batal", null)
                .create();
        d.setOnDismissListener(x -> refresh());
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String[] f = readMailForm(etTo, etUser, etPass, hasPass);
                if (f == null) return;
                if (!IntruderMailer.save(this, f[1], f[3], f[0])) {
                    Toast.makeText(this, "Gagal menyimpan sandi secara aman", Toast.LENGTH_LONG).show();
                    return;
                }
                IntruderMailer.setEnabled(this, true);
                IntruderMailer.flush(this);
                Toast.makeText(this, "Email penyusup aktif", Toast.LENGTH_SHORT).show();
                d.dismiss();
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String[] f = readMailForm(etTo, etUser, etPass, hasPass);
                if (f == null) return;
                v.setEnabled(false);
                Toast.makeText(this, "Mengirim email uji…", Toast.LENGTH_SHORT).show();
                IntruderMailer.sendTest(this, f[1], f[2], f[0], err -> {
                    v.setEnabled(true);
                    if (err == null) {
                        Toast.makeText(this, "Email uji terkirim ke " + f[0], Toast.LENGTH_LONG).show();
                    } else {
                        new AlertDialog.Builder(this, R.style.Theme_AldefPower_Dialog)
                                .setTitle("Email uji gagal")
                                .setMessage(err)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            });
        });
        d.show();
    }

    /** @return {tujuan, pengirim, sandi untuk dipakai, sandi baru (null = tetap)} atau null jika tidak valid. */
    private String[] readMailForm(EditText etTo, EditText etUser, EditText etPass, boolean hasPass) {
        String to = etTo.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString().replace(" ", "").trim();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(to).matches()) {
            etTo.setError("Email tujuan tidak valid");
            return null;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(user).matches()) {
            etUser.setError("Alamat Gmail tidak valid");
            return null;
        }
        String newPass = pass.isEmpty() ? null : pass;
        String use = newPass != null ? newPass : (hasPass ? IntruderMailer.password(this) : null);
        if (use == null) {
            etPass.setError("Isi Sandi Aplikasi Gmail");
            return null;
        }
        if (newPass != null && newPass.length() != 16) {
            etPass.setError("Sandi Aplikasi Gmail terdiri dari 16 huruf");
            return null;
        }
        return new String[]{to, user, use, newPass};
    }

    private void styleChip(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        tv.setTextColor(getColor(active ? R.color.text_primary : R.color.text_secondary));
    }
}
