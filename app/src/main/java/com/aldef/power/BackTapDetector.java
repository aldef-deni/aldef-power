package com.aldef.power;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Mendeteksi ketukan di punggung HP dengan akselerometer.
 *
 * Ketukan di punggung mendorong HP ke arah depan (sumbu +Z) dengan lonjakan yang sangat
 * tajam (< 10 ms). Sentuhan di layar mendorong ke arah sebaliknya (-Z), sedangkan langkah
 * kaki / goyangan naik perlahan. Jadi yang dihitung hanya lonjakan +Z yang tajam & dominan.
 */
final class BackTapDetector implements SensorEventListener {

    interface Listener {
        /** Rangkaian ketukan selesai (dipanggil setelah jeda). */
        void onTaps(int count);

        /** Setiap satu ketukan terdeteksi (untuk tampilan uji). */
        default void onTap(int soFar) {}
    }

    /** true saat layar uji di Fitur Ekstra terbuka (aksi asli ditahan). */
    static volatile boolean testing;

    private static final long MIN_GAP_NS = 80_000_000L;    // jarak minimum antar ketukan
    private static final long MAX_GAP_NS = 500_000_000L;   // jarak maksimum dalam satu rangkaian
    private static final long SETTLE_MS = 520;             // jeda sebelum rangkaian dianggap selesai
    private static final int SAMPLE_US = 5000;             // ~200 Hz

    private final SensorManager sm;
    private final Sensor accel;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float gx, gy, gz;
    private final float[] histZ = new float[4];
    private int histPos;
    private boolean warm;
    private int warmSamples;
    private long lastTapNs;
    private int count;
    private float threshold;

    private final Runnable finish;

    BackTapDetector(Context c, int sensitivity, Listener l) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        accel = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        listener = l;
        finish = () -> {
            int n = count;
            count = 0;
            if (n > 0) listener.onTaps(n);
        };
        setSensitivity(sensitivity);
    }

    void setSensitivity(int s) {
        threshold = s == Features.SENS_HIGH ? 1.5f : s == Features.SENS_LOW ? 3.6f : 2.4f;
    }

    boolean start() {
        if (accel == null) return false;
        warm = false;
        warmSamples = 0;
        return sm.registerListener(this, accel, SAMPLE_US, handler);
    }

    void stop() {
        if (sm != null) sm.unregisterListener(this);
        handler.removeCallbacks(finish);
        count = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float ax = e.values[0], ay = e.values[1], az = e.values[2];
        if (!warm) {
            gx = ax; gy = ay; gz = az;
            if (++warmSamples > 20) warm = true;
            return;
        }
        // Pisahkan gravitasi (low-pass) dari gerakan cepat (high-pass)
        final float a = 0.96f;
        gx = a * gx + (1 - a) * ax;
        gy = a * gy + (1 - a) * ay;
        gz = a * gz + (1 - a) * az;
        float lx = ax - gx, ly = ay - gy, lz = az - gz;

        // Lonjakan tajam: kenaikan besar dibanding sampel 10-20 ms sebelumnya
        float minPrev = Math.min(Math.min(histZ[0], histZ[1]), Math.min(histZ[2], histZ[3]));
        histZ[histPos] = lz;
        histPos = (histPos + 1) % histZ.length;
        float rise = lz - minPrev;

        boolean spike = lz > threshold
                && rise > threshold * 0.8f
                && lz > 1.7f * Math.abs(lx)
                && lz > 1.7f * Math.abs(ly);
        if (!spike) return;

        long t = e.timestamp;
        if (t - lastTapNs < MIN_GAP_NS) return;
        if (count > 0 && t - lastTapNs > MAX_GAP_NS) count = 0;
        lastTapNs = t;
        count++;
        listener.onTap(count);
        handler.removeCallbacks(finish);
        handler.postDelayed(finish, SETTLE_MS);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
