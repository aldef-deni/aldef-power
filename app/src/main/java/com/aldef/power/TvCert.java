package com.aldef.power;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Sertifikat klien untuk Android TV Remote: RSA 2048 + X.509 self-signed yang dibuat sendiri
 * (encoder DER mini, tanpa BouncyCastle). TV mengingat sertifikat ini setelah pairing.
 */
final class TvCert {

    private static final String PREFS = "tv_remote";
    private static final String K_KEY = "cert_key";
    private static final String K_CERT = "cert_der";
    private static final String ALIAS = "aldef";

    private static PrivateKey key;
    private static X509Certificate cert;

    private TvCert() {}

    static synchronized void ensure(Context c) throws Exception {
        if (cert != null) return;
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String k = p.getString(K_KEY, null), d = p.getString(K_CERT, null);
        if (k != null && d != null) {
            try {
                key = KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(k, Base64.NO_WRAP)));
                cert = parse(Base64.decode(d, Base64.NO_WRAP));
                return;
            } catch (Exception ignored) {
                // rusak: buat ulang (TV akan meminta pairing lagi)
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048, new SecureRandom());
        KeyPair kp = g.generateKeyPair();
        byte[] der = selfSigned(kp, "ALDEF POWER Remote");
        key = kp.getPrivate();
        cert = parse(der);
        p.edit().putString(K_KEY, Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP))
                .putString(K_CERT, Base64.encodeToString(der, Base64.NO_WRAP)).apply();
    }

    static RSAPublicKey publicKey(Context c) throws Exception {
        ensure(c);
        return (RSAPublicKey) cert.getPublicKey();
    }

    /** SSLContext dengan sertifikat klien kita; sertifikat TV (self-signed) diterima apa adanya. */
    static SSLContext sslContext(Context c) throws Exception {
        ensure(c);
        final PrivateKey pk = key;
        final X509Certificate[] chain = {cert};
        KeyManager km = new X509KeyManager() {
            @Override public String[] getClientAliases(String t, Principal[] i) { return new String[]{ALIAS}; }
            @Override public String chooseClientAlias(String[] t, Principal[] i, Socket s) { return ALIAS; }
            @Override public String[] getServerAliases(String t, Principal[] i) { return null; }
            @Override public String chooseServerAlias(String t, Principal[] i, Socket s) { return null; }
            @Override public X509Certificate[] getCertificateChain(String a) { return chain; }
            @Override public PrivateKey getPrivateKey(String a) { return pk; }
        };
        TrustManager tm = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] ch, String a) {}
            @Override public void checkServerTrusted(X509Certificate[] ch, String a) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[]{km}, new TrustManager[]{tm}, new SecureRandom());
        return ctx;
    }

    private static X509Certificate parse(byte[] der) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    // ------------------------------------------------------------ X.509 DER

    private static final byte[] OID_SHA256_RSA = {0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B};
    private static final byte[] OID_CN = {0x55, 0x04, 0x03};
    private static final byte[] OID_O = {0x55, 0x04, 0x0A};

    private static byte[] selfSigned(KeyPair kp, String cn) throws Exception {
        byte[] algId = seq(tlv(0x06, OID_SHA256_RSA), new byte[]{0x05, 0x00});
        byte[] name = seq(
                tlv(0x31, seq(tlv(0x06, OID_CN), tlv(0x0C, cn.getBytes(StandardCharsets.UTF_8)))),
                tlv(0x31, seq(tlv(0x06, OID_O), tlv(0x0C, "Aldef Tech".getBytes(StandardCharsets.UTF_8)))));
        long now = System.currentTimeMillis();
        byte[] validity = seq(time(now - 86_400_000L), time(now + 20L * 365 * 86_400_000L));
        byte[] serial = tlv(0x02, new BigInteger(62, new SecureRandom()).add(BigInteger.ONE).toByteArray());
        byte[] version = tlv(0xA0, tlv(0x02, new byte[]{2}));
        byte[] tbs = seq(version, serial, algId, name, validity, name, kp.getPublic().getEncoded());

        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        return seq(tbs, algId, tlv(0x03, bits));
    }

    private static byte[] time(long ms) {
        Date d = new Date(ms);
        SimpleDateFormat year = new SimpleDateFormat("yyyy", Locale.US);
        year.setTimeZone(TimeZone.getTimeZone("UTC"));
        boolean utc = Integer.parseInt(year.format(d)) < 2050;
        SimpleDateFormat f = new SimpleDateFormat(utc ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(utc ? 0x17 : 0x18, f.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] seq(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return tlv(0x30, o.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(content.length + 6);
        o.write(tag);
        int len = content.length;
        if (len < 0x80) {
            o.write(len);
        } else if (len < 0x100) {
            o.write(0x81);
            o.write(len);
        } else if (len < 0x10000) {
            o.write(0x82);
            o.write(len >> 8);
            o.write(len & 0xFF);
        } else {
            o.write(0x83);
            o.write(len >> 16);
            o.write((len >> 8) & 0xFF);
            o.write(len & 0xFF);
        }
        o.write(content, 0, len);
        return o.toByteArray();
    }
}
