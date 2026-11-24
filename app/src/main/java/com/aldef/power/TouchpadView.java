package com.aldef.power;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** Touchpad ala Google TV: geser = arah (bisa beruntun), ketuk = OK, tahan = OK tekan lama. */
public class TouchpadView extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint touch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private DpadView.OnKey onKey;
    private float sx, sy, tx = -1, ty = -1;
    private long downAt, flashUntil;
    private boolean moved, longSent, down;
    private int flashDir = -1;

    private final Runnable longOk = () -> {
        if (down && !moved) {
            longSent = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            fire(KeyEvent.KEYCODE_DPAD_CENTER, TvRemote.DIR_START_LONG);
        }
    };

    public TouchpadView(Context c) {
        this(c, null);
    }

    public TouchpadView(Context c, AttributeSet a) {
        super(c, a);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        dot.setColor(Color.argb(40, 183, 156, 255));
        hint.setColor(Color.argb(150, 154, 160, 200));
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setTextSize(dp(12));
        setHapticFeedbackEnabled(true);
        setContentDescription("Touchpad remote TV");
    }

    void setOnKey(DpadView.OnKey k) {
        onKey = k;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rect.set(dp(4), dp(4), w - dp(4), h - dp(4));
        bg.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(22, 25, 54), Color.rgb(11, 12, 30),
                Shader.TileMode.CLAMP));
        stroke.setShader(new LinearGradient(0, 0, w, h, new int[]{Color.rgb(34, 211, 255),
                Color.rgb(108, 59, 255), Color.rgb(230, 36, 143)}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = dp(28);
        c.drawRoundRect(rect, r, r, bg);
        c.drawRoundRect(rect, r, r, stroke);

        // pola titik halus
        float gap = dp(22);
        for (float x = rect.left + gap; x < rect.right - gap / 2; x += gap) {
            for (float y = rect.top + gap; y < rect.bottom - gap / 2; y += gap) {
                c.drawCircle(x, y, dp(1.1f), dot);
            }
        }
        if (!down) {
            c.drawText("Geser untuk navigasi  ·  Ketuk untuk OK", getWidth() / 2f, getHeight() / 2f, hint);
        }
        if (down && tx >= 0) {
            touch.setShader(new RadialGradient(tx, ty, dp(46), new int[]{Color.argb(150, 183, 156, 255),
                    Color.argb(60, 139, 92, 255), Color.argb(0, 139, 92, 255)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(tx, ty, dp(46), touch);
        }
        // kilatan tepi sesuai arah yang terkirim
        if (flashDir >= 0 && SystemClock.uptimeMillis() < flashUntil) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(140, 183, 156, 255));
            float t = dp(4);
            switch (flashDir) {
                case 0: c.drawRect(rect.left + r, rect.top, rect.right - r, rect.top + t, p); break;
                case 1: c.drawRect(rect.right - t, rect.top + r, rect.right, rect.bottom - r, p); break;
                case 2: c.drawRect(rect.left + r, rect.bottom - t, rect.right - r, rect.bottom, p); break;
                default: c.drawRect(rect.left, rect.top + r, rect.left + t, rect.bottom - r, p); break;
            }
            postInvalidateDelayed(16);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float step = dp(40);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                moved = false;
                longSent = false;
                sx = tx = e.getX();
                sy = ty = e.getY();
                downAt = e.getEventTime();
                postDelayed(longOk, 650);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                tx = e.getX();
                ty = e.getY();
                float dx = tx - sx, dy = ty - sy;
                if (!longSent && (Math.abs(dx) > step || Math.abs(dy) > step)) {
                    int dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
                    int code = dir == 0 ? KeyEvent.KEYCODE_DPAD_UP : dir == 1 ? KeyEvent.KEYCODE_DPAD_RIGHT
                            : dir == 2 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_LEFT;
                    fire(code, TvRemote.DIR_SHORT);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    flashDir = dir;
                    flashUntil = SystemClock.uptimeMillis() + 180;
                    sx = tx;
                    sy = ty;
                    moved = true;
                    removeCallbacks(longOk);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longOk);
                if (longSent) {
                    fire(KeyEvent.KEYCODE_DPAD_CENTER, TvRemote.DIR_END_LONG);
                } else if (!moved && e.getActionMasked() == MotionEvent.ACTION_UP
                        && e.getEventTime() - downAt < 400
                        && Math.hypot(e.getX() - sx, e.getY() - sy) < dp(14)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    fire(KeyEvent.KEYCODE_DPAD_CENTER, TvRemote.DIR_SHORT);
                }
                down = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void fire(int code, int dir) {
        if (onKey != null) onKey.key(code, dir);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(longOk);
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
