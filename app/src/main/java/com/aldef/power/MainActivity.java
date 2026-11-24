package com.aldef.power;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Patterns;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 51;
    private static final int REQ_ADMIN = 52;

    private View statusDot, mailOptions;
    private TextView tvStatusTitle, tvStatusSub, tvIntruderStatus, tvMailStatus, tvRemoteShortcut;
    private Switch swBall, swCharging, swIntruder, swMail, swRemoteShortcut;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusDot = findViewById(R.id.statusDot);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusSub = findViewById(R.id.tvStatusSub);
        mailOptions = findViewById(R.id.mailOptions);
        tvIntruderStatus = findViewById(R.id.tvIntruderStatus);
        tvMailStatus = findViewById(R.id.tvMailStatus);
        tvRemoteShortcut = findViewById(R.id.tvRemoteShortcut);
        swBall = findViewById(R.id.swBall);
        swCharging = findViewById(R.id.swCharging);
        swIntruder = findViewById(R.id.swIntruder);
        swMail = findViewById(R.id.swMail);
        swRemoteShortcut = findViewById(R.id.swRemoteShortcut);

        findViewById(R.id.btnRestart).setOnClickListener(v ->
                RebootHelper.confirmAndRestart(this, this::refresh));
        findViewById(R.id.btnAccessibility).setOnClickListener(v ->
                RebootHelper.openAccessibilitySettings(this));
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.btnRemote).setOnClickListener(v ->
                startActivity(new Intent(this, RemoteActivity.class)));

        // Ketuk kartu = ubah sakelarnya
        findViewById(R.id.cardBall).setOnClickListener(v -> swBall.toggle());
        findViewById(R.id.cardCharging).setOnClickListener(v -> swCharging.toggle());
        findViewById(R.id.cardIntruder).setOnClickListener(v -> swIntruder.toggle());
        findViewById(R.id.rowRemoteShortcut).setOnClickListener(v -> swRemoteShortcut.toggle());
        findViewById(R.id.btnMail).setOnClickListener(v -> openMailSetup());
        findViewById(R.id.btnPreviewCharging).setOnClickListener(v -> ChargingActivity.show(this));

        swBall.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            Features.set(this, Features.KEY_BALL, on);
            if (on) needAccessibilityHint();
        });
        swCharging.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            Features.set(this, Features.KEY_CHARGING, on);
            if (on) needAccessibilityHint();
        });
        swIntruder.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            if (on) enableIntruder();
            else disableIntruder();
        });
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
        swRemoteShortcut.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            boolean pinAsked = RemoteShortcut.set(this, on);
            Toast.makeText(this, on
                    ? (pinAsked ? "Shortcut Aldef TV ditambahkan" : "Shortcut Aldef TV aktif — tahan ikonnya di laci aplikasi untuk ditaruh di layar utama")
                    : "Shortcut Aldef TV dihilangkan", Toast.LENGTH_LONG).show();
            refresh();
        });

        // Fitur rahasia: ketuk 5x
        TextView footer = findViewById(R.id.tvFooter);
        try {
            footer.setText("ALDEF TECH  ·  v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {
        }
        footer.setOnClickListener(secretTap(() -> openVault(VaultActivity.MODE_MANAGE)));
        findViewById(R.id.ivLogo).setOnClickListener(secretTap(() -> PinActivity.openGate(this)));

        RemoteShortcut.sync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        new Thread(() -> AppHider.rehideTemporary(this)).start();
    }

    private void refresh() {
        boolean active = RebootHelper.bestMethod(this) != RebootHelper.Method.NONE;
        int color = getColor(active ? R.color.ok : R.color.danger);
        statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        tvStatusTitle.setText(active ? "Layanan aktif" : "Layanan belum aktif");
        tvStatusSub.setText(active ? "Siap digunakan" : "Aktifkan Aksesibilitas di bawah ini");

        boolean intruderOn = Features.intruder(this) && intruderReady();
        if (Features.intruder(this) && !intruderOn) Features.set(this, Features.KEY_INTRUDER, false);
        boolean shortcutOn = RemoteShortcut.enabled(this);

        binding = true;
        swBall.setChecked(Features.ball(this));
        swCharging.setChecked(Features.charging(this));
        swIntruder.setChecked(intruderOn);
        swMail.setChecked(IntruderMailer.enabled(this));
        swRemoteShortcut.setChecked(shortcutOn);
        binding = false;

        // Saat aktif tidak ada teks status; saat nonaktif tampilkan apa yang dibutuhkan.
        tvIntruderStatus.setText("Butuh izin kamera & admin perangkat");
        tvIntruderStatus.setVisibility(intruderOn ? View.GONE : View.VISIBLE);
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
        tvRemoteShortcut.setText(shortcutOn
                ? "Ikon Aldef TV tampil di laci aplikasi & menu tahan-ikon"
                : "Ikon Aldef TV disembunyikan");
    }

    private void needAccessibilityHint() {
        if (!RebootHelper.isAccessibilityOn()) {
            Toast.makeText(this, "Aktifkan Aksesibilitas ALDEF POWER agar fitur ini berjalan",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ Incorrect Password Capture

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
                                    + "dengan kamera depan.");
            startActivityForResult(i, REQ_ADMIN);
            return;
        }
        Features.set(this, Features.KEY_INTRUDER, true);
        Toast.makeText(this, "Incorrect Password Capture aktif", Toast.LENGTH_SHORT).show();
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

    // ------------------------------------------------------------ email

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
                .setTitle("Kirim Foto ke Email")
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
                Toast.makeText(this, "Kirim ke email aktif", Toast.LENGTH_SHORT).show();
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
        if (!Patterns.EMAIL_ADDRESS.matcher(to).matches()) {
            etTo.setError("Email tujuan tidak valid");
            return null;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(user).matches()) {
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

    // ------------------------------------------------------------ util

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

    private void openAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }
}
