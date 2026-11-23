package com.aldef.power;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Foto penyusup disimpan di penyimpanan internal ALDEF (tidak tampil di Galeri),
 * lengkap dengan cap waktu & alasan yang digambar langsung di foto.
 */
final class IntruderStore {

    static final String REASON_LOCK = "lock";
    static final String REASON_VAULT = "vault";

    private static final Locale ID = new Locale("id", "ID");
    private static final int MAX_SIDE = 1280;

    private IntruderStore() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "intruders");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static List<File> list(Context c) {
        File[] files = dir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        List<File> out = new ArrayList<>(files == null ? new ArrayList<>() : Arrays.asList(files));
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    static int count(Context c) {
        return list(c).size();
    }

    static String reasonText(File f) {
        return f.getName().contains("_" + REASON_VAULT) ? "Kode Ruang Privat salah" : "PIN layar kunci salah";
    }

    static String timeText(long when) {
        return new SimpleDateFormat("EEEE, d MMM yyyy · HH:mm:ss", ID).format(new Date(when));
    }

    static void clear(Context c) {
        for (File f : list(c)) f.delete();
    }

    /** Simpan foto JPEG mentah dari kamera, diputar sesuai EXIF & diberi cap waktu. */
    static File save(Context c, byte[] jpeg, String reason) {
        long now = System.currentTimeMillis();
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > MAX_SIDE) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inMutable = true;
            Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (bmp == null) return null;

            int rotation = 0;
            try {
                ExifInterface exif = new ExifInterface(new ByteArrayInputStream(jpeg));
                int ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rotation = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                        : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                        : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (Exception ignored) {
            }
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                bmp.recycle();
                bmp = r.isMutable() ? r : r.copy(Bitmap.Config.ARGB_8888, true);
            }
            stamp(bmp, reason, now);

            String name = "intruder_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(now))
                    + "_" + reason + ".jpg";
            File out = new File(dir(c), name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            bmp.recycle();
            return out;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static void stamp(Bitmap bmp, String reason, long when) {
        Canvas cv = new Canvas(bmp);
        int w = bmp.getWidth(), h = bmp.getHeight();
        float band = h * 0.24f;

        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, h - band, 0, h,
                Color.TRANSPARENT, Color.argb(215, 5, 6, 15), Shader.TileMode.CLAMP));
        cv.drawRect(0, h - band, w, h, bg);

        float pad = w * 0.05f;
        Paint tag = new Paint(Paint.ANTI_ALIAS_FLAG);
        tag.setColor(Color.rgb(255, 77, 109));
        tag.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tag.setTextSize(w * 0.034f);
        tag.setLetterSpacing(0.12f);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextSize(w * 0.05f);

        Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
        sub.setColor(Color.argb(220, 210, 214, 240));
        sub.setTextSize(w * 0.036f);

        Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
        brand.setColor(Color.argb(200, 183, 156, 255));
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brand.setTextSize(w * 0.03f);
        brand.setLetterSpacing(0.2f);
        brand.setTextAlign(Paint.Align.RIGHT);

        float y = h - pad;
        cv.drawText(timeText(when), pad, y, sub);
        y -= sub.getTextSize() * 1.5f;
        cv.drawText(REASON_VAULT.equals(reason) ? "Kode Ruang Privat salah" : "PIN layar kunci salah", pad, y, title);
        y -= title.getTextSize() * 1.35f;
        cv.drawText("PERCOBAAN AKSES GAGAL", pad, y, tag);
        cv.drawText("ALDEF POWER", w - pad, h - pad, brand);
    }
}
