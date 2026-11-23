package com.aldef.power;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Splash 1: layar hitam (window background dari tema).
 * Splash 2: gambar ALDEF POWER dengan progress bar beranimasi selama 3 detik.
 */
public class SplashActivity extends Activity {

    private static final long BLACK_HOLD_MS = 500;   // splash 1 (hitam)
    private static final long FADE_IN_MS = 400;
    private static final long LOADING_MS = 3000;     // splash 2 (loading)

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator animator;
    private SplashView splashView;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashView = new SplashView(this);
        splashView.setAlpha(0f);
        setContentView(splashView);
        handler.postDelayed(this::startLoading, BLACK_HOLD_MS);
    }

    private void startLoading() {
        if (isFinishing()) return;
        splashView.animate().alpha(1f).setDuration(FADE_IN_MS).start();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(LOADING_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> splashView.setProgress((float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                openMain();
            }
        });
        animator.start();
    }

    private void openMain() {
        if (done || isFinishing()) return;
        done = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        done = true;
        handler.removeCallbacksAndMessages(null);
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
        }
        super.onDestroy();
    }

    /** Menggambar splash (center-crop) dan mengganti bar statis di gambar dengan bar beranimasi. */
    static final class SplashView extends View {

        // Koordinat bagian dalam progress bar pada gambar asli (941 x 1672 px)
        private static final float BAR_LEFT = 230.5f;
        private static final float BAR_RIGHT = 709f;
        private static final float BAR_TOP = 1293f;
        private static final float BAR_BOTTOM = 1313.5f;
        private static final float FILL_INSET = 1.2f;

        private final Bitmap bitmap;
        private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress;

        SplashView(Context c) {
            super(c);
            setBackgroundColor(Color.BLACK);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            bitmap = BitmapFactory.decodeResource(c.getResources(), R.drawable.splash_aldef_power, o);

            trackPaint.setColor(Color.rgb(3, 4, 45));

            Shader gradient = new LinearGradient(BAR_LEFT, 0, BAR_RIGHT, 0,
                    new int[]{Color.rgb(25, 230, 255), Color.rgb(30, 90, 255),
                            Color.rgb(150, 40, 255), Color.rgb(255, 26, 224)},
                    new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP);
            fillPaint.setShader(gradient);
            glowPaint.setShader(gradient);

            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(1.3f);
            edgePaint.setColor(Color.argb(210, 255, 255, 255));
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bitmap == null) return;
            int vw = getWidth(), vh = getHeight();
            int bw = bitmap.getWidth(), bh = bitmap.getHeight();
            float s = Math.max((float) vw / bw, (float) vh / bh);

            canvas.save();
            canvas.translate((vw - bw * s) / 2f, (vh - bh * s) / 2f);
            canvas.scale(s, s);
            canvas.drawBitmap(bitmap, 0, 0, bmpPaint);

            // Tutup isi bar statis bawaan gambar
            float r = (BAR_BOTTOM - BAR_TOP) / 2f;
            rect.set(BAR_LEFT, BAR_TOP, BAR_RIGHT, BAR_BOTTOM);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            // Isi bar sesuai progress
            float minW = (BAR_BOTTOM - BAR_TOP) - 2 * FILL_INSET;
            float fullW = (BAR_RIGHT - BAR_LEFT) - 2 * FILL_INSET;
            float w = Math.max(minW, fullW * progress);
            float left = BAR_LEFT + FILL_INSET, top = BAR_TOP + FILL_INSET, bottom = BAR_BOTTOM - FILL_INSET;
            float fr = (bottom - top) / 2f;

            // Glow
            for (int i = 3; i >= 1; i--) {
                float g = i * 1.6f;
                glowPaint.setAlpha(28 * (4 - i));
                rect.set(left - g, top - g, left + w + g, bottom + g);
                canvas.drawRoundRect(rect, fr + g, fr + g, glowPaint);
            }
            rect.set(left, top, left + w, bottom);
            canvas.drawRoundRect(rect, fr, fr, fillPaint);
            rect.inset(1.6f, 1.6f);
            canvas.drawRoundRect(rect, fr - 1.6f, fr - 1.6f, edgePaint);

            canvas.restore();
        }
    }
}
