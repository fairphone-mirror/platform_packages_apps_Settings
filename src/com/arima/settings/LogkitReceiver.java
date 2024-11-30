/**
 * Copyright (C) 2018 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
//<2018/12/31-kanewang, [8901][FEATURE][COMMON][VENDORAPP][][]Add logkit app with screct code *#*#784654#*#*.
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
        //<2019/2/27-xiaoleiding, [8901][FEATURE][COMMON][SETTINGS][][]Change secret code handler for logkit lite.
        logIntent.setClassName("com.qualcomm.qti.logkit.lite", "com.qualcomm.qti.logkit.lite.cActivity");
        //>2019/2/27-xiaoleiding
        logIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(logIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "qti-logkit app not installed?!", Toast.LENGTH_LONG).show();
        }
    }
}
//>2018/12/31-kanewang
