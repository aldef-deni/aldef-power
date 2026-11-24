package com.aldef.power;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Bola Power Melayang (overlay aksesibilitas, tanpa izin "tampil di atas aplikasi lain").
 * Ketuk = panel volume · Tahan = menu power · Geser cepat = screenshot · Seret pelan = pindahkan.
 */
final class FloatingBall {

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private BallView view;
    private WindowManager.LayoutParams lp;

    FloatingBall(AccessibilityService svc) {
        this.svc = svc;
        this.wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        this.density = svc.getResources().getDisplayMetrics().density;
    }

    boolean isShown() {
        return view != null;
    }

    @SuppressLint("ClickableViewAccessibility")
    void show() {
        if (view != null) return;
        int size = dp(56);
        Point screen = screenSize();
        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Features.prefs(svc).getInt(Features.KEY_BALL_X, screen.x - size - dp(6));
        lp.y = Features.prefs(svc).getInt(Features.KEY_BALL_Y, (int) (screen.y * 0.42f));
        view = new BallView(svc);
        view.setAlpha(0f);
        view.setScaleX(0.4f);
        view.setScaleY(0.4f);
        view.setOnTouchListener(new Touch());
        try {
            wm.addView(view, lp);
            view.animate().alpha(BallView.IDLE_ALPHA).scaleX(1f).scaleY(1f)
                    .setInterpolator(new OvershootInterpolator()).setDuration(320).start();
        } catch (Exception e) {
            view = null;
        }
    }

    void hide() {
        if (view == null) return;
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
        view = null;
    }

    // ------------------------------------------------------------ gestur

    private final class Touch implements View.OnTouchListener {
        private final GestureDetector gd;
        private final int slop = ViewConfiguration.get(svc).getScaledTouchSlop();
        private VelocityTracker vt;
        private float downX, downY;
        private int startX, startY;
        private long downTime;
        private boolean dragging, longPressed;

        Touch() {
            gd = new GestureDetector(svc, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (dragging || longPressed) return false;
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    view.pulse();
                    showVolumePanel();
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (dragging) return;
                    longPressed = true;
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    view.pulse();
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                }
            });
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            gd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    downTime = e.getEventTime();
                    dragging = false;
                    longPressed = false;
                    view.setPressedLook(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && !longPressed && Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        lp.x = startX + (int) dx;
                        lp.y = startY + (int) dy;
                        update();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressedLook(false);
                    if (dragging) {
                        float speed = 0;
                        if (vt != null) {
                            vt.addMovement(e);
                            vt.computeCurrentVelocity(1000);
                            speed = (float) Math.hypot(vt.getXVelocity(), vt.getYVelocity());
                        }
                        long dt = e.getEventTime() - downTime;
                        float dist = (float) Math.hypot(e.getRawX() - downX, e.getRawY() - downY);
                        if (dt < 350 && speed > 900 * density && dist > dp(40)) {
                            takeScreenshot(startX, startY);
                        } else {
                            snapToEdge();
                        }
                    }
                    if (vt != null) {
                        vt.recycle();
                        vt = null;
                    }
                    dragging = false;
                    break;
                default:
                    break;
            }
            return true;
        }
    }

    /** Tampilkan panel volume Android (slider bisa digeser untuk besar/kecilkan suara). */
    private void showVolumePanel() {
        AudioManager am = (AudioManager) svc.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI);
        } catch (SecurityException ignored) {
            // Mode Jangan Ganggu dapat menolak perubahan; panel tetap tidak memaksa
        }
    }

    private void takeScreenshot(int backX, int backY) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        lp.x = backX;
        lp.y = backY;
        update();
        view.setVisibility(View.INVISIBLE);
        handler.postDelayed(() ->
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT), 220);
        handler.postDelayed(() -> {
            if (view != null) view.setVisibility(View.VISIBLE);
        }, 1600);
    }

    private void snapToEdge() {
        Point screen = screenSize();
        int size = lp.width;
        int targetX = (lp.x + size / 2) < screen.x / 2 ? dp(6) : screen.x - size - dp(6);
        int targetY = Math.max(dp(40), Math.min(lp.y, screen.y - size - dp(60)));
        int fromX = lp.x, fromY = lp.y;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(260);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            lp.x = (int) (fromX + (targetX - fromX) * f);
            lp.y = (int) (fromY + (targetY - fromY) * f);
            update();
        });
        va.start();
        Features.prefs(svc).edit().putInt(Features.KEY_BALL_X, targetX).putInt(Features.KEY_BALL_Y, targetY).apply();
    }

    private void update() {
        if (view == null) return;
        try {
            wm.updateViewLayout(view, lp);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private Point screenSize() {
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    // ------------------------------------------------------------ tampilan bola

    static final class BallView extends View {
        /** Transparansi saat diam (50%); saat disentuh solid, lalu kembali setelah 3 detik. */
        static final float IDLE_ALPHA = 0.5f;
        private static final long SOLID_HOLD_MS = 3000;

        private final Runnable fadeBack = () ->
                animate().alpha(IDLE_ALPHA).setDuration(450).start();

        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int[] colors = {
                Color.rgb(34, 211, 255), Color.rgb(108, 59, 255), Color.rgb(230, 36, 143),
                Color.rgb(255, 122, 24), Color.rgb(34, 211, 255)};

        BallView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            glow.setStyle(Paint.Style.STROKE);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setColor(Color.WHITE);
        }

        void setPressedLook(boolean pressed) {
            removeCallbacks(fadeBack);
            if (pressed) {
                // langsung solid begitu disentuh
                animate().scaleX(0.9f).scaleY(0.9f).alpha(1f).setDuration(120).start();
            } else {
                // tetap solid 3 detik setelah dilepas, lalu transparan lagi
                animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                postDelayed(fadeBack, SOLID_HOLD_MS);
            }
        }

        void pulse() {
            animate().scaleX(1.15f).scaleY(1.15f).setDuration(110)
                    .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cx = w / 2f, cy = h / 2f;
            SweepGradient sg = new SweepGradient(cx, cy, colors, null);
            ring.setShader(sg);
            glow.setShader(sg);
            core.setShader(new RadialGradient(cx, cy * 0.8f, w * 0.5f,
                    Color.rgb(32, 30, 72), Color.rgb(7, 8, 21), Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2f, cy = getHeight() / 2f;
            float stroke = w * 0.075f;
            float r = w / 2f - stroke * 2.2f;

            // cahaya luar
            for (int i = 3; i >= 1; i--) {
                glow.setStrokeWidth(stroke + i * stroke * 0.9f);
                glow.setAlpha(26 * (4 - i));
                c.drawCircle(cx, cy, r, glow);
            }
            c.drawCircle(cx, cy, r, core);
            ring.setStrokeWidth(stroke);
            c.drawCircle(cx, cy, r, ring);

            // ikon power
            float ir = r * 0.45f;
            icon.setStrokeWidth(stroke * 0.85f);
            rect.set(cx - ir, cy - ir, cx + ir, cy + ir);
            c.drawArc(rect, -60, 300, false, icon);
            c.drawLine(cx, cy - ir * 1.15f, cx, cy - ir * 0.15f, icon);
        }
    }
}
