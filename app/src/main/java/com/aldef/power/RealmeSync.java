package com.aldef.power;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sinkronisasi dua arah dengan fitur "Hide apps" realme.
 *
 * Daftar Hide apps realme dikunci izin sistem (signature), jadi tidak bisa dibaca/diubah
 * lewat API. ALDEF membuka layar Hide apps, lalu layanan aksesibilitas ALDEF:
 *  1. menyalakan / mematikan sakelar aplikasi yang diminta (ALDEF -> realme), lalu
 *  2. membaca semua sakelar yang menyala (realme -> ALDEF) dan memperbarui Ruang Privat.
 * Mesin langkahnya ada di {@link RebootAccessibilityService}.
 */
final class RealmeSync {

    static final String SAFE_PKG = "com.coloros.safecenter";

    /** Judul bagian "aplikasi lain" di layar Hide apps (batas akhir daftar tersembunyi). */
    static final String[] OTHER_HEADERS = {"OTHER APPS", "Other apps", "APLIKASI LAIN", "Aplikasi lain"};

    static final int PHASE_APPLY = 0;
    static final int PHASE_TOP = 1;
    static final int PHASE_READ = 2;

    private static final String PREFS = "realme_sync";
    private static final String KEY_ASKED = "asked_first_sync";

    static final class Job {
        final Map<String, Boolean> ops = new LinkedHashMap<>();   // label (normal) -> mau disembunyikan?
        final Map<String, String> labelToPkg = new HashMap<>();
        final Set<String> failed = new LinkedHashSet<>();
        final Set<String> hiddenLabels = new LinkedHashSet<>();
        int phase = PHASE_APPLY;
        int scrolls;
        long deadline;
        boolean overlayShown;
    }

    static volatile Job job;

    private RealmeSync() {}

    static String norm(CharSequence s) {
        return s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
    }

    static boolean isRunning() {
        Job j = job;
        return j != null && SystemClock.uptimeMillis() < j.deadline;
    }

    /**
     * Mulai sinkron. pkgOps: package -> true (sembunyikan) / false (tampilkan).
     * Kosong = hanya membaca daftar realme.
     */
    static boolean start(Context c, Map<String, Boolean> pkgOps) {
        if (!RebootHelper.isAccessibilityOn() || !RealmePrivacy.isSupported(c)) return false;
        PackageManager pm = c.getPackageManager();
        Job j = new Job();
        j.deadline = SystemClock.uptimeMillis() + 90_000;

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            j.labelToPkg.put(norm(ai.loadLabel(pm)), ai.packageName);
        }
        for (Map.Entry<String, Boolean> e : pkgOps.entrySet()) {
            String label = labelOf(pm, e.getKey());
            if (label.isEmpty()) continue;
            j.ops.put(norm(label), e.getValue());
            j.labelToPkg.put(norm(label), e.getKey());
        }
        job = j;
        if (!RealmePrivacy.openHideApps(c)) {
            job = null;
            return false;
        }
        return true;
    }

    static String labelOf(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(pm));
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** Ubah label realme yang menyala menjadi daftar package. */
    static Set<String> toPackages(Job j) {
        Set<String> pkgs = new LinkedHashSet<>();
        for (String label : j.hiddenLabels) {
            String pkg = j.labelToPkg.get(norm(label));
            if (pkg != null) pkgs.add(pkg);
        }
        return pkgs;
    }

    // ------------------------------------------------------------ tawaran sinkron pertama

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean shouldAskFirstSync(Context c) {
        return !prefs(c).getBoolean(KEY_ASKED, false);
    }

    static void markAsked(Context c) {
        prefs(c).edit().putBoolean(KEY_ASKED, true).apply();
    }
}
