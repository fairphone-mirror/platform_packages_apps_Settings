/*
 * Copyright (C) 2021-2022 Fairphone B.V.
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

package com.arima.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.Process;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.feature.ImsFeature;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.settings.wifi.calling.DisclaimerItem;
import com.android.settings.wifi.calling.EmergencyCallLimitationDisclaimer;

public class VoWifiQSTileService extends TileService {
    private final String TAG = "VoWifiQSTileService";
    private final boolean DEBUG = false;

    private final int UNINITIALIZED_DELAY_VALUE = -1;

    private ImsManager mImsManager = null;
    private WfcSettingObserver mWfcSettingObserver;
    private CarrierConfigManager mCarrierConfigManager;
    private int mSubId = 0;
    private boolean mEnable = true;
    private boolean mAvailable = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mWfcSettingObserver = new WfcSettingObserver();
        mCarrierConfigManager = this.getSystemService(CarrierConfigManager.class);
        if (DEBUG) Log.d(TAG, "onCreate id=" + mSubId);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (DEBUG) Log.d(TAG, "onStartListening");
        mSubId = getMasterSimSubId();
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mImsManager = ImsManager.getInstance(this, SubscriptionManager.getPhoneId(mSubId));
            mAvailable = wifiCallingSettingsAvalible();
        }
        updateStatus();
        registerWfcSettingObserver();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (DEBUG) Log.d(TAG, "onStopListening");
        unregisterWfcSettingObserver();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (DEBUG) Log.d(TAG, "onClick");

        if (mAvailable && !needToShowMobileSettingPage()) {
            mImsManager.setWfcSetting(mEnable ? false : true);
            updateStatus();
        }
    }

    protected PersistableBundle getCarrierConfig() {
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(mSubId);
        if (config != null) {
            return config;
        }

        return CarrierConfigManager.getDefaultConfig();
    }

    private boolean needToShowMobileSettingPage() {
        int notificationDelay =
                getCarrierConfig()
                        .getInt(CarrierConfigManager.KEY_EMERGENCY_NOTIFICATION_DELAY_INT);

        SharedPreferences prefs =
                this.getSharedPreferences(
                        "wfc_disclaimer_prefs", Context.MODE_PRIVATE);
        boolean disclaimer =
                prefs.getBoolean(
                        "key_has_agreed_emergency_limitation_disclaimer"
                        + mSubId,
                        false);
        if (DEBUG)
            Log.d(
                    TAG,
                    "needToShowMobileSettingPage notificationDelay="
                            + notificationDelay
                            + " disclaimer="
                            + disclaimer);

        if (notificationDelay != UNINITIALIZED_DELAY_VALUE && !disclaimer) {
            int userID = Process.myUserHandle().myUserId();
            Intent mobileSetting = new Intent("android.settings.WIFI_CALLING_SETTINGS");
            mobileSetting.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivityAndCollapse(mobileSetting);
            if (DEBUG) Log.d(TAG, "needToShowMobileSettingPage mobileSetting=" + mobileSetting);
            return true;
        }

        return false;
    }

    public boolean wifiCallingSettingsAvalible() {
        boolean isCConfigHideWifiCalling = getCarrierConfig().getBoolean("hide_wfc_ims_bool");
        try {
            if ((mImsManager.getImsServiceState() != ImsFeature.STATE_READY)
                    || isCConfigHideWifiCalling) {
                if (DEBUG)
                    Log.d(
                            TAG,
                            "wifiCallingSettingsAvalible"
                                    + " ImsServiceState="
                                    + mImsManager.getImsServiceState()
                                    + " isCConfigHideWifiCalling="
                                    + isCConfigHideWifiCalling);
                return false;
            }
        } catch (ImsException ex) {
            if (DEBUG) Log.d(TAG, "Exception when trying to get ImsServiceStatus: " + ex);
        }
        return true;
    }

    private void updateStatus() {
        if (DEBUG) Log.d(TAG, "updateStatus avalible=" + mAvailable + " enable=" + mEnable);
        Tile tile = getQsTile();

        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && mAvailable) {
            mEnable = mImsManager.isWfcEnabledByUser() && mImsManager.isNonTtyOrTtyOnVolteEnabled();
            tile.setState(mEnable ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            int wfcMode = mImsManager.getWfcMode(false);
            String mode = getWfcModeSummary(wfcMode);
            tile.setSubtitle(mode);
        } else {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle("");
        }
        tile.updateTile();
    }

    private String getWfcModeSummary(int wfcMode) {
        int resId = com.android.internal.R.string.wifi_calling_off_summary;
        if (mImsManager.isWfcEnabledByUser()) {
            switch (wfcMode) {
                case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                    resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                    resId = com.android.internal.R.string.wfc_mode_cellular_preferred_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                    resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.IMS_PREFERRED:
                    resId = com.android.internal.R.string.wfc_mode_ims_preferred_summary;
                    break;
                default:
                    Log.e(TAG, "Unexpected WFC mode value: " + wfcMode);
            }
        }

        return this.getResources().getString(resId);
    }

    private int getMasterSimSubId() {
        SubscriptionManager sm =
                (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        int simStateStableCount = 0;
        int simCount = sm.getActiveSubscriptionInfoCountMax();
        int simSlotId = -1;

        for (int i = 0; i < simCount; i++) {
            int simState = sm.getSimStateForSlotIndex(i);
            if (simState != TelephonyManager.SIM_STATE_UNKNOWN
                    && simState != TelephonyManager.SIM_STATE_PIN_REQUIRED) {
                simStateStableCount++;
                if (DEBUG) Log.d(TAG, "getMasterSimSubId simSlotId=" + simSlotId + " i=" + i);

                if (simSlotId == -1 || simSlotId > i) {
                    simSlotId = i;
                }
            }
        }
        if (DEBUG) Log.d(TAG, "getMasterSimSubId simSlotId=" + simSlotId);

        int simSubId = -1;
        if (simSlotId != -1) {
            int[] subIds = SubscriptionManager.getSubId(simSlotId);
            if (subIds != null && subIds.length > 0) {
                simSubId = subIds[0];
            }
        }
        if (DEBUG) Log.d(TAG, "getMasterSimSubId simSlotId=" + simSlotId + " simSubId=" + simSubId);

        boolean simReady = (simStateStableCount == simCount);
        if (DEBUG) Log.d(TAG, "getMasterSimSubId Ready=" + simReady + " {" + simSubId + "}");

        return simReady ? simSubId : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private class WfcSettingObserver extends ContentObserver {
        WfcSettingObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (DEBUG) Log.d(TAG, "onChange self=" + selfChange + " uri=" + uri);
            updateStatus();
        }
    }

    private void registerWfcSettingObserver() {
        unregisterWfcSettingObserver();
        Uri uri = getUriForWfcEnableSetting();
        if (DEBUG) Log.d(TAG, "registerWfcSettingObserver id=" + mSubId + " uri=" + uri);
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            getContentResolver().registerContentObserver(uri, false, mWfcSettingObserver);
        }
    }

    private void unregisterWfcSettingObserver() {
        if (DEBUG) Log.d(TAG, "unregisterWfcSettingObserver");
        getContentResolver().unregisterContentObserver(mWfcSettingObserver);
    }

    private Uri getUriForWfcEnableSetting() {
        Uri uri =
                Uri.withAppendedPath(
                        SubscriptionManager.getUriForSubscriptionId(mSubId),
                        SubscriptionManager.WFC_IMS_ENABLED);
        if (DEBUG) Log.d(TAG, "getUriForWfcEnableSetting id=" + mSubId + " uri=" + uri);
        return uri;
    }
}
