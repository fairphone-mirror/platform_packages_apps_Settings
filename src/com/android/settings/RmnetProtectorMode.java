/*
 * Copyright (c) 2023 T2M Limited. All rights reserved.
 * Feature added by shaopan.tang 2023-11-28 for FP5U-38 Disable or enable rmnet port by the following hidden code
 */
package com.android.settings;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Broadcaster;
import android.os.SystemProperties;
import android.util.Log;
import android.view.WindowManager;

import static com.android.internal.telephony.TelephonyIntents.SECRET_CODE_ACTION;

public class RmnetProtectorMode extends BroadcastReceiver {

   private String PROPERTY_RMNETPROTECT ="sys.usb.config";
    private String TAG = "RmnetProtectorMode";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction() != null && intent.getAction().equals(SECRET_CODE_ACTION)) {

            Log.i(TAG, "handleRmnetProtector");
            String strRmnetProtect = SystemProperties.get(PROPERTY_RMNETPROTECT, "");

            Log.i(TAG, "property_Rmnetprotect=" + strRmnetProtect);
            String show = null;
            if (strRmnetProtect.contains("rmnet")) {
                show = context.getResources().getString(R.string.rmnetprotect_on);
                SystemProperties.set(PROPERTY_RMNETPROTECT, "diag,serial_cdev,serial_cdev_nmea,adb");
            } else {
                show = context.getResources().getString(R.string.rmnetprotect_off);
                SystemProperties.set(PROPERTY_RMNETPROTECT, "diag,serial_cdev,rmnet,adb");
            }
            AlertDialog alert = new AlertDialog.Builder(context.getApplicationContext())
                    .setTitle(R.string.rmnetprotect_title)
                    .setMessage(show)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .create();

            alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            alert.show();
        }
    }
}
