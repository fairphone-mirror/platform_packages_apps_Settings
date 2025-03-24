/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settings.fuelgauge.batterysaver.BatterySaverScheduleRadioButtonsController;
import com.android.settings.fuelgauge.datasaver.DynamicDenylistManager;
import com.android.settingslib.fuelgauge.BatterySaverUtils;

import java.util.List;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.SystemProperties;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import android.os.UserHandle;

/** Execute battery settings migration tasks in the device booting stage. */
public final class BatterySettingsMigrateChecker extends BroadcastReceiver {
    private static final String TAG = "BatterySettingsMigrateChecker";

    private static final String T2M_PROP_SET_FILESDEFAULT = "persist.sys.setfilesdefault";

    @VisibleForTesting static BatteryOptimizeUtils sBatteryOptimizeUtils = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent + " owner: " + BatteryBackupHelper.isOwner());
        if (intent != null
                && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && BatteryBackupHelper.isOwner()) {
            verifyConfiguration(context);
        }

        if(intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            if("0".equals(SystemProperties.get(T2M_PROP_SET_FILESDEFAULT,"0"))){
                final PackageManager pm = context.getPackageManager();
                try{
                    Intent action = new Intent();
                    action.setAction("android.intent.action.VIEW");
                    action.setDataAndType(Uri.parse("content://com.android.providers.media.documents/"),"vnd.android.document/root");
                    List<ResolveInfo> list = pm.queryIntentActivities(action,0);
                    int size = list.size();
                    ComponentName[] set;
                    set = new ComponentName[size];
                    int google_files_index=0;
                    for(int i= 0;i< size;i++){
                        set[i] = new ComponentName(list.get(i).activityInfo.packageName,
                            list.get(i).activityInfo.name);

                        if("com.google.android.apps.nbu.files".equals(list.get(i).activityInfo.packageName)){
                            google_files_index = i;
                        }
                    }
                    IntentFilter filter = new IntentFilter();
                    filter.addAction("android.intent.action.VIEW");
                    filter.addDataType("vnd.android.document/root");
                    filter.addCategory(Intent.CATEGORY_DEFAULT);
                    pm.addUniquePreferredActivity(filter, list.get(google_files_index).match, set, list.get(google_files_index).activityInfo.getComponentName());
                } catch (Exception e) {
                    Log.w("SetdefaultFiles", "Failed to set ", e);
                }

                try{
                    Intent action = new Intent();
                    action.setAction("android.os.storage.action.MANAGE_STORAGE");
                    List<ResolveInfo> list = pm.queryIntentActivities(action,0);
                    int size = list.size();
                    ComponentName[] set;
                    set = new ComponentName[size];
                    int google_files_index=0;
                    for(int i= 0;i< size;i++){
                        set[i] = new ComponentName(list.get(i).activityInfo.packageName,
                            list.get(i).activityInfo.name);
                        if("com.google.android.apps.nbu.files".equals(list.get(i).activityInfo.packageName)){
                             google_files_index = i;
                        }
                    }
                    IntentFilter filter = new IntentFilter();
                    filter.addAction("android.os.storage.action.MANAGE_STORAGE");
                    filter.addCategory(Intent.CATEGORY_DEFAULT);
                    pm.addUniquePreferredActivity(filter, list.get(google_files_index).match, set, list.get(google_files_index).activityInfo.getComponentName());
                    SystemProperties.set(T2M_PROP_SET_FILESDEFAULT,"1");
                } catch (Exception e) {
                    Log.w("SetdefaultFiles", "Failed to set ", e);
                }
            }

            String SwitchKey = readSwitchKeyState();
            Intent intent_switch = new Intent("com.fairphone.action.SWITCH_STATE_CHANGED");
            intent_switch.putExtra("com.fairphone.extra.SWITCH_STATUS", "0".equals(SwitchKey) ? "UP" : "DOWN");
            intent_switch.addFlags(Intent.FLAG_RECEIVER_NO_ABORT | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND 
                    | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcastAsUser(intent_switch, UserHandle.ALL);
        }
    }

    private String readSwitchKeyState(){
        String state = "0";
        try {
            InputStream is = new FileInputStream("/sys/bus/platform/drivers/gpio-keys/soc:gpio_keys/switch_state");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            state = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "get SwitchKeyState fail" + e);
        }
        return state;
    }

    static void verifyConfiguration(Context context) {
        context = context.getApplicationContext();
        verifySaverConfiguration(context);
        verifyBatteryOptimizeModes(context);
        DynamicDenylistManager.getInstance(context).onBootComplete();
    }

    /** Avoid users set important apps into the unexpected battery optimize modes */
    static void verifyBatteryOptimizeModes(Context context) {
        Log.d(TAG, "invoke verifyOptimizationModes()");
        verifyBatteryOptimizeModeApps(
                context,
                BatteryOptimizeUtils.MODE_OPTIMIZED,
                BatteryOptimizeUtils.getForceBatteryOptimizeModeList(context));
        verifyBatteryOptimizeModeApps(
                context,
                BatteryOptimizeUtils.MODE_UNRESTRICTED,
                BatteryOptimizeUtils.getForceBatteryUnrestrictModeList(context));
    }

    @VisibleForTesting
    static void verifyBatteryOptimizeModeApps(
            Context context,
            @BatteryOptimizeUtils.OptimizationMode int optimizationMode,
            List<String> allowList) {
        for (String packageName : allowList) {
            final BatteryOptimizeUtils batteryOptimizeUtils =
                    BatteryBackupHelper.newBatteryOptimizeUtils(
                            context,
                            packageName,
                            /* testOptimizeUtils */ sBatteryOptimizeUtils);
            if (batteryOptimizeUtils == null) {
                continue;
            }
            if (batteryOptimizeUtils.getAppOptimizationMode() != optimizationMode) {
                Log.w(TAG, "Reset " + packageName + " mode into " + optimizationMode);
                batteryOptimizeUtils.setAppUsageState(
                        optimizationMode,
                        BatteryOptimizeHistoricalLogEntry.Action.FORCE_RESET);
            }
        }
    }

    static void verifySaverConfiguration(Context context) {
        Log.d(TAG, "invoke verifySaverConfiguration()");
        final ContentResolver resolver = context.getContentResolver();
        final int threshold =
                Settings.Global.getInt(resolver, Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0);
        // Force refine the invalid scheduled battery level.
        if (threshold < BatterySaverScheduleRadioButtonsController.TRIGGER_LEVEL_MIN
                && threshold > 0) {
            Settings.Global.putInt(
                    resolver,
                    Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL,
                    BatterySaverScheduleRadioButtonsController.TRIGGER_LEVEL_MIN);
            Log.w(TAG, "Reset invalid scheduled battery level from: " + threshold);
        }
        // Force removing the 'schedule by routine' state.
        BatterySaverUtils.revertScheduleToNoneIfNeeded(context);
    }
}
