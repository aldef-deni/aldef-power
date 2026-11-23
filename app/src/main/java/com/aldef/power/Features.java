package com.aldef.power;

import android.content.Context;
import android.content.SharedPreferences;

/** Pengaturan Fitur Ekstra (ketuk punggung, bola power, selfie penyusup, animasi cas). */
final class Features {

    static final String PREFS = "features";

    static final String KEY_BACK_TAP = "back_tap";
    static final String KEY_BACK_TAP_SENS = "back_tap_sens";
    static final String KEY_BALL = "ball";
    static final String KEY_BALL_X = "ball_x";
    static final String KEY_BALL_Y = "ball_y";
    static final String KEY_INTRUDER = "intruder";
    static final String KEY_CHARGING = "charging";

    static final int SENS_LOW = 0;
    static final int SENS_MEDIUM = 1;
    static final int SENS_HIGH = 2;

    /** Aplikasi yang dibuka dengan ketuk punggung 4x. */
    static final String ALDEF_SYSTEM_PKG = "com.aldef.system";

    private Features() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean backTap(Context c) { return prefs(c).getBoolean(KEY_BACK_TAP, false); }
    static int backTapSensitivity(Context c) { return prefs(c).getInt(KEY_BACK_TAP_SENS, SENS_MEDIUM); }
    static boolean ball(Context c) { return prefs(c).getBoolean(KEY_BALL, false); }
    static boolean intruder(Context c) { return prefs(c).getBoolean(KEY_INTRUDER, false); }
    static boolean charging(Context c) { return prefs(c).getBoolean(KEY_CHARGING, false); }

    static void set(Context c, String key, boolean value) {
        prefs(c).edit().putBoolean(key, value).apply();
    }

    static void setInt(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }
}
