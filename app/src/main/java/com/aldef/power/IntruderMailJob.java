package com.aldef.power;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;

/** Mengulang pengiriman email Selfie Penyusup saat jaringan tersedia. */
public class IntruderMailJob extends JobService {

    private static final int JOB_ID = 7301;

    static void schedule(Context c) {
        JobScheduler js = IntruderMailer.scheduler(c);
        if (js != null) js.schedule(IntruderMailer.jobInfo(c, JOB_ID));
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            boolean done = IntruderMailer.sendPending(getApplicationContext());
            jobFinished(params, !done);
        }, "aldef-mail-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
