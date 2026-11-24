package com.aldef.power;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Klien SMTP mini (SMTPS port 465, TLS + AUTH PLAIN) untuk Gmail, tanpa library tambahan.
 * Login memakai "Sandi Aplikasi" Google (16 karakter), bukan sandi akun biasa.
 */
final class SmtpMailer {

    static final String HOST = "smtp.gmail.com";
    static final int PORT = 465;

    /** Login ditolak (sandi aplikasi / akun salah). */
    static final class AuthException extends IOException {
        AuthException(String m) {
            super(m);
        }
    }

    private BufferedReader in;
    private OutputStream out;

    private SmtpMailer() {}

    static void send(String user, String pass, String to, String subject, String html, File image)
            throws IOException {
        new SmtpMailer().run(user, pass, to, subject, html, image);
    }

    private void run(String user, String pass, String to, String subject, String html, File image)
            throws IOException {
        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        try {
            s.connect(new InetSocketAddress(HOST, PORT), 15000);
            s.setSoTimeout(30000);
            s.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(HOST, s.getSession())) {
                throw new IOException("Sertifikat server email tidak valid");
            }
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = s.getOutputStream();

            expect(220);
            cmd("EHLO aldef-power", 250);
            String auth = Base64.encodeToString(("\0" + user + "\0" + pass).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
            int code = cmdCode("AUTH PLAIN " + auth);
            if (code == 535 || code == 534) {
                throw new AuthException("Login Gmail ditolak. Periksa alamat Gmail & Sandi Aplikasi.");
            }
            if (code != 235) throw new IOException("Login gagal (SMTP " + code + ")");
            cmd("MAIL FROM:<" + user + ">", 250);
            int rc = cmdCode("RCPT TO:<" + to + ">");
            if (rc != 250 && rc != 251) throw new IOException("Alamat tujuan ditolak (SMTP " + rc + ")");
            cmd("DATA", 354);
            out.write(message(user, to, subject, html, image));
            out.write(".\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            expect(250);
            try {
                cmdCode("QUIT");
            } catch (IOException ignored) {
            }
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static byte[] message(String from, String to, String subject, String html, File image)
            throws IOException {
        String b = "aldef_" + UUID.randomUUID().toString().replace("-", "");
        StringBuilder m = new StringBuilder();
        m.append("From: ALDEF POWER <").append(from).append(">\r\n");
        m.append("To: <").append(to).append(">\r\n");
        m.append("Subject: =?UTF-8?B?")
                .append(Base64.encodeToString(subject.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP))
                .append("?=\r\n");
        m.append("Date: ").append(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                .format(new Date())).append("\r\n");
        m.append("Message-ID: <").append(UUID.randomUUID()).append("@aldef.power>\r\n");
        m.append("X-Priority: 1\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: multipart/related; boundary=\"").append(b).append("\"\r\n\r\n");

        m.append("--").append(b).append("\r\n");
        m.append("Content-Type: text/html; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        m.append(Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.CRLF));

        if (image != null && image.exists()) {
            String fn = image.getName();
            m.append("--").append(b).append("\r\n");
            m.append("Content-Type: image/jpeg; name=\"").append(fn).append("\"\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n");
            m.append("Content-ID: <intruder>\r\n");
            m.append("Content-Disposition: inline; filename=\"").append(fn).append("\"\r\n\r\n");
            m.append(Base64.encodeToString(readAll(image), Base64.CRLF));
        }
        m.append("--").append(b).append("--\r\n");
        return m.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private void cmd(String line, int want) throws IOException {
        int code = cmdCode(line);
        if (code != want) throw new IOException("Server email menolak (SMTP " + code + ")");
    }

    private int cmdCode(String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        return read();
    }

    private void expect(int want) throws IOException {
        int code = read();
        if (code != want) throw new IOException("Server email menolak (SMTP " + code + ")");
    }

    /** Baca balasan (bisa multi-baris "250-..."), kembalikan kodenya. */
    private int read() throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) throw new IOException("Koneksi email terputus");
        } while (line.length() >= 4 && line.charAt(3) == '-');
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (Exception e) {
            throw new IOException("Balasan server email tidak valid");
        }
    }
}
