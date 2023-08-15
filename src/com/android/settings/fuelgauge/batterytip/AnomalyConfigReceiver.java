/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.fuelgauge.batterytip;

import android.app.StatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.IntentFilter;
import java.util.List;
import android.net.Uri;
import android.os.SystemProperties;
import android.content.ContentResolver;
import android.provider.Settings;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;
/**
 * Receive broadcast when {@link StatsManager} restart, then check the anomaly config and
 * prepare info for {@link StatsManager}
 */
public class AnomalyConfigReceiver extends BroadcastReceiver {
    private static final String TAG = "AnomalyConfigReceiver";
    private static final String T2M_PROP_SET_FILESDEFAULT = "persist.sys.setfilesdefault";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (StatsManager.ACTION_STATSD_STARTED.equals(intent.getAction())
                || Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            final StatsManager statsManager = context.getSystemService(StatsManager.class);

            // Check whether to update the config
            AnomalyConfigJobService.scheduleConfigUpdate(context);

            try {
                BatteryTipUtils.uploadAnomalyPendingIntent(context, statsManager);
            } catch (StatsManager.StatsUnavailableException e) {
                Log.w(TAG, "Failed to uploadAnomalyPendingIntent.", e);
            }

            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                AnomalyCleanupJobService.scheduleCleanUp(context);
                final ContentResolver cr = context.getContentResolver();
                float minRefreshRate = Settings.System.getFloatForUser(cr,
                    Settings.System.MIN_REFRESH_RATE, 0f, cr.getUserId());
                if(minRefreshRate == 90f){
                    Settings.System.putFloatForUser(cr,
                        Settings.System.MIN_REFRESH_RATE, 60f,cr.getUserId());
                    Settings.System.putFloatForUser(cr,
                        Settings.System.MIN_REFRESH_RATE, 90f,cr.getUserId());
                }

                int screen_time = Settings.System.getInt(cr, SCREEN_OFF_TIMEOUT, 60*1000);
                if(Settings.Secure.getInt(cr, Settings.Secure.USER_SETUP_COMPLETE, 0) != 0 && screen_time == 121000){
                    Settings.System.putInt(cr, SCREEN_OFF_TIMEOUT, 60*1000); 
                }
            }
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
        }
    }
}
