package com.aldef.power;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menyembunyikan aplikasi lain lewat DevicePolicyManager.setApplicationHidden
 * (butuh ALDEF POWER sebagai Device Owner / Mode Admin).
 *
 * Aplikasi tersembunyi dibuka dari Ruang Privat dengan cara "dimunculkan sementara",
 * lalu otomatis disembunyikan lagi saat kembali ke layar utama, layar mati,
 * atau Ruang Privat dibuka kembali.
 */
final class AppHider {

    private static final String PREFS = "app_hider";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_TEMP = "temp";

    private AppHider() {}

    /** Mode Admin (device owner): ALDEF sendiri yang menyembunyikan aplikasi. */
    static boolean isAvailable(Context c) {
        return RebootHelper.isDeviceOwner(c);
    }

    /** Tanpa Mode Admin: aplikasi disembunyikan oleh fitur "Hide apps" realme. */
    static boolean usesRealme(Context c) {
        return !isAvailable(c) && RealmePrivacy.isSupported(c);
    }

    /** Fitur sembunyikan aplikasi bisa dipakai (Mode Admin atau realme). */
    static boolean isSupported(Context c) {
        return isAvailable(c) || RealmePrivacy.isSupported(c);
    }

    // ------------------------------------------------------------ penyimpanan

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> get(Context c, String key) {
        return new HashSet<>(prefs(c).getStringSet(key, Collections.emptySet()));
    }

    private static void put(Context c, String key, Set<String> value) {
        prefs(c).edit().putStringSet(key, value).commit();
    }

    private static DevicePolicyManager dpm(Context c) {
        return RebootHelper.dpm(c);
    }

    private static ComponentName admin(Context c) {
        return RebootHelper.admin(c);
    }

    private static boolean setHidden(Context c, String pkg, boolean hidden) {
        try {
            dpm(c).setApplicationHidden(admin(c), pkg, hidden);
            return dpm(c).isApplicationHidden(admin(c), pkg) == hidden;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ aksi

    static synchronized boolean hide(Context c, String pkg) {
        if (isAvailable(c)) {
            if (!setHidden(c, pkg, true)) return false;
        } else if (!RealmePrivacy.isSupported(c)) {
            return false;
        }
        // Mode realme: ALDEF mencatat aplikasi ke Ruang Privat; penyembunyian ikon oleh realme.
        Set<String> s = get(c, KEY_HIDDEN);
        s.add(pkg);
        put(c, KEY_HIDDEN, s);
        return true;
    }

    static synchronized boolean unhide(Context c, String pkg) {
        if (isAvailable(c) && !setHidden(c, pkg, false)) return false;
        Set<String> s = get(c, KEY_HIDDEN);
        s.remove(pkg);
        put(c, KEY_HIDDEN, s);
        Set<String> t = get(c, KEY_TEMP);
        t.remove(pkg);
        put(c, KEY_TEMP, t);
        return true;
    }

    /** Hasil sinkron dari realme: daftar Ruang Privat = daftar Hide apps realme. */
    static synchronized void replaceRealmeHidden(Context c, Set<String> pkgs) {
        if (isAvailable(c)) return;   // Mode Admin punya daftarnya sendiri
        put(c, KEY_HIDDEN, new HashSet<>(pkgs));
    }

    static int unhideAll(Context c) {
        int n = 0;
        for (String pkg : get(c, KEY_HIDDEN)) {
            if (unhide(c, pkg)) n++;
        }
        return n;
    }

    /** Buka aplikasi tersembunyi tanpa harus unhide permanen. */
    static void launch(Activity a, String pkg) {
        if (isAvailable(a)) {
            synchronized (AppHider.class) {
                setHidden(a, pkg, false);
                Set<String> t = get(a, KEY_TEMP);
                t.add(pkg);
                put(a, KEY_TEMP, t);
            }
        }
        Intent li = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) {
            Toast.makeText(a, usesRealme(a)
                    ? "Aplikasi diblokir realme. Buka lewat kode akses di aplikasi Telepon."
                    : "Aplikasi tidak bisa dibuka", Toast.LENGTH_LONG).show();
            rehideTemporary(a);
            return;
        }
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(li);
    }

    static boolean hasTemporary(Context c) {
        return !prefs(c).getStringSet(KEY_TEMP, Collections.emptySet()).isEmpty();
    }

    /** Sembunyikan kembali aplikasi yang tadi dibuka dari Ruang Privat. */
    static synchronized void rehideTemporary(Context c) {
        Set<String> t = get(c, KEY_TEMP);
        if (t.isEmpty()) return;
        Set<String> hidden = get(c, KEY_HIDDEN);
        if (isAvailable(c)) {
            for (String pkg : t) {
                if (hidden.contains(pkg)) setHidden(c, pkg, true);
            }
        }
        put(c, KEY_TEMP, new HashSet<>());
    }

    // ------------------------------------------------------------ daftar

    static Set<String> launcherPackages(Context c) {
        Set<String> s = new HashSet<>();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, 0)) {
            s.add(ri.activityInfo.packageName);
        }
        return s;
    }

    /** Aplikasi yang bisa disembunyikan (punya ikon di launcher, belum tersembunyi). */
    static List<VaultItem> listVisibleApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> exclude = launcherPackages(c);
        exclude.add(c.getPackageName());
        exclude.add("com.android.settings");
        exclude.addAll(get(c, KEY_HIDDEN));

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, VaultItem> map = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (exclude.contains(pkg) || map.containsKey(pkg)) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            VaultItem it = new VaultItem(true, pkg, String.valueOf(ai.loadLabel(pm)));
            it.icon = ai.loadIcon(pm);
            map.put(pkg, it);
        }
        List<VaultItem> list = new ArrayList<>(map.values());
        list.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
        return list;
    }

    /** Aplikasi yang sedang disembunyikan. */
    static List<VaultItem> listHiddenApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> hidden = get(c, KEY_HIDDEN);
        List<VaultItem> list = new ArrayList<>();
        boolean changed = false;
        for (String pkg : new ArrayList<>(hidden)) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                VaultItem it = new VaultItem(true, pkg, String.valueOf(ai.loadLabel(pm)));
                it.icon = ai.loadIcon(pm);
                list.add(it);
            } catch (PackageManager.NameNotFoundException e) {
                hidden.remove(pkg);   // sudah di-uninstall
                changed = true;
            }
        }
        if (changed) put(c, KEY_HIDDEN, hidden);
        list.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
        return list;
    }
}
