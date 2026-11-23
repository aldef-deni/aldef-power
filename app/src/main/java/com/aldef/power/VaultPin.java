package com.aldef.power;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Kode Ruang Privat: kode asli membuka brankas sungguhan, kode pengecoh membuka
 * brankas palsu. Disimpan sebagai hash PBKDF2 + salt (kode aslinya tidak pernah disimpan).
 */
final class VaultPin {

    static final int SPACE_REAL = 0;
    static final int SPACE_DECOY = 1;
    static final int NO_MATCH = -1;

    static final int MIN_LEN = 4;
    static final int MAX_LEN = 8;

    private static final String PREFS = "vault_pin";
    private static final int ITERATIONS = 12000;

    private VaultPin() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int space) {
        return space == SPACE_DECOY ? "decoy" : "real";
    }

    static boolean has(Context c, int space) {
        return prefs(c).contains(key(space) + "_hash");
    }

    static void set(Context c, int space, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        prefs(c).edit()
                .putString(key(space) + "_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(key(space) + "_hash", hash(pin, salt))
                .apply();
    }

    static void clear(Context c, int space) {
        prefs(c).edit().remove(key(space) + "_salt").remove(key(space) + "_hash").apply();
    }

    static boolean matches(Context c, int space, String pin) {
        String salt = prefs(c).getString(key(space) + "_salt", null);
        String stored = prefs(c).getString(key(space) + "_hash", null);
        if (salt == null || stored == null) return false;
        String h = hash(pin, Base64.decode(salt, Base64.NO_WRAP));
        return MessageDigest.isEqual(h.getBytes(), stored.getBytes());
    }

    /** SPACE_REAL, SPACE_DECOY, atau NO_MATCH. */
    static int check(Context c, String pin) {
        if (matches(c, SPACE_REAL, pin)) return SPACE_REAL;
        if (matches(c, SPACE_DECOY, pin)) return SPACE_DECOY;
        return NO_MATCH;
    }

    private static String hash(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
