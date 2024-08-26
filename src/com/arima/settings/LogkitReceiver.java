/**
 * Copyright (C) 2018 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
package com.arima.settings;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class LogkitReceiver extends BroadcastReceiver {
    private final static String TAG = "LogkitReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive secret code.");

        Intent logIntent = new Intent();
        logIntent.setClassName("com.qualcomm.qti.logkit.lite", "com.qualcomm.qti.logkit.lite.cActivity");
        logIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(logIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "qti-logkit app not installed?!", Toast.LENGTH_LONG).show();
        }
    }
}
