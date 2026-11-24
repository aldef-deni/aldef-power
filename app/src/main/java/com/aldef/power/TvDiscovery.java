package com.aldef.power;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Deque;

/** Mencari Android TV di Wi-Fi yang sama lewat mDNS (_androidtvremote2._tcp). */
final class TvDiscovery {

    private static final String SERVICE = "_androidtvremote2._tcp";

    interface Listener {
        void onFound(String name, String host);
    }

    private final NsdManager nsd;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Deque<NsdServiceInfo> queue = new ArrayDeque<>();
    private boolean resolving, running;
    private NsdManager.DiscoveryListener discovery;

    TvDiscovery(Context c, Listener l) {
        nsd = (NsdManager) c.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        listener = l;
    }

    void start() {
        if (running || nsd == null) return;
        running = true;
        discovery = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String t, int e) { running = false; }
            @Override public void onStopDiscoveryFailed(String t, int e) {}
            @Override public void onDiscoveryStarted(String t) {}
            @Override public void onDiscoveryStopped(String t) {}
            @Override public void onServiceLost(NsdServiceInfo s) {}

            @Override
            public void onServiceFound(NsdServiceInfo s) {
                main.post(() -> {
                    queue.add(s);
                    next();
                });
            }
        };
        try {
            nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Exception e) {
            running = false;
        }
    }

    void stop() {
        if (!running) return;
        running = false;
        queue.clear();
        try {
            nsd.stopServiceDiscovery(discovery);
        } catch (Exception ignored) {
        }
    }

    /** Android 11 hanya mengizinkan satu resolve sekaligus, jadi diantrikan. */
    @SuppressWarnings("deprecation")
    private void next() {
        if (resolving || queue.isEmpty() || !running) return;
        resolving = true;
        NsdServiceInfo s = queue.poll();
        nsd.resolveService(s, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo si, int err) {
                main.post(() -> {
                    resolving = false;
                    next();
                });
            }

            @Override
            public void onServiceResolved(NsdServiceInfo si) {
                String host = si.getHost() == null ? null : si.getHost().getHostAddress();
                String name = si.getServiceName();
                main.post(() -> {
                    resolving = false;
                    if (running && host != null) listener.onFound(name, host);
                    next();
                });
            }
        });
    }
}
