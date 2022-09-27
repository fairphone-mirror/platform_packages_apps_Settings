/*
 * Copyright (c) 2013 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.settings.wifi;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.settingslib.wifi.AccessPoint;

public class SSIDPreloadService extends Service {

    private static final String TAG = "SSIDPreloadService";
    private static final boolean DEBUG = true;

    private static final String SHARE_PREFERENCE_FILE_NAME = "wifi_ssid_preload";
    private static final String SHARE_PREFERENCE_PRELOAD_FLAG_KEY = "wifi_ssid_preload_flag";

    private static final int EVENT_CHECK_AND_SAVE_PRESET_NETWORK = 0;

    private Handler mHandler = new SSIDPreloadEventHandler();
    private boolean mIsSSIDPreloaded;
    private WifiManager mWifiManager;

    private WifiManager.ActionListener mSaveListener = new WifiManager.ActionListener() {
        public void onSuccess() {
            if (DEBUG) Log.e(TAG, "Save network successfully");
            setSSIDPreloadStatus(true);
        }

        public void onFailure(int reason) {
            setSSIDPreloadStatus(false);
            Log.e(TAG, "Failed to save network, reason:" + reason);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mIsSSIDPreloaded = isSSIDPreloaded();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG,"SSIDPreloadService onStartCommand mIsSSIDPreloaded = " + mIsSSIDPreloaded);
        if (!mIsSSIDPreloaded) {
            mHandler.sendEmptyMessage(EVENT_CHECK_AND_SAVE_PRESET_NETWORK);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private class SSIDPreloadEventHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case EVENT_CHECK_AND_SAVE_PRESET_NETWORK:
                    try {
                        if (DEBUG) Log.d(TAG,"checkAndSavePresetNetwork");
                        preloadSSIDNetwork(getApplicationContext());
                        stopSelf();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void preloadSSIDNetwork(Context context) {
        if (DEBUG) Log.d(TAG, "preloadSSIDNetwork");

        int activeModems = TelephonyManager.from(context).getActiveModemCount();
        int phoneId = 0;
        for ( ; phoneId < activeModems; phoneId++) {
            String simOperator = TelephonyManager.from(context).getSimOperatorNumericForPhone(phoneId);
            if (simOperator != null && simOperator.equals("20815")) {
                if (DEBUG) Log.d(TAG, "preloadSSIDNetwork Device insert a 20815 SIM card");
                break;
            }
        }

        if (phoneId == activeModems) {
            if (DEBUG) Log.d(TAG, "Device has no 20815 SIM card, not doing preload SSID");
            return;
        }

        WifiConfiguration config = new WifiConfiguration();
        String default_ssid = "FreeWifi_secure";
        config.SSID = AccessPoint.convertToQuotedString(default_ssid.trim());
        if (DEBUG) Log.d(TAG, "Preload SSID : " + config.SSID);

        if (AccessPoint.convertToQuotedString(config.SSID).trim().length() == 0){
            if (DEBUG) Log.d(TAG, "Preload null SSID, just return!");
            return;
        }

        // set default akm to WPA-EAP
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        config.priority = 0;

        // set default hidden to false
        config.hiddenSSID = false;
        mWifiManager.save(config, mSaveListener);
        mWifiManager.startScan();
    }

    private void setSSIDPreloadStatus(boolean isPreloaded) {
        SharedPreferences sharedPreferences = getSharedPreferences(
                SHARE_PREFERENCE_FILE_NAME,
                Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARE_PREFERENCE_PRELOAD_FLAG_KEY, isPreloaded);
        editor.commit();
    }

    private boolean isSSIDPreloaded() {
        SharedPreferences sharedPreferences = getSharedPreferences(
                SHARE_PREFERENCE_FILE_NAME,
                Activity.MODE_PRIVATE);
        Log.d(TAG, "isSSIDPreloaded " + sharedPreferences.getBoolean(SHARE_PREFERENCE_PRELOAD_FLAG_KEY, false));
        return sharedPreferences.getBoolean(SHARE_PREFERENCE_PRELOAD_FLAG_KEY, false);
    }
}
