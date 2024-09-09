/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.sim.receivers;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;
import android.os.LocaleList;

import com.android.internal.app.LocalePicker;
import com.android.settings.sim.SimActivationNotifier;
import com.android.settings.sim.SimNotificationService;

import java.util.Locale;

/** This class manage all SIM operations after device boot up. */
public class SimCompleteBootReceiver extends BroadcastReceiver {
    private static final String TAG = "SimCompleteBootReceiver";
    public static boolean isLaunguageChanged = false;
    private static final String MY_FAIRPHONE_PERMISSION = "persist.sys.fairphone.permission";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isLaunguageChanged) {
            Locale oldLocale = Locale.getDefault();
            LocaleList locales = context.getResources().getConfiguration().getLocales();
            Locale newLocal = new Locale("en_US");
            if (oldLocale.toString().startsWith("en")) {
                newLocal = new Locale("zh_CN_#Hans");
            }
            LocalePicker.updateLocale(newLocal);
            //LocalePicker.updateLocale(oldLocale);
            LocalePicker.updateLocales(locales);
            isLaunguageChanged = true;
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e(TAG, "Invalid broadcast received.");
            return;
        }
        if (SimActivationNotifier.getShowSimSettingsNotification(context)) {
            SimNotificationService.scheduleSimNotification(
                    context, SimActivationNotifier.NotificationType.NETWORK_CONFIG);
        }

        if(isFirstSetMyFairPhonePermission(context)){
            setFirstSetMyFairPhonePermission(context);
            configVodafonePermission(context);
        }

    }

    private boolean isFirstSetMyFairPhonePermission(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_ANIMAL,Context.MODE_PRIVATE);
        return sharedPreferences.getInt(MY_FAIRPHONE_PERMISSION,0) == 0;
    }

    private void setFirstSetMyFairPhonePermission(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_ANIMAL,Context.MODE_PRIVATE);
        sharedPreferences.edit().putInt(MY_FAIRPHONE_PERMISSION,1).commit();
    }

    private void configVodafonePermission(Context context){
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        appOpsManager.setMode(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                  ActivityManager.getCurrentUser(), "com.fairphone.myfairphone", AppOpsManager.MODE_ALLOWED);
    }

}
