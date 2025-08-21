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

/*
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.settings;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.FeatureFlagUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.biometrics.fingerprint2.BiometricsEnvironment;
import com.android.settings.core.instrumentation.ElapsedTimeUtils;
import com.android.settings.development.DeveloperOptionsActivityLifecycle;
import com.android.settings.flags.Flags;
import com.android.settings.fuelgauge.BatterySettingsStorage;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settings.localepicker.LocaleNotificationDataManager;
import com.android.settings.metrics.SettingsMetricsLogger;
import com.android.settings.network.telephony.TelephonyUtils;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.overlay.FeatureFactoryImpl;
import com.android.settings.spa.SettingsSpaEnvironment;
import com.android.settingslib.applications.AppIconCacheManager;
import com.android.settingslib.datastore.BackupRestoreStorageManager;
import com.android.settingslib.metadata.FixedArrayMap;
import com.android.settingslib.metadata.PreferenceScreenMetadataFactory;
import com.android.settingslib.metadata.PreferenceScreenRegistry;
import com.android.settingslib.metadata.ProvidePreferenceScreenOptions;
import com.android.settingslib.preference.PreferenceBindingFactory;
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
import android.text.TextUtils;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

/** Settings application which sets up activity embedding rules for the large screen device. */
@ProvidePreferenceScreenOptions(
        codegenCollector = "com.android.settings/PreferenceScreenCollector/get"
)
public class SettingsApplication extends Application {

    private static final String TAG = "SettingsApplication";
    private WeakReference<SettingsHomepageActivity> mHomeActivity = new WeakReference<>(null);
    @Nullable volatile private BiometricsEnvironment mBiometricsEnvironment;
    private BatteryBroadcastReceiver mBatteryBroadcastReceiver = null;
    private static final long ONE_WEEK_SECONDS = 7 * 24 * 60 * 60;
    private static final String BATTERY_CYCLE_COUNT = "BatteryCycleCount";
    private static final String BATTERY_SOH = "BatterySoh";
    public static final String IS_REMOVE_BATTERY_HEALTH = "IsRemoveBatteryHealth";
    private static final String FIRST_BOOT_TIME = "persist.sys.first_boot_time";
    private static final String IS_BATTERY_HEALTH_HIDE = "persist.sys.is_battery_health_hide";
    private boolean isDebug = false;
    final String WALLPAPER_CONFIG = "persist.sys.config.wallpaper";
    private PowerManager mPm;
    private final Handler mBackgroundHandler = new BackgroundHandler(Looper.getMainLooper());
    private static final int MSG_REBOOT_LOAD_WIFI = 1;

    private class BackgroundHandler extends Handler {

