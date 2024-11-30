package com.android.settings;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.os.ServiceManager;
import com.android.internal.statusbar.IStatusBarService;
import android.os.RemoteException;
import com.android.settings.R;
import android.util.Log;
import android.provider.Settings.Secure;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

public class ShutdownJobService extends JobService {
    private static final String TAG = ShutdownJobService.class.getSimpleName();

    static final long SHUT_DOWN_MS = 30 * 60 * 1000;

    public static void startShutdownJob(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        final ComponentName component = new ComponentName(context, ShutdownJobService.class);
        final JobInfo.Builder jobBuilder =
                new JobInfo.Builder(R.integer.job_shut_down, component)
                        .setMinimumLatency(SHUT_DOWN_MS)
                        .setOverrideDeadline(SHUT_DOWN_MS);
        final JobInfo pending = jobScheduler.getPendingJob(R.integer.job_shut_down);
        Log.d(TAG,"startShutdownJob");

        // Don't schedule it if it already exists, to make sure it runs periodically even after
        // reboot
        if (pending == null && jobScheduler.schedule(jobBuilder.build())
                != JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "Shutdown job service schedule failed.");
        }
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        if (!isUserSetupCompleted() && !isSimReady() && !isWifiConnected()) {
            Log.d(TAG,"shutdown device as sim not ready ,wifi not connect, setup wizard not complete");
            shutdown();
        }
        Log.d(TAG,"ShutdownJobService finish");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }

    private void shutdown(){
        IStatusBarService mBarService = IStatusBarService.Stub.asInterface(
            ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        try {
            mBarService.shutdown();
        } catch (RemoteException e) {
        }
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private boolean isSimReady() {
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    private boolean isUserSetupCompleted() {
        return Secure.getInt(getApplicationContext().getContentResolver(), USER_SETUP_COMPLETE, 0) != 0;
    }
}
