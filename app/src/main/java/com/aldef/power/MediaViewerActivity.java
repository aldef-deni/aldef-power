package com.aldef.power;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;

/** Penampil foto / pemutar video untuk media tersembunyi (tanpa perlu unhide). */
public class MediaViewerActivity extends Activity {

    static final String EXTRA_PATH = "path";
    static final String EXTRA_VIDEO = "video";
    static final String EXTRA_NAME = "name";

    private static final int MAX_SIDE = 2560;

    private View topBar;
    private boolean barVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (VaultActivity.SECURE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_viewer);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        boolean video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        ((TextView) findViewById(R.id.tvName)).setText(getIntent().getStringExtra(EXTRA_NAME));
        topBar = findViewById(R.id.topBar);
        findViewById(R.id.btnClose).setOnClickListener(v -> finishFade());

        if (path == null || !new File(path).exists()) {
            Toast.makeText(this, "File tidak ditemukan", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (video) {
            VideoView vv = findViewById(R.id.video);
            vv.setVisibility(View.VISIBLE);
            MediaController mc = new MediaController(this);
            mc.setAnchorView(vv);
            vv.setMediaController(mc);
            vv.setVideoPath(path);
            vv.setOnPreparedListener(mp -> vv.start());
            vv.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "Video tidak bisa diputar", Toast.LENGTH_SHORT).show();
                return true;
            });
        } else {
            ImageView iv = findViewById(R.id.image);
            iv.setVisibility(View.VISIBLE);
            iv.setOnClickListener(v -> toggleBar());
            new Thread(() -> {
                Bitmap b = decode(path);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (b == null) {
                        Toast.makeText(this, "Gambar tidak bisa dibuka", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    iv.setAlpha(0f);
                    iv.setImageBitmap(b);
                    iv.animate().alpha(1f).setDuration(200).start();
                });
            }).start();
        }
    }

    private void toggleBar() {
        barVisible = !barVisible;
        topBar.animate().alpha(barVisible ? 1f : 0f).setDuration(180).start();
    }

    private static Bitmap decode(String path) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(new File(path)),
                        (decoder, info, src) -> {
                            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                            int s = 1;
                            while (Math.max(w, h) / s > MAX_SIDE) s *= 2;
                            decoder.setTargetSampleSize(s);
                        });
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > MAX_SIDE) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void finishFade() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        finishFade();
    }
}
