/* Copyright (C) 2021 Tcl Corporation Limited */
package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import java.util.List;
import android.os.SystemProperties;
import android.app.AlertDialog;
import android.view.WindowManager;

public class PhoneCodeReceiver extends BroadcastReceiver {

    private static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static final String GMS_DISABLE = "persist.sys.gmsapp.disable";
    private static final String TAG = "PhoneCodeReceiver";
    private static final String HOST_CODE_GMS = "666";
    private static final String HOST_CODE_VERSIONINFO = "3228";
    private static final String HOST_CODE_DEVICEINFO = "02";
    private static final String HOST_CODE_MODULEINFO = "001";
    private Context mContext;
    private String READ_ERROR_STR = "????????";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive : " + intent.toString());
        mContext = context;

        final String action = intent.getAction();
        if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
            String host = intent.getData() != null ? intent.getData().getHost() : null;
            if (HOST_CODE_GMS.equals(host)) {
                PackageManager pm = context.getPackageManager();
                int hasDisabled = SystemProperties.getInt(GMS_DISABLE, 0);
                List<PackageInfo> packages =
                        pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
                for (PackageInfo pi : packages) {
                    if (pi.packageName != null
                                    && pi.packageName.startsWith("com.google")
                                    && !pi.packageName.equals("com.google.android.dialer")
                                    && !pi.packageName.equals(
                                            "com.google.android.networkstack.tethering")
                                    && !pi.packageName.equals("com.google.android.networkstack")
                                    && !pi.packageName.equals(
                                            "com.google.android.networkstack.permissionconfig")
                                    && !pi.packageName.equals("com.google.android.setupwizard")
                                    && !pi.packageName.equals("com.google.android.packageinstaller")
                                    && !pi.packageName.equals(
                                            "com.google.android.permissioncontroller")
                                    && !pi.packageName.equals(
                                            "com.google.android.apps.work.oobconfig")
                            || pi.packageName.equals("com.android.chrome")
                            || pi.packageName.equals("com.android.vending"))
                        try {
                            if (hasDisabled == 0) {
                                pm.setApplicationEnabledSetting(
                                        pi.packageName,
                                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                                        0);
                            } else {
                                pm.setApplicationEnabledSetting(
                                        pi.packageName,
                                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                        0);
                        }
                        } catch (Exception e) {
                            // do nothing
                        }
                }
                if (hasDisabled == 0) {
                    SystemProperties.set(GMS_DISABLE, "1");
                } else {
                    SystemProperties.set(GMS_DISABLE, "0");
                }
            } else if (HOST_CODE_VERSIONINFO.equals(host)) {
                String result = "";
                result = SystemProperties.get("ro.tct.sys.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.boot.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.rec.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.modem.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.study.ver", READ_ERROR_STR);
                openAlertDialog(result);
            } else if (HOST_CODE_DEVICEINFO.equals(host)) {
                Intent i = new Intent(context, PhoneDeviceInfo.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } else if (HOST_CODE_MODULEINFO.equals(host)) {
                Intent i = new Intent(context, ModuleDeviceInfo.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }

    private void openAlertDialog(String message) {
        AlertDialog alert =
                new AlertDialog.Builder(mContext.getApplicationContext())
                        .setTitle(R.string.dialog_title_image_mapping)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .setCancelable(true)
                        .create();

        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alert.show();
    }
}
