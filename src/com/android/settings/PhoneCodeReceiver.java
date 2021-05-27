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

public class PhoneCodeReceiver extends BroadcastReceiver {

    private static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static final String GMS_DISABLE = "persist.sys.gmsapp.disable";
    private static final String TAG = "PhoneCodeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive : " + intent.toString());
        PackageManager pm = context.getPackageManager();
        final String action = intent.getAction();
        if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
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
                                && !pi.packageName.equals("com.google.android.permissioncontroller")
                                && !pi.packageName.equals("com.google.android.apps.work.oobconfig")
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
        }
    }
}
