package com.aldef.power;

import android.content.Context;
import android.content.SharedPreferences;

/** Pengaturan fitur: Flying Ball Shortcut, Incorrect Password Capture, Animate Charging. */
final class Features {

    static final String PREFS = "features";

    static final String KEY_BALL = "ball";
    static final String KEY_BALL_X = "ball_x";
    static final String KEY_BALL_Y = "ball_y";
    static final String KEY_INTRUDER = "intruder";
    static final String KEY_CHARGING = "charging";

    private Features() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean ball(Context c) { return prefs(c).getBoolean(KEY_BALL, false); }
    static boolean intruder(Context c) { return prefs(c).getBoolean(KEY_INTRUDER, false); }
    static boolean charging(Context c) { return prefs(c).getBoolean(KEY_CHARGING, false); }

    static void set(Context c, String key, boolean value) {
        prefs(c).edit().putBoolean(key, value).apply();
    }
}
