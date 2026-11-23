package com.aldef.power;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.CycleInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Papan kode Ruang Privat.
 * - MODE_UNLOCK     : kode asli -> Ruang Privat, kode pengecoh -> brankas palsu, salah -> selfie penyusup
 * - MODE_SET_REAL   : buat / ubah kode asli
 * - MODE_SET_DECOY  : buat / ubah kode pengecoh
 */
public class PinActivity extends Activity {

    static final String EXTRA_MODE = "pin_mode";
    static final int MODE_UNLOCK = 0;
    static final int MODE_SET_REAL = 1;
    static final int MODE_SET_DECOY = 2;

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 30_000;
    private static long lockedUntil;

    private int mode;
    private boolean openAfterSet;     // buat kode pertama kali -> langsung buka Ruang Privat
    private String firstEntry;        // tahap konfirmasi saat membuat kode
    private final StringBuilder pin = new StringBuilder();
    private int wrong;

    private TextView tvTitle, tvSubtitle, tvError;
    private LinearLayout dots;

    static void start(Context c, int mode) {
        Intent i = new Intent(c, PinActivity.class).putExtra(EXTRA_MODE, mode);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Pintu masuk dari logo 5x. */
    static void openGate(Activity a) {
        if (VaultPin.has(a, VaultPin.SPACE_REAL)) {
            start(a, MODE_UNLOCK);
        } else {
            a.startActivity(new Intent(a, PinActivity.class)
                    .putExtra(EXTRA_MODE, MODE_SET_REAL).putExtra("open_after", true));
        }
        a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (VaultActivity.SECURE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_pin);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);
        openAfterSet = getIntent().getBooleanExtra("open_after", false);

        tvTitle = findViewById(R.id.tvPinTitle);
        tvSubtitle = findViewById(R.id.tvPinSubtitle);
        tvError = findViewById(R.id.tvPinError);
        dots = findViewById(R.id.pinDots);
        findViewById(R.id.btnPinBack).setOnClickListener(v -> finishFade());
        buildKeypad();
        resetPrompt();
    }

    private void resetPrompt() {
        pin.setLength(0);
        tvError.setText("");
        if (mode == MODE_UNLOCK) {
            tvTitle.setText("Ruang Privat");
            tvSubtitle.setText("Masukkan kode");
        } else if (firstEntry == null) {
            boolean exists = VaultPin.has(this, mode == MODE_SET_DECOY ? VaultPin.SPACE_DECOY : VaultPin.SPACE_REAL);
            tvTitle.setText(mode == MODE_SET_DECOY && !exists ? "Kode Pengecoh"
                    : exists ? "Ubah Kode" : "Buat Kode Ruang Privat");
            tvSubtitle.setText(VaultPin.MIN_LEN + "–" + VaultPin.MAX_LEN + " angka, lalu ketuk ✓");
        } else {
            tvTitle.setText("Ulangi Kode");
            tvSubtitle.setText("Masukkan kode yang sama sekali lagi");
        }
        renderDots();
    }

    // ------------------------------------------------------------ keypad