        public BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REBOOT_LOAD_WIFI:
                    if(mPm != null){
                        mPm.reboot(null);
                    }
                    break;
            }
        }
    }
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED)) {
                System.exit(0);
            }else if("android.intent.action.NETWORK_COUNTRYCODE_UPDATED".equals(action)){
                String countrycode = intent.getStringExtra("countrycode");
                getWifiCountryCode(countrycode);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        FeatureFactory.setFactory(this, getFeatureFactory());
    }

    private void getWifiCountryCode(String countrycode){
        Log.d("wificode", "countrycode = " + countrycode);
        String countrycodeFromProperty = SystemProperties.get("persist.odm.ccode","other");
        Log.d("wificode", "countrycode from property = " + countrycodeFromProperty);
        if("US".equals(countrycode)){
            if(!"fcc".equals(countrycodeFromProperty)){
                SystemProperties.set("persist.odm.ccode","fcc");
                showToast();
                mBackgroundHandler.sendEmptyMessageDelayed(MSG_REBOOT_LOAD_WIFI,3*1000);
            }
        }else if("EU".equals(countrycode)){
            if(!"eu".equals(countrycodeFromProperty)){
                SystemProperties.set("persist.odm.ccode","eu");
            }
            if("fcc".equals(countrycodeFromProperty)){
                showToast();
                mBackgroundHandler.sendEmptyMessageDelayed(MSG_REBOOT_LOAD_WIFI,3*1000);
            }
        }
    }

    private void showToast(){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getBaseContext(), "Restart the phone to load the corresponding configuration.", Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Flags.catalyst()) {
            PreferenceScreenRegistry.INSTANCE.setPreferenceScreenMetadataFactories(
                    preferenceScreenFactories());
            PreferenceScreenRegistry.INSTANCE.setPreferenceUiActionMetricsLogger(
                    new SettingsMetricsLogger(this));
            PreferenceBindingFactory.setDefaultFactory(new SettingsPreferenceBindingFactory());
        }

        mPm = getBaseContext().getSystemService(PowerManager.class);
        BackupRestoreStorageManager.getInstance(this)
                .add(
                        new BatterySettingsStorage(this),
                        LocaleNotificationDataManager.getSharedPreferencesStorage(this));

        // Add null checking to avoid test case failed.
        if (getApplicationContext() != null) {
            ElapsedTimeUtils.assignSuwFinishedTimeStamp(getApplicationContext());
        }

        TelephonyUtils.connectExtTelephonyService(getApplicationContext());

        // Set Spa environment.
        setSpaEnvironment();

        if (ActivityEmbeddingUtils.isSettingsSplitEnabled(this)
                && FeatureFlagUtils.isEnabled(this,
                        FeatureFlagUtils.SETTINGS_SUPPORT_LARGE_SCREEN)) {
            if (WizardManagerHelper.isUserSetupComplete(this)) {
                new ActivityEmbeddingRulesController(this).initRules();
            } else {
                new DeviceProvisionedObserver().registerContentObserver();
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED);
        filter.addAction("android.intent.action.NETWORK_COUNTRYCODE_UPDATED");
        registerReceiver(mBroadcastReceiver,filter);

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

        Log.d(TAG, "Try initialize default wifi ssid");
        updateDefaultWifiSsid();
    }

    private void updateDefaultWifiSsid() {
        if (!"FP6".equalsIgnoreCase(Build.PRODUCT)) return;
        if (getApplicationContext() == null) return;

        final String KEY_SSID_UPDATE = "wifi_default_ssid_update";
        SharedPreferences prefs = getSharedPreferences("wifi_tether",
            Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SSID_UPDATE, false)) {
            Log.d(TAG, "Default wifi ssid updated already, bail");
            return;
        }

        WifiManager wifiManager = getApplicationContext().getSystemService(
                WifiManager.class);
        SoftApConfiguration config = wifiManager.getSoftApConfiguration();
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder(config);

        String ssid = SystemProperties.get("ro.product.product.model");
        Log.d(TAG, "Update wifi default ssid: " + ssid);
        configBuilder.setSsid(ssid);
        wifiManager.setSoftApConfiguration(configBuilder.build());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("wifi_default_ssid_update", true);
        editor.commit();

        boolean success = prefs.getBoolean(KEY_SSID_UPDATE, false);
        Log.d(TAG, "Update Wifi default ssid(" + ssid + ") updated "
            + (success ? "success" : "failed"));
    }

    /** Returns the factories of preference screen metadata. */
    protected FixedArrayMap<String, PreferenceScreenMetadataFactory> preferenceScreenFactories() {
        // PreferenceScreenCollector is generated by annotation processor from classes annotated
        // with @ProvidePreferenceScreen
        return PreferenceScreenCollector.get();
    }

    /**
     * Set default Lock wallpaper
     */
    private void setDefaultOnLock(Context mContext) {
        String wallpaperConfig = SystemProperties.get(WALLPAPER_CONFIG, null);
        Log.d(TAG, "setDefaultOnLock wallpaperConfig = " + wallpaperConfig);
        if (!TextUtils.isEmpty(wallpaperConfig)) {
            return;
        }
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
            mWallpaperManager.setBitmap(lockBitmap,null,true,WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
        } catch (IOException e) {
            Log.w(TAG, "Setting wallpaper to default threw exception", e);
        }
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
        BiometricsEnvironment localEnvironment = mBiometricsEnvironment;
        if (localEnvironment == null) {
            synchronized (this) {
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                    return null;
                }
                final FingerprintManager fpm = getSystemService(FingerprintManager.class);
                localEnvironment = mBiometricsEnvironment;
                if (fpm != null && localEnvironment == null) {
                    mBiometricsEnvironment = localEnvironment = new BiometricsEnvironment(this,
                            fpm);
                } else {
                    Log.e(TAG, "Error when creating environment, fingerprint manager was null");
                }
            }
        }
        return localEnvironment;
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
        long date = SystemProperties.getLong("persist.sys.fp.tft.date",0);
        if(date == 0){
            long persistTFTdate = SystemProperties.getLong("sys.fp.tft",0);
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
