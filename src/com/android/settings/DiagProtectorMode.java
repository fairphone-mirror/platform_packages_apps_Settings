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

public class DiagProtectorMode extends BroadcastReceiver {

   private String PROPERTY_DIAGPROTECT ="sys.usb.config";
    private String TAG = "DiagProtectorMode";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction() != null && intent.getAction().equals(SECRET_CODE_ACTION)) {

            Log.i(TAG, "handleDiagProtector");
            String strDiagProtect = SystemProperties.get(PROPERTY_DIAGPROTECT, "");

            Log.i(TAG, "property_diagprotect=" + strDiagProtect);
            String show = null;
            if (strDiagProtect.contains("diag")) {
                show = context.getResources().getString(R.string.diagprotect_on);
                SystemProperties.set(PROPERTY_DIAGPROTECT, "adb");
            } else {
                show = context.getResources().getString(R.string.diagprotect_off);
                SystemProperties.set(PROPERTY_DIAGPROTECT, "diag,serial_cdev,rmnet,adb");
            }
            AlertDialog alert = new AlertDialog.Builder(context.getApplicationContext())
                    .setTitle(R.string.diagprotect_title)
                    .setMessage(show)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .create();

            alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            alert.show();

        }
    }


}
