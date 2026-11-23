package com.aldef.power;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Memuat thumbnail foto/video di background dengan cache memori. */
final class ThumbLoader {

    private static final int SIZE = 320;

    private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache;

    ThumbLoader(Context c) {
        resolver = c.getContentResolver();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    void load(ImageView iv, VaultItem it) {
        final String key = it.key;
        iv.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(null);
        pool.execute(() -> {
            if (!key.equals(iv.getTag())) return;
            Bitmap bm = decode(it);
            if (bm == null) return;
            cache.put(key, bm);
            main.post(() -> {
                if (key.equals(iv.getTag())) {
                    iv.setAlpha(0f);
                    iv.setImageBitmap(bm);
                    iv.animate().alpha(1f).setDuration(180).start();
                }
            });
        });
    }

    @SuppressWarnings("deprecation")
    private Bitmap decode(VaultItem it) {
        Size size = new Size(SIZE, SIZE);
        try {
            if (it.contentUri != null && Build.VERSION.SDK_INT >= 29) {
                return resolver.loadThumbnail(it.contentUri, size, null);
            }
            if (it.path == null) return null;
            File f = new File(it.path);
            if (Build.VERSION.SDK_INT >= 29) {
                return it.video ? ThumbnailUtils.createVideoThumbnail(f, size, null)
                        : ThumbnailUtils.createImageThumbnail(f, size, null);
            }
            if (it.video) {
                return ThumbnailUtils.createVideoThumbnail(it.path, MediaStore.Video.Thumbnails.MINI_KIND);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(it.path, o);
            int s = 1;
            while (Math.min(o.outWidth, o.outHeight) / (s * 2) >= SIZE) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(it.path, o);
            return b == null ? null : ThumbnailUtils.extractThumbnail(b, SIZE, SIZE);
        } catch (Exception e) {
            return null;
        }
    }

    void shutdown() {
        pool.shutdownNow();
        cache.evictAll();
    }
}
