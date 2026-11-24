package com.aldef.power;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import java.util.Collections;

/**
 * Opsi "Shortcut Aldef TV": ikon Aldef TV di laci aplikasi (activity-alias), pintasan di menu
 * tahan-ikon ALDEF POWER, dan ikon yang disematkan ke layar utama. Bisa ditambah / dihilangkan.
 */
final class RemoteShortcut {

    static final String ID = "aldef_tv";

    private RemoteShortcut() {}

    private static ComponentName alias(Context c) {
        return new ComponentName(c.getPackageName(), "com.aldef.power.RemoteLauncher");
    }

    static boolean enabled(Context c) {
        int s = c.getPackageManager().getComponentEnabledSetting(alias(c));
        return s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private static ShortcutInfo info(Context c) {
        return new ShortcutInfo.Builder(c, ID)
                .setShortLabel(c.getString(R.string.aldef_tv))
                .setLongLabel("Buka Aldef Remote")
                .setIcon(Icon.createWithResource(c, R.mipmap.ic_launcher_remote))
                .setIntent(new Intent(Intent.ACTION_VIEW).setClass(c, RemoteActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK))
                .build();
    }

    /** Pastikan pintasan menu tahan-ikon ada bila opsi aktif (dipanggil saat aplikasi dibuka). */
    static void sync(Context c) {
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null) return;
        try {
            if (enabled(c)) sm.addDynamicShortcuts(Collections.singletonList(info(c)));
            else sm.removeDynamicShortcuts(Collections.singletonList(ID));
        } catch (Exception ignored) {
        }
    }

    /**
     * @return true jika diminta menyematkan ke layar utama (launcher mendukung); false jika
     * pengguna perlu menyeret ikon Aldef TV dari laci aplikasi sendiri.
     */
    static boolean set(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(alias(c),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null) return false;
        try {
            if (on) {
                sm.enableShortcuts(Collections.singletonList(ID));
            } else {
                sm.removeDynamicShortcuts(Collections.singletonList(ID));
                sm.disableShortcuts(Collections.singletonList(ID), "Shortcut Aldef TV dinonaktifkan");
                return false;
            }
        } catch (Exception ignored) {
        }
        sync(c);
        try {
            if (sm.isRequestPinShortcutSupported()) return sm.requestPinShortcut(info(c), null);
        } catch (Exception ignored) {
        }
        return false;
    }
}
