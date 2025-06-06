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

package com.android.settings.fuelgauge.batteryusage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.settings.core.instrumentation.ElapsedTimeUtils;
import com.android.settings.fuelgauge.BatteryUsageHistoricalLogEntry.Action;
import com.android.settings.fuelgauge.batteryusage.bugreport.BatteryUsageLogUtils;
import com.android.settingslib.fuelgauge.BatteryUtils;

import java.time.Duration;
import android.os.Message;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.widget.Toast;
import android.os.PowerManager;

/** Receives broadcasts to start or stop the periodic fetching job. */
public final class BootBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BootBroadcastReceiver";
    private static final long RESCHEDULE_FOR_BOOT_ACTION_DELAY_MILLIS =
            Duration.ofSeconds(6).toMillis();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Handler mBackgroundHandler = new BackgroundHandler(Looper.getMainLooper());
    private static final int MSG_GET_COUNTRY_CODE = 1;
    private static final int MSG_REBOOT_LOAD_WIFI = 2;
    private static int mtrytimes = 0;

    private WifiManager mWifiManager;
    private PowerManager mPm;
    private static Context mContext;

    public static final String ACTION_PERIODIC_JOB_RECHECK =
            "com.android.settings.battery.action.PERIODIC_JOB_RECHECK";
    public static final String ACTION_SETUP_WIZARD_FINISHED =
            "com.google.android.setupwizard.SETUP_WIZARD_FINISHED";

    /** Invokes periodic job rechecking process. */
    public static void invokeJobRecheck(Context context) {
        context = context.getApplicationContext();
        final Intent intent = new Intent(ACTION_PERIODIC_JOB_RECHECK);
        intent.setClass(context, BootBroadcastReceiver.class);
        context.sendBroadcast(intent);
    }

    private class BackgroundHandler extends Handler {

        public BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_COUNTRY_CODE:
                    mtrytimes++;
                    if(mtrytimes <= 2){
                        getWifiCountryCode();
                    }else{
                        SystemProperties.set("persist.odm.ccode","other");
                    }
                    break;
                case MSG_REBOOT_LOAD_WIFI:
                    if(mPm != null){
                        mPm.reboot(null);
                    }else{
                        if(mContext != null){
                            mPm = mContext.getSystemService(PowerManager.class);
                            mPm.reboot(null);
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent == null ? "" : intent.getAction();
        if (BatteryUtils.isWorkProfile(context)) {
            Log.w(TAG, "do not start job for work profile action=" + action);
            return;
        }

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case ACTION_SETUP_WIZARD_FINISHED:
            case ACTION_PERIODIC_JOB_RECHECK:
                Log.d(TAG, "refresh periodic job from action=" + action);
                refreshJobs(context);
                break;
            case Intent.ACTION_TIME_CHANGED:
                Log.d(TAG, "refresh job and clear data from action=" + action);
                DatabaseUtils.clearDataAfterTimeChangedIfNeeded(context, intent);
                break;
            case Intent.ACTION_TIMEZONE_CHANGED:
                Log.d(TAG, "refresh job and clear all data from action=" + action);
                DatabaseUtils.clearDataAfterTimeZoneChangedIfNeeded(context);
                break;
            default:
                Log.w(TAG, "receive unsupported action=" + action);
        }

        // Waits a while to recheck the scheduler to avoid AlarmManager is not ready.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            final Intent recheckIntent = new Intent(ACTION_PERIODIC_JOB_RECHECK);
            recheckIntent.setClass(context, BootBroadcastReceiver.class);
            final long delayedTime = RESCHEDULE_FOR_BOOT_ACTION_DELAY_MILLIS;
            mHandler.postDelayed(() -> context.sendBroadcast(recheckIntent), delayedTime);

            // Refreshes the usage source from UsageStatsManager when booting.
            DatabaseUtils.removeUsageSource(context);

            BatteryUsageLogUtils.writeLog(context, Action.RECHECK_JOB, "delay:" + delayedTime);

            if(mContext == null){
                 mContext = context.getApplicationContext();
            }

            if(mWifiManager == null){
                mWifiManager = context.getSystemService(WifiManager.class);
                mPm = context.getSystemService(PowerManager.class);
            }
            String countrycode = SystemProperties.get("persist.odm.ccode","");
            Log.d("wificode", "countrycode from property = " + countrycode);
            if("".equals(countrycode) || "other".equals(countrycode)){
                getWifiCountryCode();
            }
        } else if (ACTION_SETUP_WIZARD_FINISHED.equals(action)) {
            ElapsedTimeUtils.storeSuwFinishedTimestamp(context, System.currentTimeMillis());
        }
    }

    private void getWifiCountryCode(){
        if(mWifiManager == null && mContext != null){
            mWifiManager = mContext.getSystemService(WifiManager.class);
        }

        if(mWifiManager != null){
            String countrycode = mWifiManager.getCountryCode();
            Log.d("wificode", "countrycode = " + countrycode);
            if(countrycode == null || countrycode.isEmpty()){
                mBackgroundHandler.sendEmptyMessageDelayed(MSG_GET_COUNTRY_CODE,10*1000);
            }else{
                if("US".equals(countrycode) || "CA".equals(countrycode)){
                    SystemProperties.set("persist.odm.ccode","fcc");
                    Toast toast = Toast.makeText(mContext, "In the NA region. Restart the phone to load the corresponding configuration.", Toast.LENGTH_SHORT);
                    toast.show();
                    mBackgroundHandler.sendEmptyMessageDelayed(MSG_REBOOT_LOAD_WIFI,3*1000);
                }else if("CN".equals(countrycode)){
                    SystemProperties.set("persist.odm.ccode","other");
                }else{
                    SystemProperties.set("persist.odm.ccode","eu");
                }
            }
        }
    }

    private static void refreshJobs(Context context) {
        PeriodicJobManager.getInstance(context).refreshJob(/* fromBoot= */ true);
    }
}
