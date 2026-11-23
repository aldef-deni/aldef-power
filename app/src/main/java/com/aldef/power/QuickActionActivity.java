package com.aldef.power;

import android.app.Activity;
import android.os.Bundle;

/** Activity transparan yang dipanggil dari shortcut ikon & tile panel notifikasi. */
public class QuickActionActivity extends Activity {

    static final String ACTION_RESTART = "com.aldef.power.RESTART";
    static final String ACTION_LOCK = "com.aldef.power.LOCK";
    static final String ACTION_POWER = "com.aldef.power.POWER_MENU";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent() != null ? getIntent().getAction() : null;

        if (ACTION_LOCK.equals(action)) {
            if (!RebootHelper.lockScreen(this)) RebootHelper.needAccessibility(this);
            finish();
        } else if (ACTION_POWER.equals(action)) {
            if (!RebootHelper.showPowerDialog()) RebootHelper.needAccessibility(this);
            finish();
        } else {
            RebootHelper.confirmAndRestart(this, this::finish);
        }
    }
}
