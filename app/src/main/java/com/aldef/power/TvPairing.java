package com.aldef.power;

import android.content.Context;
import android.os.Build;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

import javax.net.ssl.SSLSocket;

/**
 * Pairing Android TV Remote v2 (port 6467): TV menampilkan kode 6 karakter heksadesimal,
 * lalu rahasia = SHA-256(modulus & eksponen klien + modulus & eksponen TV + kode[2..]).
 * Dijalankan di thread latar (semua method memblokir).
 */
final class TvPairing implements Closeable {

    static final int PORT = 6467;

    private static final int STATUS_OK = 200;
    private static final int ENC_HEX = 3;
    private static final int ROLE_INPUT = 1;

    private final Context ctx;
    private final String host;
    private SSLSocket sock;
    private InputStream in;
    private OutputStream out;

    TvPairing(Context c, String host) {
        this.ctx = c.getApplicationContext();
        this.host = host;
    }

    /** Sampai TV menampilkan kode di layar. */
    void start() throws Exception {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, PORT), 6000);
        sock = (SSLSocket) TvCert.sslContext(ctx).getSocketFactory().createSocket(raw, host, PORT, true);
        sock.setSoTimeout(15000);
        sock.startHandshake();
        in = sock.getInputStream();
        out = sock.getOutputStream();

        send(10, new Proto.W().str(1, "com.aldef.power").str(2, "ALDEF POWER · " + Build.MODEL));
        expect(11);
        Proto.W enc = new Proto.W().num(1, ENC_HEX).num(2, 6);
        send(20, new Proto.W().msg(1, enc).num(3, ROLE_INPUT));
        expect(20);
        send(30, new Proto.W().msg(1, new Proto.W().num(1, ENC_HEX).num(2, 6)).num(2, ROLE_INPUT));
        expect(31);
        sock.setSoTimeout(0);   // tunggu pengguna mengetik kode
    }

    /**
     * @return true jika berhasil; false jika kode tidak cocok (TV masih menampilkan kode, boleh dicoba lagi).
     */
    boolean finish(String code) throws Exception {
        code = code.trim().toUpperCase();
        if (!code.matches("[0-9A-F]{6}")) return false;
        Certificate[] peer = sock.getSession().getPeerCertificates();
        RSAPublicKey server = (RSAPublicKey) peer[0].getPublicKey();
        RSAPublicKey client = TvCert.publicKey(ctx);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(unsigned(client.getModulus()));
        md.update(unsigned(client.getPublicExponent()));
        md.update(unsigned(server.getModulus()));
        md.update(unsigned(server.getPublicExponent()));
        md.update(hex(code.substring(2)));
        byte[] secret = md.digest();
        if ((secret[0] & 0xFF) != Integer.parseInt(code.substring(0, 2), 16)) return false;

        sock.setSoTimeout(15000);
        send(40, new Proto.W().bytes(1, secret));
        expect(41);
        return true;
    }

    private void send(int field, Proto.W inner) throws IOException {
        byte[] msg = new Proto.W().num(1, 2).num(2, STATUS_OK).msg(field, inner).toBytes();
        out.write(Proto.frame(msg));
        out.flush();
    }

    private Proto.M expect(int field) throws IOException {
        Proto.M m = Proto.M.parse(Proto.readFrame(in));
        long status = m.num(2, 0);
        if (status != STATUS_OK) {
            throw new IOException(status == 402 ? "Kode salah" : "TV menolak pairing (status " + status + ")");
        }
        if (!m.has(field)) throw new IOException("Balasan TV tidak dikenal");
        return m;
    }

    private static byte[] unsigned(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            byte[] t = new byte[b.length - 1];
            System.arraycopy(b, 1, t, 0, t.length);
            return t;
        }
        return b;
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    @Override
    public void close() {
        try {
            if (sock != null) sock.close();
        } catch (IOException ignored) {
        }
    }
}
