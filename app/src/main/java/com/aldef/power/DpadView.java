package com.aldef.power;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** D-pad bulat bergaya neon ALDEF: 4 arah + OK di tengah. Tahan arah = berulang, tahan OK = tekan lama. */
public class DpadView extends View {

    interface OnKey {
        void key(int keyCode, int direction);
    }

    private static final int NONE = -1, UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3, OK = 4;
    private static final int[] CODES = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint okFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint okText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF(), ovalIn = new RectF();
    private final int[] neon = {Color.rgb(34, 211, 255), Color.rgb(108, 59, 255), Color.rgb(230, 36, 143),
            Color.rgb(255, 122, 24), Color.rgb(34, 211, 255)};

    private float cx, cy, outer, inner;
    private int pressed = NONE;
    private boolean longSent;
    private OnKey onKey;

    private final Runnable repeat = new Runnable() {
        @Override
        public void run() {
            if (pressed >= UP && pressed <= LEFT) {
                fire(CODES[pressed], TvRemote.DIR_SHORT);
                postDelayed(this, 110);
            }
        }
    };

    private final Runnable longOk = () -> {
        if (pressed == OK) {
            longSent = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            fire(KeyEvent.KEYCODE_DPAD_CENTER, TvRemote.DIR_START_LONG);
        }
    };

    public DpadView(Context c) {
        this(c, null);
    }

    public DpadView(Context c, AttributeSet a) {
        super(c, a);
        ring.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        okText.setColor(Color.WHITE);
        okText.setTextAlign(Paint.Align.CENTER);
        okText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        okText.setLetterSpacing(0.1f);
        setHapticFeedbackEnabled(true);
        setContentDescription("D-pad remote TV");
    }

    void setOnKey(OnKey k) {
        onKey = k;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        outer = Math.min(w, h) / 2f - dp(10);
        inner = outer * 0.38f;
        fill.setShader(new RadialGradient(cx, cy - outer * 0.3f, outer * 1.2f,
                Color.rgb(30, 33, 74), Color.rgb(10, 11, 28), Shader.TileMode.CLAMP));
        SweepGradient sg = new SweepGradient(cx, cy, neon, null);
        ring.setShader(sg);
        glow.setShader(sg);
        hi.setShader(new RadialGradient(cx, cy, outer, new int[]{Color.argb(0, 139, 92, 255),
                Color.argb(110, 139, 92, 255), Color.argb(150, 230, 36, 143)}, new float[]{0.3f, 0.7f, 1f},
                Shader.TileMode.CLAMP));
        okFill.setShader(new LinearGradient(cx - inner, cy - inner, cx + inner, cy + inner,
                Color.rgb(108, 59, 255), Color.rgb(230, 36, 143), Shader.TileMode.CLAMP));
        oval.set(cx - outer, cy - outer, cx + outer, cy + outer);
        ovalIn.set(cx - inner * 1.12f, cy - inner * 1.12f, cx + inner * 1.12f, cy + inner * 1.12f);
    }

    @Override
    protected void onDraw(Canvas c) {
        float stroke = dp(2.2f);
        // cahaya neon luar
        for (int i = 3; i >= 1; i--) {
            glow.setStrokeWidth(stroke + i * dp(3));
            glow.setAlpha(18 * (4 - i));
            c.drawCircle(cx, cy, outer, glow);
        }
        c.drawCircle(cx, cy, outer, fill);

        // sektor yang ditekan
        if (pressed >= UP && pressed <= LEFT) {
            float start = -135 + pressed * 90;
            path.reset();
            path.arcTo(oval, start, 90);
            path.arcTo(ovalIn, start + 90, -90);
            path.close();
            c.drawPath(path, hi);
        }
        ring.setStrokeWidth(stroke);
        ring.setAlpha(230);
        c.drawCircle(cx, cy, outer, ring);

        // panah
        float ar = (outer + inner) / 2f + dp(4), as = dp(9);
        arrow.setStrokeWidth(dp(3));
        for (int d = UP; d <= LEFT; d++) {
            arrow.setColor(pressed == d ? Color.WHITE : Color.argb(200, 154, 160, 200));
            c.save();
            c.rotate(d * 90, cx, cy);
            float y = cy - ar;
            path.reset();
            path.moveTo(cx - as, y + as * 0.55f);
            path.lineTo(cx, y - as * 0.45f);
            path.lineTo(cx + as, y + as * 0.55f);
            c.drawPath(path, arrow);
            c.restore();
        }

        // tombol OK
        if (pressed == OK) {
            c.drawCircle(cx, cy, inner * 0.96f, okFill);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(20, 22, 50));
            c.drawCircle(cx, cy, inner, p);
            ring.setStrokeWidth(dp(1.6f));
            ring.setAlpha(170);
            c.drawCircle(cx, cy, inner, ring);
        }
        okText.setTextSize(inner * 0.42f);
        c.drawText("OK", cx, cy - (okText.descent() + okText.ascent()) / 2f, okText);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int z = hit(e.getX(), e.getY());
                if (z == NONE) return false;
                pressed = z;
                longSent = false;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (z == OK) {
                    postDelayed(longOk, 600);
                } else {
                    fire(CODES[z], TvRemote.DIR_SHORT);
                    postDelayed(repeat, 420);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(repeat);
                removeCallbacks(longOk);
                if (pressed == OK) {
                    if (longSent) fire(KeyEvent.KEYCODE_DPAD_CENTER, TvRemote.DIR_END_LONG);
                    else if (e.getActionMasked() == MotionEvent.ACTION_UP)
                        fire(KeyEvent.KEYCODE_DPAD_CENTER, TvRemote.DIR_SHORT);
                }
                pressed = NONE;
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }

    private int hit(float x, float y) {
        float dx = x - cx, dy = y - cy;
        double dist = Math.hypot(dx, dy);
        if (dist > outer + dp(8)) return NONE;
        if (dist <= inner * 1.05f) return OK;
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? RIGHT : LEFT;
        return dy > 0 ? DOWN : UP;
    }

    private void fire(int code, int dir) {
        if (onKey != null) onKey.key(code, dir);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(repeat);
        removeCallbacks(longOk);
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
