package com.aldef.power;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Menyembunyikan foto & video dengan memindahkannya ke folder tersembunyi
 * /storage/emulated/0/.aldef_vault (berisi .nomedia, jadi tidak tampil di Galeri).
 * Folder ini tetap ada walau aplikasi di-uninstall, jadi file tidak hilang.
 */
final class MediaVault {

    private static final String DIR = ".aldef_vault";
    /** Brankas pengecoh (nama netral, tidak mencurigakan). */
    private static final String DIR_DECOY = ".aldef_media";
    private static final String INDEX = "index.json";

    /** Path yang baru saja dipindahkan (MediaStore butuh waktu untuk memperbarui). */
    private static final Set<String> recentlyMoved = Collections.synchronizedSet(new HashSet<>());

    private MediaVault() {}

    @SuppressWarnings("deprecation")
    static File dir() {
        return dir(VaultPin.SPACE_REAL);
    }

    @SuppressWarnings("deprecation")
    static File dir(int space) {
        File d = new File(Environment.getExternalStorageDirectory(),
                space == VaultPin.SPACE_DECOY ? DIR_DECOY : DIR);
        if (!d.exists()) d.mkdirs();
        File nomedia = new File(d, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException ignored) {
            }
        }
        return d;
    }

    // ------------------------------------------------------------ izin

    static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static void requestPermission(Activity a, int requestCode) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            a.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        }
    }

    // ------------------------------------------------------------ galeri

    @SuppressWarnings("deprecation")
    static List<VaultItem> queryGallery(Context c) {
        List<VaultItem> out = new ArrayList<>();
        boolean hasDuration = Build.VERSION.SDK_INT >= 29;
        String[] proj = hasDuration
                ? new String[]{MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.MediaColumns.DURATION}
                : new String[]{MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MEDIA_TYPE};
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN ("
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + ","
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        String vaultPath = dir(VaultPin.SPACE_REAL).getAbsolutePath();
        String decoyPath = dir(VaultPin.SPACE_DECOY).getAbsolutePath();

        try (Cursor cur = c.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                proj, sel, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                String path = cur.getString(1);
                if (path == null || path.startsWith(vaultPath) || path.startsWith(decoyPath)
                        || recentlyMoved.contains(path)) continue;
                String name = cur.getString(2);
                boolean video = cur.getInt(3) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                VaultItem it = new VaultItem(false, "m" + id, name != null ? name : new File(path).getName());
                it.mediaId = id;
                it.path = path;
                it.video = video;
                it.contentUri = ContentUris.withAppendedId(video
                        ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                if (hasDuration && video) it.durationMs = cur.getLong(4);
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------ sembunyikan / tampilkan

    static synchronized int hide(Context c, List<VaultItem> items, int space) {
        File d = dir(space);
        JSONArray index = readIndex(d);
        List<String> scan = new ArrayList<>();
        int n = 0;
        for (VaultItem it : items) {
            if (it.path == null) continue;
            File src = new File(it.path);
            if (!src.exists()) continue;
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            File dst = new File(d, id + "_" + src.getName());
            if (!move(src, dst)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("file", dst.getName());
                o.put("orig", src.getAbsolutePath());
                o.put("name", src.getName());
                o.put("video", it.video);
                o.put("dur", it.durationMs);
                o.put("time", System.currentTimeMillis());
                index.put(o);
            } catch (JSONException ignored) {
            }
            writeIndex(d, index);   // simpan tiap file agar tidak ada yang "hilang jejak"
            recentlyMoved.add(src.getAbsolutePath());
            if (it.contentUri != null) {
                try {
                    c.getContentResolver().delete(it.contentUri, null, null);
                } catch (Exception ignored) {
                }
            }
            scan.add(src.getAbsolutePath());
            n++;
        }
        scan(c, scan);
        return n;
    }

    static synchronized int unhide(Context c, List<VaultItem> items, int space) {
        Set<String> ids = new HashSet<>();
        for (VaultItem it : items) if (it.vaultId != null) ids.add(it.vaultId);

        File d = dir(space);
        JSONArray index = readIndex(d);
        JSONArray keep = new JSONArray();
        List<String> scan = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o == null) continue;
            if (!ids.contains(o.optString("id"))) {
                keep.put(o);
                continue;
            }
            File f = new File(d, o.optString("file"));
            File target = uniquify(new File(o.optString("orig")));
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (f.exists() && move(f, target)) {
                recentlyMoved.remove(target.getAbsolutePath());
                scan.add(target.getAbsolutePath());
                n++;
            } else if (f.exists()) {
                keep.put(o);
            }
        }
        writeIndex(d, keep);
        scan(c, scan);
        return n;
    }

    static List<VaultItem> listHidden(int space) {
        File d = dir(space);
        JSONArray index = readIndex(d);
        List<VaultItem> out = new ArrayList<>();
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o == null) continue;
            File f = new File(d, o.optString("file"));
            if (!f.exists()) continue;
            String id = o.optString("id");
            VaultItem it = new VaultItem(false, "v" + id, o.optString("name", f.getName()));
            it.vaultId = id;
            it.path = f.getAbsolutePath();
            it.video = o.optBoolean("video");
            it.durationMs = o.optLong("dur");
            it.time = o.optLong("time");
            out.add(it);
        }
        out.sort((x, y) -> Long.compare(y.time, x.time));
        return out;
    }

    // ------------------------------------------------------------ util

    private static boolean move(File src, File dst) {
        if (src.renameTo(dst)) return true;
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } catch (IOException e) {
            dst.delete();
            return false;
        }
        if (dst.length() != src.length()) {
            dst.delete();
            return false;
        }
        dst.setLastModified(src.lastModified());
        if (!src.delete()) {
            dst.delete();
            return false;
        }
        return true;
    }

    private static File uniquify(File f) {
        if (!f.exists()) return f;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            File g = new File(f.getParentFile(), base + " (" + i + ")" + ext);
            if (!g.exists()) return g;
        }
    }

    private static void scan(Context c, List<String> paths) {
        if (paths.isEmpty()) return;
        MediaScannerConnection.scanFile(c.getApplicationContext(),
                paths.toArray(new String[0]), null, null);
    }

    private static JSONArray readIndex(File d) {
        File f = new File(d, INDEX);
        if (!f.exists()) return new JSONArray();
        try (InputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0, r;
            while (off < data.length && (r = in.read(data, off, data.length - off)) > 0) off += r;
            return new JSONArray(new String(data, 0, off, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void writeIndex(File d, JSONArray index) {
        File tmp = new File(d, INDEX + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(index.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        File f = new File(d, INDEX);
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }
}