    private void buildKeypad() {
        GridLayout grid = findViewById(R.id.keypad);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok"};
        int size = dp(76);
        int gap = dp(10);
        for (String k : keys) {
            View v;
            if (k.equals("del") || k.equals("ok")) {
                ImageView iv = new ImageView(this);
                iv.setImageResource(k.equals("del") ? R.drawable.ic_backspace : R.drawable.ic_check);
                iv.setScaleType(ImageView.ScaleType.CENTER);
                iv.setPadding(dp(24), dp(24), dp(24), dp(24));
                iv.setBackgroundResource(k.equals("ok") ? R.drawable.bg_key_ok : R.drawable.bg_key);
                iv.setContentDescription(k.equals("del") ? "Hapus" : "OK");
                v = iv;
            } else {
                TextView tv = new TextView(this);
                tv.setText(k);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(28);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
                tv.setBackgroundResource(R.drawable.bg_key);
                v = tv;
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(gap, gap, gap, gap);
            v.setLayoutParams(lp);
            v.setOnClickListener(x -> onKey(k, x));
            if (k.equals("del")) v.setOnLongClickListener(x -> {
                pin.setLength(0);
                renderDots();
                return true;
            });
            grid.addView(v);
        }
    }

    private void onKey(String k, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (mode == MODE_UNLOCK && SystemClock.elapsedRealtime() < lockedUntil) {
            showLockout();
            return;
        }
        tvError.setText("");
        if (k.equals("del")) {
            if (pin.length() > 0) pin.setLength(pin.length() - 1);
        } else if (k.equals("ok")) {
            submit();
            return;
        } else if (pin.length() < VaultPin.MAX_LEN) {
            pin.append(k);
        }
        renderDots();
    }

    private void renderDots() {
        dots.removeAllViews();
        int n = Math.max(VaultPin.MIN_LEN, pin.length());
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
            lp.setMargins(dp(8), 0, dp(8), 0);
            d.setLayoutParams(lp);
            d.setBackgroundResource(i < pin.length() ? R.drawable.bg_pin_dot_on : R.drawable.bg_pin_dot_off);
            dots.addView(d);
        }
    }

    // ------------------------------------------------------------ logika

    private void submit() {
        String code = pin.toString();
        if (code.length() < VaultPin.MIN_LEN) {
            error("Minimal " + VaultPin.MIN_LEN + " angka");
            return;
        }
        if (mode == MODE_UNLOCK) {
            int space = VaultPin.check(this, code);
            if (space == VaultPin.NO_MATCH) {
                onWrong();
                return;
            }
            openVault(space);
            return;
        }
        // Membuat / mengubah kode
        if (firstEntry == null) {
            int other = mode == MODE_SET_DECOY ? VaultPin.SPACE_REAL : VaultPin.SPACE_DECOY;
            if (VaultPin.matches(this, other, code)) {
                error("Kode harus berbeda dari kode lainnya");
                return;
            }
            firstEntry = code;
            resetPrompt();
        } else if (firstEntry.equals(code)) {
            int space = mode == MODE_SET_DECOY ? VaultPin.SPACE_DECOY : VaultPin.SPACE_REAL;
            VaultPin.set(this, space, code);
            android.widget.Toast.makeText(this, "Kode disimpan", android.widget.Toast.LENGTH_SHORT).show();
            if (openAfterSet) openVault(VaultPin.SPACE_REAL);
            else finishFade();
        } else {
            firstEntry = null;
            resetPrompt();
            error("Kode tidak sama, ulangi dari awal");
        }
    }

    private void onWrong() {
        wrong++;
        if (Features.intruder(this)
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            new IntruderCamera(this, IntruderStore.REASON_VAULT, null).capture();
        }
        if (wrong >= MAX_ATTEMPTS) {
            wrong = 0;
            lockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS;
            showLockout();
        } else {
            error("Kode salah");
        }
    }

    private void showLockout() {
        long s = Math.max(1, (lockedUntil - SystemClock.elapsedRealtime()) / 1000);
        error("Terlalu banyak percobaan. Coba lagi dalam " + s + " detik");
    }

    private void error(String msg) {
        pin.setLength(0);
        renderDots();
        tvError.setText(msg);
        dots.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        dots.animate().cancel();
        dots.setTranslationX(0);
        dots.animate().translationX(dp(14)).setInterpolator(new CycleInterpolator(3)).setDuration(420).start();
    }

    private void openVault(int space) {
        startActivity(new Intent(this, VaultActivity.class)
                .putExtra(VaultActivity.EXTRA_MODE, VaultActivity.MODE_VAULT)
                .putExtra(VaultActivity.EXTRA_SPACE, space));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void finishFade() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        finishFade();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
