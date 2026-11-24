package com.aldef.power;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/**
 * Koneksi kontrol Android TV Remote v2 (port 6466, TLS + protobuf), protokol yang sama
 * dengan aplikasi Google TV. Callback Listener selalu di main thread.
 */
final class TvRemote {

    static final int PORT = 6466;

    static final int STATE_CONNECTING = 1;
    static final int STATE_CONNECTED = 2;
    static final int STATE_DISCONNECTED = 3;
    static final int STATE_NEED_PAIRING = 4;
    static final int STATE_UNREACHABLE = 5;

    static final int DIR_START_LONG = 1;
    static final int DIR_END_LONG = 2;
    static final int DIR_SHORT = 3;

    /** Fitur yang diumumkan ke TV: ping, tombol, IME, power, volume, app link. */
    private static final int FEATURES = 622;

    interface Listener {
        void onState(int state, String detail);

        void onVolume(int level, int max, boolean muted);

        void onPower(boolean on);

        /** TV meminta keyboard (kolom teks di TV dipilih); text = isi kolom saat ini. */
        void onImeRequest(String text);
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    private volatile SSLSocket sock;
    private volatile OutputStream out;
    private volatile boolean connected, closed;
    private volatile int gen;
    private volatile int imeCounter, fieldCounter;
    private volatile String imeText = "";
    private Thread reader;

    TvRemote(Context c, Listener l) {
        this.ctx = c.getApplicationContext();
        this.listener = l;
    }

    boolean isConnected() {
        return connected;
    }

    void connect(String host) {
        disconnect();
        closed = false;
        final int g = ++gen;
        post(STATE_CONNECTING, null, g);
        reader = new Thread(() -> run(host, g), "aldef-tv");
        reader.start();
    }

    void disconnect() {
        closed = true;
        gen++;
        connected = false;
        SSLSocket s = sock;
        sock = null;
        out = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    void release() {
        disconnect();
        writer.shutdownNow();
    }

    void sendKey(int keyCode, int direction) {
        send(new Proto.W().msg(10, new Proto.W().num(1, keyCode).num(2, direction)));
    }

    void launchApp(String link) {
        send(new Proto.W().msg(90, new Proto.W().str(1, link)));
    }

    /** Isi kolom teks yang sedang aktif di TV (remote_ime_batch_edit). */
    void sendText(String text) {
        int cursor = Math.max(0, text.length() - 1);
        Proto.W field = new Proto.W().num(1, cursor).num(2, cursor).str(3, text);
        Proto.W edit = new Proto.W().num(1, 1).msg(2, field);
        send(new Proto.W().msg(21, new Proto.W().num(1, imeCounter).num(2, fieldCounter).msg(3, edit)));
        imeText = text;
    }

    /** Isi kolom teks TV terakhir yang diketahui. */
    String imeText() {
        return imeText;
    }

    // ------------------------------------------------------------ inti

    private void run(String host, int g) {
        boolean authed = false, tcpUp = false;
        try {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(host, PORT), 5000);
            tcpUp = true;
            SSLSocket s = (SSLSocket) TvCert.sslContext(ctx).getSocketFactory().createSocket(raw, host, PORT, true);
            s.setSoTimeout(8000);
            s.startHandshake();
            if (stale(g)) {
                s.close();
                return;
            }
            sock = s;
            out = s.getOutputStream();
            InputStream in = s.getInputStream();
            while (!stale(g)) {
                Proto.M m = Proto.M.parse(Proto.readFrame(in));
                if (!authed) {
                    authed = true;
                    s.setSoTimeout(25000);   // TV mengirim ping berkala
                }
                handle(m, g);
            }
        } catch (Exception e) {
            if (stale(g)) return;
            connected = false;
            if (!tcpUp || e instanceof ConnectException || e instanceof NoRouteToHostException) {
                post(STATE_UNREACHABLE, "TV tidak terjangkau. Pastikan TV menyala & satu Wi-Fi.", g);
            } else if (!authed && (e instanceof SSLException || e instanceof EOFException
                    || e instanceof java.net.SocketException || e instanceof SocketTimeoutException)) {
                // TV memutus jabat tangan: sertifikat kita belum dikenal -> perlu pairing
                post(STATE_NEED_PAIRING, null, g);
            } else {
                post(STATE_DISCONNECTED, e.getMessage(), g);
            }
        } finally {
            if (!stale(g)) connected = false;
        }
    }

