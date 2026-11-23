package com.aldef.power;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

/**
 * Admin Perangkat ALDEF:
 * - Selfie Penyusup: dipanggil sistem setiap kali PIN/pola layar kunci salah.
 * - (Opsional) Mode Device Owner untuk restart & sembunyikan aplikasi.
 */
public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onPasswordFailed(Context context, Intent intent, UserHandle user) {
        if (Features.intruder(context)) {
            IntruderCaptureActivity.launch(context, IntruderStore.REASON_LOCK);
        }
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Selfie Penyusup akan berhenti bekerja jika admin ALDEF POWER dinonaktifkan.";
    }
}
