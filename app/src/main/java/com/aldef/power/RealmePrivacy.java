package com.aldef.power;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Jembatan ke fitur bawaan realme/ColorOS "Hide apps" (Pengaturan > Privasi > Hide apps).
 * Layar Hide apps dilindungi izin sistem, jadi ALDEF membuka halaman Privasi lalu
 * layanan aksesibilitas ALDEF mengetuk menu "Hide apps" secara otomatis.
 */
final class RealmePrivacy {

    static final String SETTINGS_PKG = "com.android.settings";

    /** Teks menu "Hide apps" dalam berbagai bahasa sistem. */
    static final String[] HIDE_APPS_LABELS = {
            "Hide apps", "Sembunyikan aplikasi", "Sembunyikan Aplikasi", "Hide Apps"
    };

    private static final String[] PRIVACY_ACTIONS = {
            "android.settings.COLOR_PRIVACY",
            "oppo.intent.action.PRIVACY_SETTINGS",
            Settings.ACTION_PRIVACY_SETTINGS
    };

    /** Batas waktu auto-tap "Hide apps" setelah halaman Privasi dibuka. */
    private static volatile long autoTapUntil;

    private RealmePrivacy() {}

    static boolean isSupported(Context c) {
        PackageManager pm = c.getPackageManager();
        return new Intent(PRIVACY_ACTIONS[0]).resolveActivity(pm) != null
                || new Intent(PRIVACY_ACTIONS[1]).resolveActivity(pm) != null;
    }

    /** Buka layar Hide apps realme (lewat halaman Privasi + auto-tap). */
    static boolean openHideApps(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String action : PRIVACY_ACTIONS) {
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(pm) == null) continue;
            if (RebootHelper.isAccessibilityOn()) autoTapUntil = SystemClock.uptimeMillis() + 6000;
            try {
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {
                autoTapUntil = 0;
            }
        }
        return false;
    }

    /** Dipanggil service aksesibilitas: true jika masih menunggu auto-tap. */
    static boolean consumeAutoTapWindow() {
        return SystemClock.uptimeMillis() < autoTapUntil;
    }

    static void autoTapDone() {
        autoTapUntil = 0;
    }
}
