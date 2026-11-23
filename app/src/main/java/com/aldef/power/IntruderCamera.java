package com.aldef.power;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Memotret diam-diam dengan kamera depan (tanpa preview di layar).
 * Kamera dibiarkan "pemanasan" sebentar agar eksposur pas, lalu satu foto diambil.
 */
final class IntruderCamera {

    private static final long WARMUP_MS = 750;
    private static final long TIMEOUT_MS = 5000;

    private final Context app;
    private final String reason;
    private final Runnable done;
    private final Handler main = new Handler(Looper.getMainLooper());

    private HandlerThread thread;
    private Handler bg;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader jpegReader, previewReader;
    private boolean finished;

    IntruderCamera(Context c, String reason, Runnable done) {
        this.app = c.getApplicationContext();
        this.reason = reason;
        this.done = done;
    }

    @SuppressLint("MissingPermission")
    void capture() {
        thread = new HandlerThread("aldef-intruder");
        thread.start();
        bg = new Handler(thread.getLooper());
        bg.postDelayed(this::finish, TIMEOUT_MS);
        try {
            CameraManager cm = (CameraManager) app.getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    id = cid;
                    break;
                }
            }
            if (id == null) {
                finish();
                return;
            }
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            Integer sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            final int jpegOrientation = sensor == null ? 270 : sensor;
            Size size = pickSize(ch);

            jpegReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(r -> {
                try (Image img = r.acquireLatestImage()) {
                    if (img != null) {
                        ByteBuffer buf = img.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buf.remaining()];
                        buf.get(bytes);
                        IntruderStore.save(app, bytes, reason);
                    }
                } catch (Exception ignored) {
                }
                finish();
            }, bg);
            // Target preview kecil yang langsung dibuang (supaya auto-exposure berjalan)
            previewReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3);
            previewReader.setOnImageAvailableListener(r -> {
                Image img = r.acquireLatestImage();
                if (img != null) img.close();
            }, bg);

            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cd) {
                    camera = cd;
                    try {
                        cd.createCaptureSession(Arrays.asList(previewReader.getSurface(), jpegReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession s) {
                                        session = s;
                                        try {
                                            CaptureRequest.Builder pb = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            pb.addTarget(previewReader.getSurface());
                                            pb.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                            s.setRepeatingRequest(pb.build(), null, bg);
                                            bg.postDelayed(() -> shoot(cd, s, jpegOrientation), WARMUP_MS);
                                        } catch (Exception e) {
                                            finish();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(CameraCaptureSession s) {
                                        finish();
                                    }
                                }, bg);
                    } catch (CameraAccessException e) {
                        finish();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cd) {
                    finish();
                }

                @Override
                public void onError(CameraDevice cd, int error) {
                    finish();
                }
            }, bg);
        } catch (Exception e) {
            finish();
        }
    }

    private void shoot(CameraDevice cd, CameraCaptureSession s, int orientation) {
        if (finished) return;
        try {
            CaptureRequest.Builder cb = cd.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            cb.addTarget(jpegReader.getSurface());
            cb.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            cb.set(CaptureRequest.JPEG_QUALITY, (byte) 92);
            s.capture(cb.build(), null, bg);
        } catch (Exception e) {
            finish();
        }
    }

    private static Size pickSize(CameraCharacteristics ch) {
        StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size best = new Size(1280, 960);
        if (map == null) return best;
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        int bestDiff = Integer.MAX_VALUE;
        for (Size s : sizes) {
            int diff = Math.abs(Math.max(s.getWidth(), s.getHeight()) - 1600);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        return best;
    }

    private synchronized void finish() {
        if (finished) return;
        finished = true;
        try { if (session != null) session.close(); } catch (Exception ignored) { }
        try { if (camera != null) camera.close(); } catch (Exception ignored) { }
        try { if (jpegReader != null) jpegReader.close(); } catch (Exception ignored) { }
        try { if (previewReader != null) previewReader.close(); } catch (Exception ignored) { }
        if (thread != null) thread.quitSafely();
        if (done != null) main.post(done);
    }
}
