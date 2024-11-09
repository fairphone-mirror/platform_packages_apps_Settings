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

/*
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.settings.network.telephony;

import static androidx.lifecycle.Lifecycle.Event.ON_START;
import static androidx.lifecycle.Lifecycle.Event.ON_STOP;

import static com.android.settings.network.telephony.TelephonyConstants.RadioAccessFamily.LTE;
import static com.android.settings.network.telephony.TelephonyConstants.RadioAccessFamily.NR;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import android.content.res.Resources;

import com.android.settings.R;
import com.android.settings.network.AllowedNetworkTypesListener;
import com.android.settings.network.CarrierConfigCache;
import com.android.settings.network.telephony.TelephonyConstants.TelephonyManagerConstants;
import com.android.settings.utils.CarrierParamsUtil;

/**
 * Preference controller for "Preferred network mode"
 */
public class PreferredNetworkModePreferenceController extends TelephonyBasePreferenceController
        implements ListPreference.OnPreferenceChangeListener, LifecycleObserver {
    private static final String TAG = "PrefNetworkModeCtrl";

    private static final String LOG_TAG = "PreferredNetworkMode";
    private CarrierConfigCache mCarrierConfigCache;
    private TelephonyManager mTelephonyManager;
    private boolean mIsGlobalCdma;
    private Preference mPreference;
    private PhoneCallStateListener mPhoneStateListener;
    private AllowedNetworkTypesListener mAllowedNetworkTypesListener;
    @VisibleForTesting
    Integer mCallState;
    private String[] pref_network_mode = null;
    private String[] pref_network_value = null;

    public PreferredNetworkModePreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigCache = CarrierConfigCache.getInstance(context);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        final PersistableBundle carrierConfig = mCarrierConfigCache.getConfigForSubId(subId);
        boolean visible;
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            visible = false;
        } else if (carrierConfig == null) {
            visible = false;
        } else if (carrierConfig.getBoolean(
                CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL)
                || carrierConfig.getBoolean(
                CarrierConfigManager.KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL)) {
            visible = false;
        } else if (carrierConfig.getBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL)) {
            visible = true;
        } else {
            visible = false;
        }

        return visible ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @OnLifecycleEvent(ON_START)
    public void onStart() {
        if (mPhoneStateListener != null) {
            mPhoneStateListener.register(mContext, mSubId);
        }
        if (mAllowedNetworkTypesListener != null) {
            mAllowedNetworkTypesListener.register(mContext, mSubId);
        }
    }

    @OnLifecycleEvent(ON_STOP)
    public void onStop() {
        if (mPhoneStateListener != null) {
            mPhoneStateListener.unregister();
        }
        if (mAllowedNetworkTypesListener != null) {
            mAllowedNetworkTypesListener.unregister(mContext, mSubId);
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        // add by T2M.dengxiangyu for FP4-61 2021-04-14
        updateState(mPreference);
    }

    @Override
    public void updateState(Preference preference) {
        if (mTelephonyManager == null) {
            return;
        }
        super.updateState(preference);
        final ListPreference listPreference = (ListPreference) preference;
        //Modify begin by renjie.zhang FP5U-304 2024/2/23
        final int networkMode = getPreferredNetworkMode();
        updatePreferenceEntries(listPreference);
        listPreference.setValue(Integer.toString(networkMode));
        //listPreference.setSummary(getPreferredNetworkModeSummaryResId(networkMode));
        //Modify END by renjie.zhang FP5U-304 2024/2/23
        setNetworkModeSummaryText(listPreference, networkMode);
        listPreference.setEnabled(isCallStateIdle());
    }

    //Modify begin by renjie.zhang FP5U-304 2024/2/23
    private void updatePreferenceEntries(ListPreference preference) {
        // Default values
        final PersistableBundle carrierConfig = mCarrierConfigCache.getConfigForSubId(mSubId);

        if (carrierConfig != null) {
            pref_network_mode = carrierConfig.getStringArray(CarrierConfigManager.KEY_PREFERRED_NETWORK_MODE);
            pref_network_value = carrierConfig.getStringArray(CarrierConfigManager.KEY_PREFERRED_NETWORK_VALUE);
        }

        if (pref_network_mode != null && pref_network_value != null && pref_network_mode.length != 0 && pref_network_value.length != 0) {
            Log.d(LOG_TAG, "init preferred network from carrier config");
        }else {

            PersistableBundle carrierParams = CarrierParamsUtil.loadInstance(mContext).getCarrierParams(mSubId);
            if (carrierParams != null) {
                pref_network_mode = carrierParams.getStringArray(CarrierConfigManager.KEY_PREFERRED_NETWORK_MODE);
                pref_network_value = carrierParams.getStringArray(CarrierConfigManager.KEY_PREFERRED_NETWORK_VALUE);
            }

            if (pref_network_mode != null && pref_network_value != null && pref_network_mode.length != 0 && pref_network_value.length != 0) {
                Log.d(LOG_TAG, "init preferred network from Settings params");
            } else {
                Log.d(LOG_TAG, "init preferred network from default config");
                //[11086878] The preferred network modes defined by T2M begin
                final Resources res = SubscriptionManager.getResourcesForSubId(mContext, mSubId);
                pref_network_mode = res.getStringArray(R.array.preferred_network_mode_custom_choices);
                pref_network_value = res.getStringArray(R.array.preferred_network_mode_custom_choices_value);
                //[11086878] The preferred network modes defined by T2M end
            }
        }

        preference.setEntries(pref_network_mode);
        preference.setEntryValues(pref_network_value);
    }
    //Modify END by renjie.zhang FP5U-304 2024/2/23
	
    @Override
    public boolean onPreferenceChange(Preference preference, Object object) {
        if (mTelephonyManager == null) {
            return false;
        }
        final int newPreferredNetworkMode = Integer.parseInt((String) object);
        final int DDS = SubscriptionManager.getDefaultDataSubscriptionId();
        final int nDDS = MobileNetworkSettings.getNonDefaultDataSub();
        final boolean isDDS = mSubId == DDS;
        // Check UE's C_IWLAN configuration and user's current network mode selection. If C_IWLAN is
        // enabled, and the selection does not contain LTE or NR, show a dialog to disable C_IWLAN.
        boolean isCiwlanIncompatibleNetworkSelected = isCiwlanIncompatibleNetworkSelected(
                newPreferredNetworkMode);
        boolean isMsimCiwlanSupported = MobileNetworkSettings.isMsimCiwlanSupported();
        boolean currentSubCiwlanEnabled = MobileNetworkSettings.isCiwlanEnabled(mSubId);
        boolean otherSubCiwlanEnabled = isDDS ? MobileNetworkSettings.isCiwlanEnabled(nDDS) :
                MobileNetworkSettings.isCiwlanEnabled(DDS);
        Log.d(LOG_TAG, "isDDS = " + isDDS +
                ", currentSubCiwlanEnabled = " + currentSubCiwlanEnabled +
                ", otherSubCiwlanEnabled = " + otherSubCiwlanEnabled +
                ", isCiwlanIncompatibleNetworkSelected = " + isCiwlanIncompatibleNetworkSelected);
        if (isMsimCiwlanSupported) {
            if (isCiwlanIncompatibleNetworkSelected) {
                if (isDDS) {
                    if (otherSubCiwlanEnabled && currentSubCiwlanEnabled) {
                        showCiwlanWarningDialog(
                                R.string.incompatible_pref_nw_for_dds_with_ciwlan_ui_on_both);
                        return false;
                    } else if (otherSubCiwlanEnabled && !currentSubCiwlanEnabled) {
                        showCiwlanWarningDialog(
                                R.string.incompatible_pref_nw_for_dds_with_ciwlan_ui_on_ndds);
                        return false;
                    } else if (!otherSubCiwlanEnabled && currentSubCiwlanEnabled) {
                        showCiwlanWarningDialog(
                                R.string.incompatible_pref_nw_for_dds_with_ciwlan_ui_on_dds);
                        return false;
                    } else {
                        // No warning
                    }
                } else {
                    if (otherSubCiwlanEnabled && currentSubCiwlanEnabled) {
                        showCiwlanWarningDialog(
                                R.string.incompatible_pref_nw_for_ndds_with_ciwlan_ui_on_both);
                    } else if (otherSubCiwlanEnabled && !currentSubCiwlanEnabled) {
                        showCiwlanWarningDialog(
                                R.string.incompatible_pref_nw_for_ndds_with_ciwlan_ui_on_dds);
                    } else if (!otherSubCiwlanEnabled && currentSubCiwlanEnabled) {
                        showCiwlanWarningDialog(
                                R.string.incompatible_pref_nw_for_ndds_with_ciwlan_ui_on_ndds);
                    } else {
                        // No warning
                    }
                }
            }
        } else {
            if (isDDS && currentSubCiwlanEnabled && isCiwlanIncompatibleNetworkSelected) {
                showCiwlanWarningDialog(
                        R.string.incompatible_pref_nw_for_dds_with_ciwlan_ui_on_dds);
                return false;
            }
        }

        mTelephonyManager.setAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                MobileNetworkUtils.getRafFromNetworkType(newPreferredNetworkMode));

        final ListPreference listPreference = (ListPreference) preference;
        //listPreference.setSummary(getPreferredNetworkModeSummaryResId(newPreferredNetworkMode));
        setNetworkModeSummaryText(listPreference, newPreferredNetworkMode);
        return true;
    }

    private boolean isCiwlanIncompatibleNetworkSelected(int networkMode) {
        long raf = MobileNetworkUtils.getRafFromNetworkType(networkMode);
        return (LTE & raf) == 0 && (NR & raf) == 0;
    }

    private void showCiwlanWarningDialog(int dialogBodyTextId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.incompatible_pref_nw_ciwlan_dialog_title)
               .setMessage(dialogBodyTextId)
               .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                   }
               });
        builder.show();
    }

    public void init(Lifecycle lifecycle, int subId) {
        mSubId = subId;
        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneCallStateListener();
        }
        final PersistableBundle carrierConfig = mCarrierConfigCache.getConfigForSubId(mSubId);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mSubId);

        mIsGlobalCdma = mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled()
                && carrierConfig.getBoolean(CarrierConfigManager.KEY_SHOW_CDMA_CHOICES_BOOL);

        if (mAllowedNetworkTypesListener == null) {
            mAllowedNetworkTypesListener = new AllowedNetworkTypesListener(
                    mContext.getMainExecutor());
            mAllowedNetworkTypesListener.setAllowedNetworkTypesListener(
                    () -> updatePreference());
        }

        lifecycle.addObserver(this);
    }

    private void updatePreference() {
        if (mPreference != null) {
            updateState(mPreference);
        }
    }

    private int getPreferredNetworkMode() {
        if (mTelephonyManager == null) {
            Log.w(TAG, "TelephonyManager is null");
            return TelephonyManagerConstants.NETWORK_MODE_UNKNOWN;
        }
        return MobileNetworkUtils.getNetworkTypeFromRaf(
                (int) mTelephonyManager.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER));
    }

    //Modify begin by renjie.zhang FP5U-304 2024/2/23
    private void setNetworkModeSummaryText(ListPreference preference, int networkmode) {
        String summary = null;

        Log.d(LOG_TAG, "set networkmode(" + networkmode + ") summary");

        for (int index = 0; index < pref_network_value.length; index++) {
            if (pref_network_value[index].equals(String.valueOf(networkmode))){
                summary = pref_network_mode[index];
                break;
            }
        }

        if (summary != null) {
            Log.d(LOG_TAG, "summary: " + summary);
            preference.setSummary(summary);
        } else {
            preference.setSummary(getPreferredNetworkModeSummaryResId(networkmode));
        }
    }
    //Modify END by renjie.zhang FP5U-304 2024/2/23

    private int getPreferredNetworkModeSummaryResId(int NetworkMode) {
        switch (NetworkMode) {
            case TelephonyManagerConstants.NETWORK_MODE_TDSCDMA_GSM_WCDMA:
                return R.string.preferred_network_mode_tdscdma_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_TDSCDMA_GSM:
                return R.string.preferred_network_mode_tdscdma_gsm_summary;
            case TelephonyManagerConstants.NETWORK_MODE_WCDMA_PREF:
                return R.string.preferred_network_mode_wcdma_perf_summary;
            case TelephonyManagerConstants.NETWORK_MODE_GSM_ONLY:
                return R.string.preferred_network_mode_gsm_only_summary;
            case TelephonyManagerConstants.NETWORK_MODE_TDSCDMA_WCDMA:
                return R.string.preferred_network_mode_tdscdma_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_WCDMA_ONLY:
                return R.string.preferred_network_mode_wcdma_only_summary;
            case TelephonyManagerConstants.NETWORK_MODE_GSM_UMTS:
                return R.string.preferred_network_mode_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_CDMA_EVDO:
                return mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled()
                        ? R.string.preferred_network_mode_cdma_summary
                        : R.string.preferred_network_mode_cdma_evdo_summary;
            case TelephonyManagerConstants.NETWORK_MODE_CDMA_NO_EVDO:
                return R.string.preferred_network_mode_cdma_only_summary;
            case TelephonyManagerConstants.NETWORK_MODE_EVDO_NO_CDMA:
                return R.string.preferred_network_mode_evdo_only_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_TDSCDMA:
                return R.string.preferred_network_mode_lte_tdscdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_ONLY:
                return R.string.preferred_network_mode_lte_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_TDSCDMA_GSM:
                return R.string.preferred_network_mode_lte_tdscdma_gsm_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA:
                return R.string.preferred_network_mode_lte_tdscdma_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_GSM_WCDMA:
                return R.string.preferred_network_mode_lte_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_CDMA_EVDO:
                return R.string.preferred_network_mode_lte_cdma_evdo_summary;
            case TelephonyManagerConstants.NETWORK_MODE_TDSCDMA_ONLY:
                return R.string.preferred_network_mode_tdscdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                return R.string.preferred_network_mode_lte_tdscdma_cdma_evdo_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA
                        || mIsGlobalCdma
                        || MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                    return R.string.preferred_network_mode_lte_cdma_evdo_gsm_wcdma_summary;
                } else {
                    return R.string.preferred_network_mode_lte_summary;
                }
            case TelephonyManagerConstants.NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                return R.string.preferred_network_mode_tdscdma_cdma_evdo_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_GLOBAL:
                return R.string.preferred_network_mode_cdma_evdo_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_TDSCDMA_WCDMA:
                return R.string.preferred_network_mode_lte_tdscdma_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_LTE_WCDMA:
                return R.string.preferred_network_mode_lte_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_ONLY:
                return R.string.preferred_network_mode_nr_only_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE:
                return R.string.preferred_network_mode_nr_lte_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_CDMA_EVDO:
                return R.string.preferred_network_mode_nr_lte_cdma_evdo_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA:
                return R.string.preferred_network_mode_nr_lte_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA:
                return R.string.preferred_network_mode_global_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_WCDMA:
                return R.string.preferred_network_mode_nr_lte_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_TDSCDMA:
                return R.string.preferred_network_mode_nr_lte_tdscdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM:
                return R.string.preferred_network_mode_nr_lte_tdscdma_gsm_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_TDSCDMA_WCDMA:
                return R.string.preferred_network_mode_nr_lte_tdscdma_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA:
                return R.string.preferred_network_mode_nr_lte_tdscdma_gsm_wcdma_summary;
            case TelephonyManagerConstants.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                return R.string.preferred_network_mode_nr_lte_tdscdma_cdma_evdo_gsm_wcdma_summary;
            default:
                return R.string.preferred_network_mode_global_summary;
        }
    }

    private boolean isCallStateIdle() {
        boolean callStateIdle = true;
        if (mCallState != null && mCallState != TelephonyManager.CALL_STATE_IDLE) {
            callStateIdle = false;
        }
        Log.d(LOG_TAG, "isCallStateIdle:" + callStateIdle);
        return callStateIdle;
    }

    private class PhoneCallStateListener extends PhoneStateListener {

        PhoneCallStateListener() {
            super(Looper.getMainLooper());
        }

        private TelephonyManager mTelephonyManager;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            mCallState = state;
            updateState(mPreference);
        }

        public void register(Context context, int subId) {
            mTelephonyManager = context.getSystemService(TelephonyManager.class);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                mTelephonyManager = mTelephonyManager.createForSubscriptionId(subId);
            }
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE);

        }

        public void unregister() {
            mCallState = null;
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
        }
    }
}