    private void handle(Proto.M m, int g) throws IOException {
        if (m.has(1)) {            // remote_configure -> balas dengan info perangkat kita
            Proto.W info = new Proto.W()
                    .str(1, Build.MODEL).str(2, Build.MANUFACTURER)
                    .num(3, 1).str(4, "1").str(5, "com.aldef.power").str(6, "1.0");
            write(new Proto.W().msg(1, new Proto.W().num(1, FEATURES).msg(2, info)));
        } else if (m.has(2)) {     // remote_set_active
            write(new Proto.W().msg(2, new Proto.W().num(1, FEATURES)));
            if (!connected) {
                connected = true;
                post(STATE_CONNECTED, null, g);
            }
        } else if (m.has(8)) {     // ping
            Proto.M ping = m.msg(8);
            write(new Proto.W().msg(9, new Proto.W().num(1, ping == null ? 0 : ping.num(1, 0))));
        } else if (m.has(40)) {    // remote_start: status layar TV
            Proto.M st = m.msg(40);
            boolean on = st != null && st.num(1, 0) != 0;
            main.post(() -> listener.onPower(on));
            if (!connected) {
                connected = true;
                post(STATE_CONNECTED, null, g);
            }
        } else if (m.has(20)) {    // remote_ime_key_inject: info kolom teks aktif
            Proto.M inj = m.msg(20);
            Proto.M st = inj == null ? null : inj.msg(2);
            if (st != null && st.str(2) != null) imeText = st.str(2);
        } else if (m.has(21)) {    // remote_ime_batch_edit dari TV: simpan penghitung
            Proto.M be = m.msg(21);
            if (be != null) {
                imeCounter = (int) be.num(1, imeCounter);
                fieldCounter = (int) be.num(2, fieldCounter);
            }
        } else if (m.has(22)) {    // remote_ime_show_request: TV membuka keyboard
            Proto.M show = m.msg(22);
            Proto.M st = show == null ? null : show.msg(2);
            if (st != null && st.str(2) != null) imeText = st.str(2);
            String t = imeText;
            main.post(() -> {
                if (!stale(g)) listener.onImeRequest(t);
            });
        } else if (m.has(50)) {    // remote_set_volume_level
            Proto.M v = m.msg(50);
            if (v != null) {
                int max = (int) v.num(6, 0), lvl = (int) v.num(7, 0);
                boolean muted = v.num(8, 0) != 0;
                main.post(() -> listener.onVolume(lvl, max, muted));
            }
        }
    }

    private void send(Proto.W msg) {
        if (!connected) return;
        try {
            writer.execute(() -> {
                try {
                    write(msg);
                } catch (IOException e) {
                    if (!closed) {
                        connected = false;
                        post(STATE_DISCONNECTED, e.getMessage(), gen);
                    }
                }
            });
        } catch (Exception ignored) {
            // executor sudah dimatikan
        }
    }

    private void write(Proto.W msg) throws IOException {
        OutputStream o = out;
        if (o == null) throw new IOException("Tidak terhubung");
        synchronized (this) {
            o.write(Proto.frame(msg.toBytes()));
            o.flush();
        }
    }

    private boolean stale(int g) {
        return closed || g != gen;
    }

    private void post(int state, String detail, int g) {
        main.post(() -> {
            if (!stale(g)) listener.onState(state, detail);
        });
    }
}
