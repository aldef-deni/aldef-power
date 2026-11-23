package com.aldef.power;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service aksesibilitas ALDEF POWER:
 * - membuka menu power & mengunci layar (performGlobalAction)
 * - mengetuk menu "Hide apps" realme secara otomatis setelah dibuka dari ALDEF
 * - sinkron Hide apps realme (menyalakan/mematikan & membaca sakelar), HANYA saat
 *   sinkron dimulai dari ALDEF. Di luar itu isi layar tidak dibaca.
 * - menyembunyikan kembali aplikasi yang dibuka dari Ruang Privat (Mode Admin).
 */
public class RebootAccessibilityService extends AccessibilityService {

    static volatile RebootAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable syncStep = this::syncStep;
    private Set<String> launchers;
    private BroadcastReceiver screenOffReceiver;
    private View overlay;
    private FeatureHub features;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        refreshLaunchers();
        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                AppHider.rehideTemporary(c);
            }
        };
        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        features = new FeatureHub(this);
        features.start();
    }

    private void refreshLaunchers() {
        launchers = AppHider.launcherPackages(this);
        launchers.remove("com.android.settings"); // FallbackHome, bukan launcher sungguhan
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        // Auto-tap "Hide apps" di halaman Privasi realme (hanya bila dibuka dari ALDEF)
        if (RealmePrivacy.SETTINGS_PKG.contentEquals(pkg) && RealmePrivacy.consumeAutoTapWindow()) {
            if (tapHideApps()) RealmePrivacy.autoTapDone();
            return;
        }

        // Sinkron Hide apps realme
        if (RealmeSync.job != null && RealmeSync.SAFE_PKG.contentEquals(pkg)) {
            handler.removeCallbacks(syncStep);
            handler.postDelayed(syncStep, 450);
            return;
        }

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || launchers == null) return;
        if (launchers.contains(pkg.toString()) && AppHider.hasTemporary(this)) {
            AppHider.rehideTemporary(this);
        }
    }

    private boolean tapHideApps() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        for (String label : RealmePrivacy.HIDE_APPS_LABELS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                CharSequence t = n.getText();
                if (t == null || !t.toString().trim().equalsIgnoreCase(label)) continue;
                if (click(n)) return true;
            }
        }
        return false;
    }

    // ================================================================ sinkron realme

    private static final class Row {
        final String label;
        final boolean checked;
        final AccessibilityNodeInfo toggle;

        Row(String label, boolean checked, AccessibilityNodeInfo toggle) {
            this.label = label;
            this.checked = checked;
            this.toggle = toggle;
        }
    }

    private void syncStep() {
        RealmeSync.Job j = RealmeSync.job;
        if (j == null) return;
        if (SystemClock.uptimeMillis() > j.deadline) {
            finishSync(j, false);
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !RealmeSync.SAFE_PKG.contentEquals(String.valueOf(root.getPackageName()))) {
            hideOverlay();
            handler.postDelayed(syncStep, 800);
            return;
        }
        List<Row> rows = collectRows(root);
        if (rows.isEmpty()) {
            // Masih memuat, atau realme meminta sandi privasi -> biarkan pengguna mengisi
            hideOverlay();
            handler.postDelayed(syncStep, 800);
            return;
        }
        showOverlay();
        AccessibilityNodeInfo list = findScrollable(root);

        if (j.phase == RealmeSync.PHASE_APPLY) {
            for (Row r : rows) {
                Boolean want = j.ops.get(RealmeSync.norm(r.label));
                if (want == null) continue;
                j.ops.remove(RealmeSync.norm(r.label));
                if (r.checked != want) {
                    if (!click(r.toggle)) j.failed.add(r.label);
                    handler.postDelayed(syncStep, 800);   // daftar bisa berubah urutan
                    return;
                }
            }
            if (!j.ops.isEmpty()) {
                if (list != null && j.scrolls < 60
                        && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    j.scrolls++;
                    handler.postDelayed(syncStep, 500);
                    return;
                }
                j.failed.addAll(j.ops.keySet());
                j.ops.clear();
            }
            j.phase = RealmeSync.PHASE_TOP;
            j.scrolls = 0;
        }

        if (j.phase == RealmeSync.PHASE_TOP) {
            if (list != null && j.scrolls < 60
                    && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                j.scrolls++;
                handler.postDelayed(syncStep, 350);
                return;
            }
            j.phase = RealmeSync.PHASE_READ;
            j.scrolls = 0;
        }

        // PHASE_READ: kumpulkan semua sakelar yang menyala sampai bagian "Aplikasi lain"
        for (Row r : rows) if (r.checked) j.hiddenLabels.add(r.label);
        boolean reachedOthers = false;
        for (String h : RealmeSync.OTHER_HEADERS) {
            List<AccessibilityNodeInfo> n = root.findAccessibilityNodeInfosByText(h);
            if (n != null && !n.isEmpty()) {
                reachedOthers = true;
                break;
            }
        }
        if (!reachedOthers && list != null && j.scrolls < 60
                && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            j.scrolls++;
            handler.postDelayed(syncStep, 500);
            return;
        }
        finishSync(j, true);
    }

    private void finishSync(RealmeSync.Job j, boolean ok) {
        RealmeSync.job = null;
        handler.removeCallbacks(syncStep);
        String msg;
        if (ok) {
            Set<String> pkgs = RealmeSync.toPackages(j);
            AppHider.replaceRealmeHidden(this, pkgs);
            msg = "Sinkron selesai · " + pkgs.size() + " aplikasi tersembunyi";
            if (!j.failed.isEmpty()) msg += " · " + j.failed.size() + " gagal";
        } else {
            msg = "Sinkron dibatalkan (waktu habis)";
        }
        final String toast = msg;
        // Tutup layar Hide apps & Privasi, kembali ke ALDEF
        backToAldef(3, () -> {
            hideOverlay();
            Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
        });
    }

    private void backToAldef(int tries, Runnable done) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root == null ? "" : String.valueOf(root.getPackageName());
        if (tries <= 0 || getPackageName().equals(pkg)) {
            done.run();
            return;
        }
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> backToAldef(tries - 1, done), 450);
    }

    private List<Row> collectRows(AccessibilityNodeInfo root) {
        List<Row> rows = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isCheckable()) {
                String label = rowLabel(n);
                if (label != null) rows.add(new Row(label, n.isChecked(), n));
                continue;
            }
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return rows;
    }

    /** Judul baris = teks pertama di kontainer baris terdekat yang berisi teks. */
    private static String rowLabel(AccessibilityNodeInfo toggle) {
        AccessibilityNodeInfo p = toggle.getParent();
        for (int depth = 0; depth < 4 && p != null; depth++) {
            String t = firstText(p, toggle);
            if (t != null) return t;
            p = p.getParent();
        }
        return null;
    }

    private static String firstText(AccessibilityNodeInfo node, AccessibilityNodeInfo exclude) {
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c == null || c.equals(exclude)) continue;
            CharSequence t = c.getText();
            if (t != null && t.toString().trim().length() > 0) return t.toString().trim();
            String deeper = firstText(c, exclude);
            if (deeper != null) return deeper;
        }
        return null;
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo best = null;
        int bestArea = 0;
        Rect r = new Rect();
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isScrollable()) {
                n.getBoundsInScreen(r);
                int area = r.width() * r.height();
                if (area > bestArea) {
                    bestArea = area;
                    best = n;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return best;
    }

    private static boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo t = n;
        while (t != null && !t.isClickable()) t = t.getParent();
        return t != null && t.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // ---------------------------------------------------------------- tirai

    private void showOverlay() {
        if (overlay != null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            View v = LayoutInflater.from(this).inflate(R.layout.overlay_sync, null);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            wm.addView(v, lp);
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(180).start();
            overlay = v;
        } catch (Exception e) {
            Log.w("AldefSync", "overlay gagal", e);
            overlay = null;
        }
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
        } catch (Exception ignored) {
        }
        overlay = null;
    }

    // ----------------------------------------------------------------

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (features != null) features.stop();
        hideOverlay();
        handler.removeCallbacksAndMessages(null);
        if (screenOffReceiver != null) {
            try {
                unregisterReceiver(screenOffReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
