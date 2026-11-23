package com.aldef.power;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;

/** Semua logika restart / kunci layar ada di sini. */
final class RebootHelper {

    enum Method { DEVICE_OWNER, ROOT, POWER_DIALOG, NONE }

    private RebootHelper() {}

    // ---------------------------------------------------------------- status

    static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    static DevicePolicyManager dpm(Context c) {
        return (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    static boolean isDeviceOwner(Context c) {
        DevicePolicyManager d = dpm(c);
        return d != null && d.isDeviceOwnerApp(c.getPackageName());
    }

    static boolean isAccessibilityOn() {
        return RebootAccessibilityService.instance != null;
    }

    /** Cek apakah binary su ada (tanpa memunculkan popup izin root). */
    static boolean suAvailable() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/system/sbin/su", "/vendor/bin/su", "/debug_ramdisk/su"};
        for (String p : paths) {
            if (new File(p).exists()) return true;
        }
        String env = System.getenv("PATH");
        if (env != null) {
            for (String dir : env.split(":")) {
                if (new File(dir, "su").exists()) return true;
            }
        }
        return false;
    }

    /** Menu power (pilihan pengguna) didahulukan; device owner / root hanya cadangan. */
    static Method bestMethod(Context c) {
        if (isAccessibilityOn()) return Method.POWER_DIALOG;
        if (isDeviceOwner(c)) return Method.DEVICE_OWNER;
        if (suAvailable()) return Method.ROOT;
        return Method.NONE;
    }

    static String label(Method m) {
        switch (m) {
            case DEVICE_OWNER: return "Device Owner (langsung restart)";
            case ROOT: return "Root (langsung restart)";
            case POWER_DIALOG: return "Menu Power (geser bulatan ke atas = Restart)";
            default: return "Belum ada. Aktifkan Aksesibilitas dulu.";
        }
    }

    // ---------------------------------------------------------------- aksi

    static boolean rebootAsOwner(Context c) {
        try {
            dpm(c).reboot(admin(c));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Blocking. Jalankan di background thread. */
    static boolean rebootAsRoot() {
        String[][] cmds = {
                {"su", "-c", "svc power reboot"},
                {"su", "-c", "reboot"},
        };
        for (String[] cmd : cmds) {
            try {
                Process p = Runtime.getRuntime().exec(cmd);
                if (p.waitFor() == 0) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    static boolean showPowerDialog() {
        RebootAccessibilityService s = RebootAccessibilityService.instance;
        return s != null && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
    }

    static boolean lockScreen(Context c) {
        RebootAccessibilityService s = RebootAccessibilityService.instance;
        if (s != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
            return true;
        }
        if (isDeviceOwner(c)) {
            try {
                dpm(c).lockNow();
                return true;
            } catch (SecurityException ignored) {
            }
        }
        return false;
    }

    static void openAccessibilitySettings(Context c) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    static void needAccessibility(Context c) {
        toast(c, "Aktifkan \"ALDEF POWER\" di Pengaturan > Aksesibilitas dulu.");
        openAccessibilitySettings(c);
    }

    // ---------------------------------------------------------------- alur restart

    /** Restart dengan konfirmasi bila restart-nya langsung (root / device owner). */
    static void confirmAndRestart(Activity a, Runnable onDone) {
        Method m = bestMethod(a);
        if (m == Method.DEVICE_OWNER || m == Method.ROOT) {
            new AlertDialog.Builder(a, R.style.Theme_AldefPower_Dialog)
                    .setTitle("Restart HP sekarang?")
                    .setMessage("HP akan langsung dimulai ulang. Simpan pekerjaan Anda dulu.")
                    .setPositiveButton("Restart", (d, w) -> performRestart(a, onDone))
                    .setNegativeButton("Batal", (d, w) -> runDone(onDone))
                    .setOnCancelListener(d -> runDone(onDone))
                    .show();
        } else {
            // Menu power bawaan sistem sudah menjadi konfirmasinya.
            performRestart(a, onDone);
        }
    }

    static void performRestart(Activity a, Runnable onDone) {
        Context app = a.getApplicationContext();
        Method m = bestMethod(app);

        if (m == Method.POWER_DIALOG) {
            fallbackPowerDialog(a, onDone);
            return;
        }

        if (m == Method.DEVICE_OWNER && rebootAsOwner(app)) return;

        if (m == Method.ROOT) {
            toast(app, "Meminta akses root...");
            new Thread(() -> {
                boolean ok = rebootAsRoot();
                if (!ok) a.runOnUiThread(() -> fallbackPowerDialog(a, onDone));
            }).start();
            return;
        }

        fallbackPowerDialog(a, onDone);
    }

    private static void fallbackPowerDialog(Activity a, Runnable onDone) {
        if (showPowerDialog()) {
            toast(a, "Geser bulatan ke ATAS untuk Restart. JANGAN ke bawah (Power off)!");
        } else {
            needAccessibility(a);
        }
        runDone(onDone);
    }

    private static void runDone(Runnable r) {
        if (r != null) r.run();
    }
}
