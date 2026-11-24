package com.aldef.power;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Protobuf mini (tanpa library) untuk protokol Android TV Remote v2.
 * Cukup untuk varint, string/bytes, dan pesan bersarang; tiap pesan dibingkai prefiks panjang varint.
 */
final class Proto {

    private Proto() {}

    // ------------------------------------------------------------ penulis

    static final class W {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        W num(int field, long v) {
            tag(field, 0);
            raw(out, v);
            return this;
        }

        W bytes(int field, byte[] b) {
            tag(field, 2);
            raw(out, b.length);
            out.write(b, 0, b.length);
            return this;
        }

        W str(int field, String s) {
            return bytes(field, s.getBytes(StandardCharsets.UTF_8));
        }

        W msg(int field, W m) {
            return bytes(field, m.toBytes());
        }

        byte[] toBytes() {
            return out.toByteArray();
        }

        private void tag(int field, int wire) {
            raw(out, ((long) field << 3) | wire);
        }
    }

    private static void raw(ByteArrayOutputStream out, long v) {
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    // ------------------------------------------------------------ pembaca

    static final class M {
        private final Map<Integer, List<Object>> f = new HashMap<>();

        static M parse(byte[] b) throws IOException {
            M m = new M();
            int[] p = {0};
            while (p[0] < b.length) {
                long key = varint(b, p);
                int field = (int) (key >>> 3), wire = (int) (key & 7);
                Object val;
                switch (wire) {
                    case 0:
                        val = varint(b, p);
                        break;
                    case 1:
                        p[0] += 8;
                        continue;
                    case 2: {
                        int len = (int) varint(b, p);
                        if (len < 0 || p[0] + len > b.length) throw new IOException("protobuf rusak");
                        byte[] v = new byte[len];
                        System.arraycopy(b, p[0], v, 0, len);
                        p[0] += len;
                        val = v;
                        break;
                    }
                    case 5:
                        p[0] += 4;
                        continue;
                    default:
                        throw new IOException("wire type " + wire);
                }
                List<Object> list = m.f.get(field);
                if (list == null) m.f.put(field, list = new ArrayList<>());
                list.add(val);
            }
            return m;
        }

        boolean has(int field) {
            return f.containsKey(field);
        }

        long num(int field, long def) {
            List<Object> l = f.get(field);
            return l != null && l.get(0) instanceof Long ? (Long) l.get(0) : def;
        }

        byte[] bytes(int field) {
            List<Object> l = f.get(field);
            return l != null && l.get(0) instanceof byte[] ? (byte[]) l.get(0) : null;
        }

        String str(int field) {
            byte[] b = bytes(field);
            return b == null ? null : new String(b, StandardCharsets.UTF_8);
        }

        M msg(int field) throws IOException {
            byte[] b = bytes(field);
            return b == null ? null : parse(b);
        }
    }

    private static long varint(byte[] b, int[] p) throws IOException {
        long r = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (p[0] >= b.length) throw new IOException("varint terpotong");
            int x = b[p[0]++] & 0xFF;
            r |= (long) (x & 0x7F) << shift;
            if ((x & 0x80) == 0) return r;
        }
        throw new IOException("varint terlalu panjang");
    }

    // ------------------------------------------------------------ bingkai

    static byte[] frame(byte[] msg) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(msg.length + 4);
        raw(out, msg.length);
        out.write(msg, 0, msg.length);
        return out.toByteArray();
    }

    static byte[] readFrame(InputStream in) throws IOException {
        long len = 0;
        for (int shift = 0; ; shift += 7) {
            int x = in.read();
            if (x < 0) throw new EOFException("koneksi ditutup TV");
            len |= (long) (x & 0x7F) << shift;
            if ((x & 0x80) == 0) break;
            if (shift > 28) throw new IOException("panjang pesan tidak valid");
        }
        if (len > 1 << 20) throw new IOException("pesan terlalu besar");
        byte[] b = new byte[(int) len];
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new EOFException("koneksi ditutup TV");
            off += n;
        }
        return b;
    }
}
