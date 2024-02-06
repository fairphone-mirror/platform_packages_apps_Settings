/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings;

import android.app.Application;

import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settingslib.applications.AppIconCacheManager;
import com.android.settings.anc.lifecycle.LifecycleCallback;

import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.content.Context;
import android.os.SystemClock;
import android.os.SystemProperties;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;


import java.lang.ref.WeakReference;

/** Settings application which sets up activity embedding rules for the large screen device. */
public class SettingsApplication extends Application {
    private static final String TAG = "SettingsApplication";
    private WeakReference<SettingsHomepageActivity> mHomeActivity = new WeakReference<>(null);
    private BatteryBroadcastReceiver mBatteryBroadcastReceiver = null;
    private static final long ONE_WEEK_SECONDS = 7 * 24 * 60 * 60;
    private static final String BATTERY_CYCLE_COUNT = "BatteryCycleCount";
    private static final String BATTERY_SOH = "BatterySoh";
    public static final String IS_REMOVE_BATTERY_HEALTH = "IsRemoveBatteryHealth";
    private static final String FIRST_BOOT_TIME = "persist.sys.first_boot_time";
    private boolean isDebug = false;

    @Override
    public void onCreate() {
        super.onCreate();

        final ActivityEmbeddingRulesController controller =
                new ActivityEmbeddingRulesController(this);
        controller.initRules();
        registerActivityLifecycleCallbacks(new LifecycleCallback());
        if (mBatteryBroadcastReceiver == null) {
            mBatteryBroadcastReceiver = new BatteryBroadcastReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.BATTERY_CHANGED");
            registerReceiver(mBatteryBroadcastReceiver, intentFilter);
        }
    }

    public void setHomeActivity(SettingsHomepageActivity homeActivity) {
        mHomeActivity = new WeakReference<>(homeActivity);
    }

    public SettingsHomepageActivity getHomeActivity() {
        return mHomeActivity.get();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        AppIconCacheManager.getInstance().release();
    }

    private class BatteryBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String batteryChangeAction = "android.intent.action.BATTERY_CHANGED";
            SharedPreferences sharedPreferences= getSharedPreferences("BatteryData", Context.MODE_PRIVATE);

            if (batteryChangeAction.equals(action)) {
                String soh = readBatHealth("/sys/class/qcom-battery/soh");
                String cycle_count = readBatHealth("/sys/class/power_supply/battery/cycle_count");
                int sohInt = Integer.valueOf(soh);
                int cycleCountInt = Integer.valueOf(cycle_count);
                if (cycleCountInt > 0) {
                    sharedPreferences.edit().putInt(BATTERY_CYCLE_COUNT,cycleCountInt).commit();
                }
                if (sohInt < 100) {
                    sharedPreferences.edit().putInt(BATTERY_SOH,sohInt).commit();
                }
                long firstBootTime = SystemProperties.getLong(FIRST_BOOT_TIME,0);
                if (firstBootTime == 0) {
                    SystemProperties.set(FIRST_BOOT_TIME,readTFT()+"");
                }
                boolean isTimeOverWeeks = false;
                if (firstBootTime > 0) {
                    isTimeOverWeeks = (readTFT() - firstBootTime) > ONE_WEEK_SECONDS;
                }
                if (isDebug) {
                    Log.d(TAG,"SettingsApplication.java-BatteryBroadcastReceiver-isTimeOverWeeks:"+isTimeOverWeeks+"    firstBootTime:"+firstBootTime+"    readTFT() - firstBootTime:"+(readTFT() - firstBootTime));
                }
                if (isTimeOverWeeks) {
                    int mBatteryCycleCount = sharedPreferences.getInt(BATTERY_CYCLE_COUNT,0);
                    int mBatterySoh = sharedPreferences.getInt(BATTERY_SOH,100);
                    if (isDebug) {
                        Log.d(TAG,"SettingsApplication.java-BatteryBroadcastReceiver-sohInt:"+sohInt+"    mBatterySoh:"+mBatterySoh+"    cycleCountInt:"+cycleCountInt+"    mBatteryCycleCount:"+mBatteryCycleCount);
                    }
                    if ((mBatterySoh < 100 && sohInt == 100) || (mBatteryCycleCount > 0 && cycleCountInt == 0)) {
                        sharedPreferences.edit().putBoolean(IS_REMOVE_BATTERY_HEALTH,true).commit();
                    }
                }
            }
        }
    }

    private String readBatHealth(String filename) {
        String value = "0";
        BufferedReader reader = null;
        FileReader fr = null;
        try {
            fr = new FileReader(filename);
            reader = new BufferedReader(fr);
            value = reader.readLine();
        } catch (IOException exception) {
            exception.printStackTrace();
        }finally {
            try{
                if (reader != null)
                    reader.close();
                if (fr != null)
                    fr.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return value;
    }

    private long readTFT(){
        long date = SystemProperties.getLong("persist.sys.tct.tft.date",0);
        if(date == 0){
            long persistTFTdate = SystemProperties.getLong("sys.t2m.tft",0);
            if(persistTFTdate != 0){
                date = persistTFTdate;
            }
        }
        if(date == 0){
            return SystemClock.elapsedRealtime() / 1000;
        }else{
            if(SystemClock.elapsedRealtime() > date){
                return SystemClock.elapsedRealtime() / 1000;
            }else{
                return date/1000;
            }
        }
    }
}
