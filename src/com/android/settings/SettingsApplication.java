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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.FeatureFlagUtils;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.biometrics.fingerprint2.BiometricsEnvironment;
import com.android.settings.core.instrumentation.ElapsedTimeUtils;
import com.android.settings.development.DeveloperOptionsActivityLifecycle;
import com.android.settings.fuelgauge.BatterySettingsStorage;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settings.localepicker.LocaleNotificationDataManager;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.overlay.FeatureFactoryImpl;
import com.android.settings.spa.SettingsSpaEnvironment;
import com.android.settingslib.applications.AppIconCacheManager;
import com.android.settingslib.datastore.BackupRestoreStorageManager;
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory;

import com.google.android.setupcompat.util.WizardManagerHelper;
import com.android.settings.anc.lifecycle.LifecycleCallback;
import com.android.internal.app.LocalePicker;

import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.ParcelFileDescriptor;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;


import java.lang.ref.WeakReference;

/** Settings application which sets up activity embedding rules for the large screen device. */
public class SettingsApplication extends Application {
    private static final String TAG = "SettingsApplication";
    private WeakReference<SettingsHomepageActivity> mHomeActivity = new WeakReference<>(null);
    private BiometricsEnvironment mBiometricsEnvironment;
    private BatteryBroadcastReceiver mBatteryBroadcastReceiver = null;
    private static final long ONE_WEEK_SECONDS = 7 * 24 * 60 * 60;
    private static final String BATTERY_CYCLE_COUNT = "BatteryCycleCount";
    private static final String BATTERY_SOH = "BatterySoh";
    public static final String IS_REMOVE_BATTERY_HEALTH = "IsRemoveBatteryHealth";
    private static final String FIRST_BOOT_TIME = "persist.sys.first_boot_time";
    private static final String IS_BATTERY_HEALTH_HIDE = "persist.sys.is_battery_health_hide";
    private boolean isDebug = false;
    public static boolean isLaunguageChanged = false;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED)) {
                System.exit(0);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        FeatureFactory.setFactory(this, getFeatureFactory());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        BackupRestoreStorageManager.getInstance(this)
                .add(
                        new BatterySettingsStorage(this),
                        LocaleNotificationDataManager.getSharedPreferencesStorage(this));

        // Add null checking to avoid test case failed.
        if (getApplicationContext() != null) {
            ElapsedTimeUtils.assignSuwFinishedTimeStamp(getApplicationContext());
        }

        // Set Spa environment.
        setSpaEnvironment();
        mBiometricsEnvironment = new BiometricsEnvironment(this);

        if (ActivityEmbeddingUtils.isSettingsSplitEnabled(this)
                && FeatureFlagUtils.isEnabled(this,
                        FeatureFlagUtils.SETTINGS_SUPPORT_LARGE_SCREEN)) {
            if (WizardManagerHelper.isUserSetupComplete(this)) {
                new ActivityEmbeddingRulesController(this).initRules();
            } else {
                new DeviceProvisionedObserver().registerContentObserver();
            }
        }

        registerReceiver(mBroadcastReceiver,
                new IntentFilter(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED));
        registerReceiver(new LocaleChangeReceiver(),
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        registerUserSetupCompleteListener();

        registerActivityLifecycleCallbacks(new DeveloperOptionsActivityLifecycle());
        registerActivityLifecycleCallbacks(new LifecycleCallback());
        if (mBatteryBroadcastReceiver == null) {
            mBatteryBroadcastReceiver = new BatteryBroadcastReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.BATTERY_CHANGED");
            registerReceiver(mBatteryBroadcastReceiver, intentFilter);
        }

        ParcelFileDescriptor mParcelFileDescriptor = WallpaperManager.getInstance(getBaseContext()).getWallpaperFile(WallpaperManager.FLAG_LOCK);

        Log.d(TAG, "mParcelFileDescriptor = " + mParcelFileDescriptor);
        if (mParcelFileDescriptor == null) {
            new Thread(() -> setDefaultOnLock(getBaseContext())).start();
        }
    }

    /**
     * Set default Lock wallpaper
     */
    private void setDefaultOnLock(Context mContext) {
        try {
            WallpaperManager mWallpaperManager = WallpaperManager.getInstance(mContext);
            BitmapDrawable finalBitmapDrawable = null;
            BitmapDrawable lockDrawableFromCustomization = (BitmapDrawable) mWallpaperManager.getDrawable();
            BitmapDrawable lockDrawableFromGoogle = (BitmapDrawable) mWallpaperManager.getBuiltInDrawable();
            if (lockDrawableFromCustomization == null) {
                if (lockDrawableFromGoogle != null) {
                    finalBitmapDrawable = lockDrawableFromGoogle;
                } else {
                    Log.e(TAG, "No default vendor-customized wallpapers and no Google wallpapers");
                    return;
                }
            } else {
                finalBitmapDrawable = lockDrawableFromCustomization;
            }
            Bitmap lockBitmap = finalBitmapDrawable.getBitmap();
            mWallpaperManager.setBitmap(lockBitmap, null, true, WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
        } catch (IOException e) {
            Log.w(TAG, "Setting wallpaper to default threw exception", e);
        }
    }

    private void registerUserSetupCompleteListener() {
        Uri userSetupCompleteUri = Settings.Secure.getUriFor(
                Settings.Secure.USER_SETUP_COMPLETE);
        getContentResolver().registerContentObserver(userSetupCompleteUri,
                false, new ContentObserver(null) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        if (userSetupCompleteUri.equals(uri)) {
                            boolean isUserSetupCompleted = Settings.Secure.getInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
                            if (isUserSetupCompleted) {
                                if (!isLaunguageChanged) {
                                    Locale oldLocale = Locale.getDefault();
                                    Locale newLocal = new Locale("en_US");
                                    LocaleList locales = getResources().getConfiguration().getLocales();
                                    if (oldLocale.toString().startsWith("en")) {
                                        newLocal = new Locale("zh_CN_#Hans");
                                    }
                                    LocalePicker.updateLocale(newLocal);
                                    //LocalePicker.updateLocale(oldLocale);
                                    LocalePicker.updateLocales(locales);
                                    isLaunguageChanged = true;
                                }
                            }
                        }
                    }
                }, UserHandle.USER_ALL);
    }

    @Override
    public void onTerminate() {
        BackupRestoreStorageManager.getInstance(this).removeAll();
        super.onTerminate();
    }

    @NonNull
    protected FeatureFactory getFeatureFactory() {
        return new FeatureFactoryImpl();
    }

    /**
     * Set the spa environment instance.
     * Override this function to set different spa environment for different Settings app.
     */
    protected void setSpaEnvironment() {
        SpaEnvironmentFactory.INSTANCE.reset(new SettingsSpaEnvironment(this));
    }

    public void setHomeActivity(SettingsHomepageActivity homeActivity) {
        mHomeActivity = new WeakReference<>(homeActivity);
    }

    public SettingsHomepageActivity getHomeActivity() {
        return mHomeActivity.get();
    }

    @Nullable
    public BiometricsEnvironment getBiometricEnvironment() {
        return mBiometricsEnvironment;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        AppIconCacheManager.getInstance().trimMemory(level);
    }

    private class DeviceProvisionedObserver extends ContentObserver {
        private final Uri mDeviceProvisionedUri = Settings.Secure.getUriFor(
                Settings.Secure.USER_SETUP_COMPLETE);

        DeviceProvisionedObserver() {
            super(null /* handler */);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, int flags) {
            if (!mDeviceProvisionedUri.equals(uri)) {
                return;
            }

            SettingsApplication.this.getContentResolver().unregisterContentObserver(this);
            new ActivityEmbeddingRulesController(SettingsApplication.this).initRules();
        }

        public void registerContentObserver() {
            SettingsApplication.this.getContentResolver().registerContentObserver(
                    mDeviceProvisionedUri,
                    false /* notifyForDescendants */,
                    this);
        }
    }

    private class BatteryBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String batteryChangeAction = "android.intent.action.BATTERY_CHANGED";
            SharedPreferences sharedPreferences= getSharedPreferences("BatteryData", Context.MODE_PRIVATE);

            boolean isRecoveBatteryHealth = sharedPreferences.getBoolean(IS_REMOVE_BATTERY_HEALTH,false);
            if (isRecoveBatteryHealth) {
                SystemProperties.set(IS_BATTERY_HEALTH_HIDE,"1");
            } else {
                SystemProperties.set(IS_BATTERY_HEALTH_HIDE,"0");
            }

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
                        android.util.Log.d("debugdebug","SettingsApplication.java-BatteryBroadcastReceiver-sohInt:"+sohInt+"    mBatterySoh:"+mBatterySoh+"    cycleCountInt:"+cycleCountInt+"    mBatteryCycleCount:"+mBatteryCycleCount);
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
