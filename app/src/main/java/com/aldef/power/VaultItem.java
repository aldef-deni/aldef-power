package com.aldef.power;

import android.graphics.drawable.Drawable;
import android.net.Uri;

/** Satu item di daftar: aplikasi atau media (foto/video). */
final class VaultItem {
    final boolean app;
    final String key;      // unik: package name / "m"+mediaId / "v"+vaultId
    final String label;

    // aplikasi
    Drawable icon;

    // media
    long mediaId = -1;     // id MediaStore (media yang belum disembunyikan)
    Uri contentUri;
    String path;           // lokasi file saat ini
    boolean video;
    long durationMs;
    String vaultId;        // id di brankas (media tersembunyi)
    long time;

    VaultItem(boolean app, String key, String label) {
        this.app = app;
        this.key = key;
        this.label = label;
    }
}
