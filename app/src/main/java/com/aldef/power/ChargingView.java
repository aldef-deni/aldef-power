package com.aldef.power;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Animasi pengisian daya bergaya neon ALDEF (digambar manual, 60 fps). */
final class ChargingView extends View {

    private static final int[] NEON = {
            Color.rgb(34, 211, 255), Color.rgb(48, 110, 255), Color.rgb(139, 92, 255),
            Color.rgb(230, 36, 143), Color.rgb(255, 122, 24), Color.rgb(34, 211, 255)};

    private final float d;
    private final long start = SystemClock.uptimeMillis();
    private final Random rnd = new Random();

    private final Paint bgPaint = new Paint();
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pct = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pctSign = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Path boltPath = new Path();

    private SweepGradient sweep;
    private Bitmap logo;
    private float cx, cy, radius;

    // data baterai
    private int level = 0;
    private String status = "Mengisi daya";
    private String info1 = "", info2 = "", info3 = "";

    // partikel energi
    private static final int N = 34;
    private final float[] px = new float[N], py = new float[N], ps = new float[N], pv = new float[N];
    private final int[] pc = new int[N];

    ChargingView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setColor(Color.rgb(24, 27, 58));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arcGlow.setStyle(Paint.Style.STROKE);
        arcGlow.setStrokeCap(Paint.Cap.ROUND);
        head.setColor(Color.WHITE);
        pct.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pct.setTextAlign(Paint.Align.CENTER);
        pctSign.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pctSign.setColor(Color.argb(200, 200, 190, 255));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(Color.rgb(230, 232, 255));
        label.setLetterSpacing(0.04f);
        chipText.setTextAlign(Paint.Align.CENTER);
        chipText.setColor(Color.rgb(170, 176, 214));
        chipBg.setColor(Color.argb(28, 255, 255, 255));
        bolt.setStyle(Paint.Style.FILL);
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setColor(Color.argb(110, 154, 160, 200));
        hint.setLetterSpacing(0.25f);
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            logo = BitmapFactory.decodeResource(getResources(), R.drawable.aldef_logo, o);
        } catch (Exception ignored) {
        }
    }

    void setBattery(int level, String status, String info1, String info2, String info3) {
        this.level = level;
        this.status = status;
        this.info1 = info1;
        this.info2 = info2;
        this.info3 = info3;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h * 0.46f;
        radius = Math.min(w, h) * 0.31f;
        bgPaint.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.75f,
                Color.rgb(22, 16, 58), Color.rgb(3, 4, 12), Shader.TileMode.CLAMP));
        sweep = new SweepGradient(cx, cy, NEON, null);
        arc.setShader(sweep);
        arcGlow.setShader(sweep);
        track.setStrokeWidth(d * 12);
        arc.setStrokeWidth(d * 12);
        pct.setTextSize(radius * 0.62f);
        pct.setShader(new LinearGradient(0, cy - radius * 0.4f, 0, cy + radius * 0.2f,
                Color.WHITE, Color.rgb(196, 176, 255), Shader.TileMode.CLAMP));
        pctSign.setTextSize(radius * 0.22f);
        label.setTextSize(d * 17);
        chipText.setTextSize(d * 12);
        hint.setTextSize(d * 10);
        bolt.setShader(new LinearGradient(cx, cy - radius * 0.62f, cx, cy - radius * 0.36f,
                Color.rgb(255, 210, 90), Color.rgb(255, 122, 24), Shader.TileMode.CLAMP));
        for (int i = 0; i < N; i++) spawn(i, true);
    }

    private void spawn(int i, boolean anywhere) {
        float h = getHeight();
        px[i] = cx + (rnd.nextFloat() - 0.5f) * radius * 2.2f;
        py[i] = anywhere ? cy + radius + rnd.nextFloat() * (h - cy - radius) : h + rnd.nextFloat() * d * 40;
        ps[i] = d * (1.2f + rnd.nextFloat() * 2.6f);
        pv[i] = d * (0.9f + rnd.nextFloat() * 2.2f);
        pc[i] = NEON[rnd.nextInt(NEON.length - 1)];
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        float w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bgPaint);

        // Intro: cincin terisi dari 0 ke level dalam 1,4 detik
        float intro = Math.min(1f, t / 1.4f);
        float eased = 1f - (float) Math.pow(1f - intro, 3);
        float shown = level * eased;
        float sweepDeg = 360f * shown / 100f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6f);

        // Halo di belakang cincin
        halo.setShader(new RadialGradient(cx, cy, radius * (1.55f + 0.06f * pulse),
                Color.argb((int) (70 + 40 * pulse), 108, 59, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius * 1.7f, halo);

        // Partikel energi yang naik menuju cincin
        for (int i = 0; i < N; i++) {
            py[i] -= pv[i];
            px[i] += (float) Math.sin(t * 2 + i) * d * 0.3f;
            float distToRing = (float) Math.hypot(px[i] - cx, py[i] - cy) - radius;
            if (distToRing < d * 6 || py[i] < cy) spawn(i, false);
            float alpha = Math.max(0f, Math.min(1f, distToRing / (radius * 0.9f)));
            dot.setColor(pc[i]);
            dot.setAlpha((int) (200 * (1f - alpha * 0.6f)));
            c.drawCircle(px[i], py[i], ps[i], dot);
        }

        // Lintasan & cincin progres berputar
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawCircle(cx, cy, radius, track);
        shaderMatrix.setRotate(t * 50f, cx, cy);
        sweep.setLocalMatrix(shaderMatrix);
        for (int i = 4; i >= 1; i--) {
            arcGlow.setStrokeWidth(d * (12 + i * 9));
            arcGlow.setAlpha((int) ((16 + 10 * pulse) * (5 - i)));
            c.drawArc(rect, -90, sweepDeg, false, arcGlow);
        }
        c.drawArc(rect, -90, sweepDeg, false, arc);

        // Ujung cincin yang menyala
        double ang = Math.toRadians(-90 + sweepDeg);
        float hx = cx + radius * (float) Math.cos(ang), hy = cy + radius * (float) Math.sin(ang);
        head.setAlpha(90);
        c.drawCircle(hx, hy, d * (11 + 3 * pulse), head);
        head.setAlpha(255);
        c.drawCircle(hx, hy, d * 5, head);

        // Titik orbit
        for (int i = 0; i < 3; i++) {
            double a = t * (0.9 + i * 0.35) + i * 2.1;
            float r = radius + d * (26 + i * 10);
            dot.setColor(NEON[i * 2]);
            dot.setAlpha(170);
            c.drawCircle(cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), d * (2.6f - i * 0.5f), dot);
        }

        // Ikon petir berdenyut
        float bs = radius * (0.13f + 0.012f * pulse);
        float bx = cx, by = cy - radius * 0.5f;
        boltPath.reset();
        boltPath.moveTo(bx + bs * 0.25f, by - bs);
        boltPath.lineTo(bx - bs * 0.55f, by + bs * 0.15f);
        boltPath.lineTo(bx - bs * 0.02f, by + bs * 0.15f);
        boltPath.lineTo(bx - bs * 0.25f, by + bs);
        boltPath.lineTo(bx + bs * 0.55f, by - bs * 0.15f);
        boltPath.lineTo(bx + bs * 0.02f, by - bs * 0.15f);
        boltPath.close();
        c.drawPath(boltPath, bolt);

        // Persentase
        String num = String.valueOf(Math.round(shown));
        float numW = pct.measureText(num);
        float baseY = cy + pct.getTextSize() * 0.32f;
        c.drawText(num, cx - pctSign.measureText("%") * 0.4f, baseY, pct);
        c.drawText("%", cx + numW / 2f - pctSign.measureText("%") * 0.3f, baseY - pct.getTextSize() * 0.36f, pctSign);

        // Status & info
        float y = cy + radius + d * 62;
        c.drawText(status, cx, y, label);
        drawChips(c, y + d * 34, info1, info2, info3);

        // Logo & petunjuk
        if (logo != null) {
            float lw = d * 92, lh = lw * logo.getHeight() / logo.getWidth();
            rect.set(cx - lw / 2, d * 54, cx + lw / 2, d * 54 + lh);
            logoPaint.setAlpha((int) (255 * Math.min(1f, t * 2)));
            c.drawBitmap(logo, null, rect, logoPaint);
        }
        c.drawText("KETUK UNTUK MENUTUP", cx, h - d * 34, hint);

        postInvalidateOnAnimation();
    }

    private void drawChips(Canvas c, float y, String... texts) {
        float gap = d * 8, padH = d * 12, hgt = d * 28;
        float total = 0;
        int n = 0;
        for (String s : texts) {
            if (s == null || s.isEmpty()) continue;
            total += chipText.measureText(s) + padH * 2;
            n++;
        }
        if (n == 0) return;
        total += gap * (n - 1);
        float x = cx - total / 2f;
        for (String s : texts) {
            if (s == null || s.isEmpty()) continue;
            float cw = chipText.measureText(s) + padH * 2;
            rect.set(x, y - hgt / 2, x + cw, y + hgt / 2);
            c.drawRoundRect(rect, hgt / 2, hgt / 2, chipBg);
            c.drawText(s, x + cw / 2, y + chipText.getTextSize() * 0.35f, chipText);
            x += cw + gap;
        }
    }
}
