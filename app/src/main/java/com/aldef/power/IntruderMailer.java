package com.aldef.power;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mengirim foto Selfie Penyusup ke email secara otomatis.
 * Langsung dicoba saat foto tersimpan; bila gagal (mis. tidak ada internet) diantrikan
 * dan diulang oleh {@link IntruderMailJob} begitu jaringan tersedia.
 */
final class IntruderMailer {

    static final String DEFAULT_TO = "deniafrizal2904@gmail.com";

    private static final String PREFS = "intruder_mail";
    private static final String K_ON = "on";
    private static final String K_USER = "user";
    private static final String K_PASS = "pass";
    private static final String K_TO = "to";
    private static final String K_PENDING = "pending";
    private static final String K_STATUS = "status";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    interface Result {
        void done(String error);   // null = berhasil
    }

    private IntruderMailer() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ON, false) && configured(c);
    }

    static boolean configured(Context c) {
        SharedPreferences p = prefs(c);
        return p.getString(K_USER, null) != null && p.getString(K_PASS, null) != null;
    }

    static String user(Context c) { return prefs(c).getString(K_USER, ""); }
    static String to(Context c) { return prefs(c).getString(K_TO, DEFAULT_TO); }
    static String status(Context c) { return prefs(c).getString(K_STATUS, null); }
    static int pendingCount(Context c) { return prefs(c).getStringSet(K_PENDING, Collections.emptySet()).size(); }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_ON, on).apply();
    }

    static boolean save(Context c, String user, String pass, String to) {
        SharedPreferences.Editor e = prefs(c).edit().putString(K_USER, user).putString(K_TO, to);
        if (pass != null) {
            String sealed = SecretBox.seal(pass);
            if (sealed == null) return false;
            e.putString(K_PASS, sealed);
        }
        e.apply();
        return true;
    }

    static String password(Context c) {
        return SecretBox.open(prefs(c).getString(K_PASS, null));
    }

    static boolean hasPassword(Context c) {
        return prefs(c).getString(K_PASS, null) != null;
    }

    // ------------------------------------------------------------ alur otomatis

    /** Dipanggil setiap foto penyusup tersimpan. */
    static void onPhotoSaved(Context c, File photo) {
        if (photo == null || !enabled(c)) return;
        Context app = c.getApplicationContext();
        synchronized (IntruderMailer.class) {
            Set<String> pend = new HashSet<>(prefs(app).getStringSet(K_PENDING, Collections.emptySet()));
            pend.add(photo.getName());
            prefs(app).edit().putStringSet(K_PENDING, pend).commit();
        }
        flush(app);
    }

    static void flush(Context c) {
        Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            if (!sendPending(app)) IntruderMailJob.schedule(app);
        });
    }

    /** @return true jika antrean kosong (semua terkirim). Memblokir; panggil di thread latar. */
    static synchronized boolean sendPending(Context c) {
        if (!enabled(c)) return true;
        String user = user(c), pass = SecretBox.open(prefs(c).getString(K_PASS, null)), to = to(c);
        if (pass == null) {
            setStatus(c, "Gagal: sandi aplikasi tidak terbaca, simpan ulang pengaturan email");
            return true;
        }
        List<String> names = new ArrayList<>(prefs(c).getStringSet(K_PENDING, Collections.emptySet()));
        Collections.sort(names);
        for (String n : names) {
            File f = new File(IntruderStore.dir(c), n);
            if (f.exists()) {
                try {
                    SmtpMailer.send(user, pass, to, subject(f), html(c, f), f);
                    setStatus(c, "Terkirim " + IntruderStore.timeText(System.currentTimeMillis()));
                } catch (SmtpMailer.AuthException e) {
                    setStatus(c, "Gagal: " + e.getMessage());
                    return true;            // percuma diulang sebelum pengaturan diperbaiki
                } catch (IOException e) {
                    setStatus(c, "Menunggu internet untuk mengirim (" + names.size() + " foto)");
                    return false;
                }
            }
            removePending(c, n);
        }
        return true;
    }

    private static synchronized void removePending(Context c, String name) {
        Set<String> pend = new HashSet<>(prefs(c).getStringSet(K_PENDING, Collections.emptySet()));
        pend.remove(name);
        prefs(c).edit().putStringSet(K_PENDING, pend).commit();
    }

    private static void setStatus(Context c, String s) {
        prefs(c).edit().putString(K_STATUS, s).apply();
    }

    /** Kirim email uji dengan pengaturan yang sedang diisi (belum tentu tersimpan). */
    static void sendTest(Context c, String user, String pass, String to, Result r) {
        Context app = c.getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        EXEC.execute(() -> {
            String err = null;
            try {
                List<File> photos = IntruderStore.list(app);
                File last = photos.isEmpty() ? null : photos.get(0);
                SmtpMailer.send(user, pass, to, "✅ ALDEF POWER · Email uji Selfie Penyusup",
                        testHtml(last != null), last);
            } catch (IOException e) {
                err = e.getMessage() == null ? "Tidak dapat terhubung ke server email" : e.getMessage();
            }
            String fe = err;
            main.post(() -> r.done(fe));
        });
    }

    // ------------------------------------------------------------ isi email

    private static String subject(File f) {
        return "⚠️ Percobaan buka HP gagal · " + IntruderStore.timeText(f.lastModified());
    }

    private static String html(Context c, File f) {
        String when = IntruderStore.timeText(f.lastModified());
        String device = Build.MANUFACTURER + " " + Build.MODEL;
        int battery = -1;
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (l >= 0 && s > 0) battery = Math.round(l * 100f / s);
            }
        } catch (Exception ignored) {
        }
        return frame("PERCOBAAN AKSES GAGAL", "#FF4D6D", IntruderStore.reasonText(f),
                row("Waktu", when)
                        + row("Perangkat", device)
                        + (battery >= 0 ? row("Baterai", battery + "%") : "")
                        + row("Foto", f.getName()),
                "<img src=\"cid:intruder\" alt=\"Foto penyusup\" style=\"width:100%;max-width:520px;border-radius:14px;display:block;margin:18px 0 4px\">");
    }

    private static String testHtml(boolean withPhoto) {
        return frame("EMAIL UJI", "#2EE59D", "Pengiriman email berhasil",
                row("Status", "Selfie Penyusup akan dikirim otomatis ke alamat ini")
                        + row("Perangkat", Build.MANUFACTURER + " " + Build.MODEL),
                withPhoto ? "<p style=\"color:#9AA0C8;font-size:13px;margin:18px 0 6px\">Contoh foto terakhir:</p>"
                        + "<img src=\"cid:intruder\" style=\"width:100%;max-width:520px;border-radius:14px;display:block\">" : "");
    }

    private static String row(String k, String v) {
        return "<tr><td style=\"color:#7C82B0;padding:6px 16px 6px 0;font-size:13px;white-space:nowrap\">" + k
                + "</td><td style=\"color:#F4F5FF;padding:6px 0;font-size:14px\">" + esc(v) + "</td></tr>";
    }

    private static String frame(String tag, String tagColor, String title, String rows, String extra) {
        return "<!doctype html><html><body style=\"margin:0;background:#070815;font-family:Roboto,Arial,sans-serif\">"
                + "<div style=\"max-width:560px;margin:0 auto;padding:24px 16px\">"
                + "<div style=\"background:#14173A;border:1px solid #262A52;border-radius:20px;padding:22px\">"
                + "<div style=\"color:" + tagColor + ";font-size:12px;font-weight:bold;letter-spacing:2px\">" + tag + "</div>"
                + "<div style=\"color:#FFFFFF;font-size:22px;margin:8px 0 14px\">" + esc(title) + "</div>"
                + "<table style=\"border-collapse:collapse\">" + rows + "</table>" + extra
                + "</div><div style=\"color:#5C6290;font-size:11px;letter-spacing:3px;text-align:center;margin-top:16px\">"
                + "ALDEF POWER · DIKIRIM OTOMATIS</div></div></body></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------ jadwal ulang

    static ComponentName jobComponent(Context c) {
        return new ComponentName(c, IntruderMailJob.class);
    }

    static JobInfo jobInfo(Context c, int id) {
        return new JobInfo.Builder(id, jobComponent(c))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
    }

    static JobScheduler scheduler(Context c) {
        return (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }
}
