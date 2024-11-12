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
 * Copyright (c) 2022-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.settings.network.telephony;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.annotations.Initializer;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.flags.Flags;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.network.telephony.scan.NetworkScanRepository;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.utils.ThreadUtils;

import com.google.common.collect.ImmutableList;

import kotlin.Unit;

import kotlinx.coroutines.Job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import android.text.TextUtils;

import com.qualcomm.qcrilhook.QcRilHookCallback;
import com.qualcomm.sysrilcmd.SysRilCmd;
import com.qualcomm.sysrilcmd.ISysRilCmd;

import android.telephony.AccessNetworkConstants;

/**
 * "Choose network" settings UI for the Settings app.
 */
@Keep
public class NetworkSelectSettings extends DashboardFragment implements
         SubscriptionsChangeListener.SubscriptionsChangeListenerClient {

    private static final String TAG = "NetworkSelectSettings";

    private static final int EVENT_SET_NETWORK_SELECTION_MANUALLY_DONE = 1;

    private static final String PREF_KEY_NETWORK_OPERATORS = "network_operators_preference";
    private static final String PREF_KEY_ERROR_MSG = "error_msg_preference";

    private PreferenceCategory mPreferenceCategory;
    private PreferenceCategory mErrorMsgCategory;
    @VisibleForTesting
    NetworkOperatorPreference mSelectedPreference;
    NetworkOperatorPreference mConnectedPreference;
    private View mProgressHeader;
    private Preference mStatusMessagePreference;
    private Preference mErrorMsgPreference;
    @VisibleForTesting
    @NonNull
    List<CellInfo> mCellInfoList = ImmutableList.of();
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private TelephonyManager mTelephonyManager;
    SubscriptionManager mSubscriptionManager;
    private SubscriptionsChangeListener mSubscriptionsChangeListener;
    private SatelliteManager mSatelliteManager;
    private CarrierConfigManager mCarrierConfigManager;
    private List<String> mForbiddenPlmns;
    private boolean mShow4GForLTE = false;
    private final ExecutorService mNetworkScanExecutor = Executors.newFixedThreadPool(1);
    private MetricsFeatureProvider mMetricsFeatureProvider;
    private boolean mIsAdvancedScanSupported;
    private CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;
    private AtomicBoolean mShouldFilterOutSatellitePlmn = new AtomicBoolean();

    private NetworkScanRepository mNetworkScanRepository;
    @Nullable
    private Job mNetworkScanJob = null;

    private NetworkSelectRepository mNetworkSelectRepository;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        onCreateInitialization();
    }

    @Keep
    @VisibleForTesting
    @Initializer
    protected void onCreateInitialization() {
        Context context = getContext();

        if (TelephonyUtils.isServiceConnected()) {
            mIsAdvancedScanSupported = TelephonyUtils.isAdvancedPlmnScanSupported(
                    getContext());
        } else {
            Log.d(TAG, "ExtTelephonyService is not connected!!! ");
        }
        Log.d(TAG, "mIsAdvancedScanSupported: " + mIsAdvancedScanSupported);
        mSubId = getSubId();

        mPreferenceCategory = getPreferenceCategory(PREF_KEY_NETWORK_OPERATORS);
        mErrorMsgCategory = getPreferenceCategory(PREF_KEY_ERROR_MSG);
        mErrorMsgPreference = new Preference(getContext());
        mErrorMsgPreference.setSelectable(false);
        mStatusMessagePreference = new Preference(context);
        mStatusMessagePreference.setSelectable(false);
        mSelectedPreference = null;
        mConnectedPreference = null;
        mTelephonyManager = getTelephonyManager(context, mSubId);
        mSatelliteManager = getSatelliteManager(context);
        mCarrierConfigManager = getCarrierConfigManager(context);
        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
        mSubscriptionsChangeListener = new SubscriptionsChangeListener(getContext(), this);
        PersistableBundle bundle = mCarrierConfigManager.getConfigForSubId(mSubId,
                CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL,
                CarrierConfigManager.KEY_REMOVE_SATELLITE_PLMN_IN_MANUAL_NETWORK_SCAN_BOOL);
        mShow4GForLTE = bundle.getBoolean(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL,
                false);
        mShouldFilterOutSatellitePlmn.set(bundle.getBoolean(
                CarrierConfigManager.KEY_REMOVE_SATELLITE_PLMN_IN_MANUAL_NETWORK_SCAN_BOOL,
                true));

        mMetricsFeatureProvider = getMetricsFeatureProvider(context);

        mCarrierConfigChangeListener =
                (slotIndex, subId, carrierId, specificCarrierId) -> handleCarrierConfigChanged(
                        subId);
        mCarrierConfigManager.registerCarrierConfigChangeListener(mNetworkScanExecutor,
                mCarrierConfigChangeListener);
        mNetworkScanRepository = new NetworkScanRepository(context, mSubId);
        mNetworkSelectRepository = new NetworkSelectRepository(context, mSubId);
        mSubscriptionsChangeListener.start();
        // add by T2M.dengxiangyu for FP4-2987 2022-01-05
        mSysRil = new SysRilCmd(getContext(), mQcrilHookCb);
    }

	    // add by T2M.dengxiangyu for FP4-2987 2022-01-05 begin
    private SysRilCmd mSysRil;
    private QcRilHookCallback mQcrilHookCb = new QcRilHookCallback() {
        @Override
        public void onQcRilHookReady() {
            Log.d(TAG, "onQcRilHookReady");
        }

        @Override
        public void onQcRilHookDisconnected() {
            Log.d(TAG, "onQcRilHookDisconnected");
        }
    };
    // add by T2M.dengxiangyu for FP4-2987 2022-01-05 end

    @Keep
    @VisibleForTesting
    protected PreferenceCategory getPreferenceCategory(String preferenceKey) {
        return findPreference(preferenceKey);
    }

    @Keep
    @VisibleForTesting
    protected TelephonyManager getTelephonyManager(Context context, int subscriptionId) {
        return context.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(subscriptionId);
    }

    @Keep
    @VisibleForTesting
    protected CarrierConfigManager getCarrierConfigManager(Context context) {
        return context.getSystemService(CarrierConfigManager.class);
    }

    @Keep
    @VisibleForTesting
    protected MetricsFeatureProvider getMetricsFeatureProvider(Context context) {
        return FeatureFactory.getFeatureFactory().getMetricsFeatureProvider();
    }

    @Keep
    @VisibleForTesting
    @Nullable
    protected SatelliteManager getSatelliteManager(Context context) {
        return context.getSystemService(SatelliteManager.class);
    }

    @Keep
    @VisibleForTesting
    protected boolean isPreferenceScreenEnabled() {
        return getPreferenceScreen().isEnabled();
    }

    @Keep
    @VisibleForTesting
    protected void enablePreferenceScreen(boolean enable) {
        getPreferenceScreen().setEnabled(enable);
    }

    @Keep
    @VisibleForTesting
    protected int getSubId() {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        Intent intent = getActivity().getIntent();
        if (intent != null) {
            subId = intent.getIntExtra(Settings.EXTRA_SUB_ID,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }
        return subId;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mProgressHeader = setPinnedHeaderView(
                com.android.settingslib.widget.progressbar.R.layout.progress_header
        ).findViewById(com.android.settingslib.widget.progressbar.R.id.progress_bar_animation);
        mNetworkSelectRepository.launchUpdateNetworkRegistrationInfo(
                getViewLifecycleOwner(),
                (info) -> {
                    forceUpdateConnectedPreferenceCategory(info);
                    return Unit.INSTANCE;
                });
        launchNetworkScan();
    }

    private void launchNetworkScan() {
        setProgressBarVisible(true);
        mNetworkScanJob = mNetworkScanRepository.launchNetworkScan(getViewLifecycleOwner(),
                (networkScanResult) -> {
                    if (isPreferenceScreenEnabled()) {
                        scanResultHandler(networkScanResult);
                    }

                    return Unit.INSTANCE;
                });
    }

    /**
     * Update forbidden PLMNs from the USIM App
     */
    @Keep
    @VisibleForTesting
    protected void updateForbiddenPlmns() {
        final String[] forbiddenPlmns = mTelephonyManager.getForbiddenPlmns();
        mForbiddenPlmns = forbiddenPlmns != null
                ? Arrays.asList(forbiddenPlmns)
                : new ArrayList<>();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mSelectedPreference) {
            Log.d(TAG, "onPreferenceTreeClick: preference is mSelectedPreference. Do nothing.");
            return true;
        }
        if (!(preference instanceof NetworkOperatorPreference)) {
            Log.d(TAG, "onPreferenceTreeClick: preference is not the NetworkOperatorPreference.");
            return false;
        }

        // Need stop network scan before manual select network.
        if (mNetworkScanJob != null) {
            mNetworkScanJob.cancel(null);
            mNetworkScanJob = null;
        }

        // Refresh the last selected item in case users reselect network.
        clearPreferenceSummary();
        if (mSelectedPreference != null) {
            // Set summary as "Disconnected" to the previously selected network
            mSelectedPreference.setSummary(R.string.network_disconnected);
        } else if (mConnectedPreference != null) {
            // Set summary as "Disconnected" to the previously connected network
            mConnectedPreference.setSummary(R.string.network_disconnected);
        }

        mSelectedPreference = (NetworkOperatorPreference) preference;
        mSelectedPreference.setSummary(R.string.network_connecting);
        mConnectedPreference = mSelectedPreference;

        mMetricsFeatureProvider.action(getContext(),
                SettingsEnums.ACTION_MOBILE_NETWORK_MANUAL_SELECT_NETWORK);

        setProgressBarVisible(true);
        // Disable the screen until network is manually set
        enablePreferenceScreen(false);

        final OperatorInfo operator = mSelectedPreference.getOperatorInfo();
        ThreadUtils.postOnBackgroundThread(() -> {
            final Message msg = mHandler.obtainMessage(
                    EVENT_SET_NETWORK_SELECTION_MANUALLY_DONE);
            msg.obj = mTelephonyManager.setNetworkSelectionModeManual(
                    operator, true /* persistSelection */);
            msg.sendToTarget();
        });

        return true;
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {
    }

    @Override
    public void onSubscriptionsChanged() {
        boolean isActiveSubscriptionId = mSubscriptionManager.isActiveSubscriptionId(mSubId);
        Log.d(TAG, "onSubscriptionsChanged, mSubId: " + mSubId
                + ", isActive: " + isActiveSubscriptionId);

        if (!isActiveSubscriptionId) {
            // The current subscription is no longer active, possibly because the SIM was removed.
            // Finish the activity.
            final Activity activity = getActivity();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                Log.d(TAG, "Calling finish");
                activity.finish();
            }
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.choose_network;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MOBILE_NETWORK_SELECT;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage, msg.what: " + msg.what);
            switch (msg.what) {
                case EVENT_SET_NETWORK_SELECTION_MANUALLY_DONE:
                    final boolean isSucceed = (boolean) msg.obj;
                    setProgressBarVisible(false);
                    enablePreferenceScreen(true);

                    if (mSelectedPreference != null) {
                        mSelectedPreference.setSummary(isSucceed
                                ? R.string.network_connected
                                : R.string.network_could_not_connect);
                    } else {
                        Log.e(TAG, "No preference to update!");
                    }
                    break;
            }
        }
    };

    /* We do not want to expose carrier satellite plmns to the user when manually scan the
       cellular network. Therefore, it is needed to filter out satellite plmns from current cell
       info list  */
    @VisibleForTesting
    List<CellInfo> filterOutSatellitePlmn(List<CellInfo> cellInfoList) {
        List<String> aggregatedSatellitePlmn = getSatellitePlmnsForCarrierWrapper();
        if (!mShouldFilterOutSatellitePlmn.get() || aggregatedSatellitePlmn.isEmpty()) {
            return cellInfoList;
        }
        return cellInfoList.stream()
                .filter(cellInfo -> !aggregatedSatellitePlmn.contains(
                        CellInfoUtil.getOperatorNumeric(cellInfo.getCellIdentity())))
                .collect(Collectors.toList());
    }

    /**
     * Serves as a wrapper method for {@link SatelliteManager#getSatellitePlmnsForCarrier(int)}.
     * Since SatelliteManager is final, this wrapper enables mocking or spying of
     * {@link SatelliteManager#getSatellitePlmnsForCarrier(int)} for unit testing purposes.
     */
    @VisibleForTesting
    protected List<String> getSatellitePlmnsForCarrierWrapper() {
        if (!Flags.carrierEnabledSatelliteFlag()) {
            return new ArrayList<>();
        }

        if (mSatelliteManager != null) {
            return mSatelliteManager.getSatellitePlmnsForCarrier(mSubId);
        } else {
            Log.e(TAG, "mSatelliteManager is null, return empty list");
            return new ArrayList<>();
        }
    }

    private void handleCarrierConfigChanged(int subId) {
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(subId,
                CarrierConfigManager.KEY_REMOVE_SATELLITE_PLMN_IN_MANUAL_NETWORK_SCAN_BOOL);
        boolean shouldFilterSatellitePlmn = config.getBoolean(
                CarrierConfigManager.KEY_REMOVE_SATELLITE_PLMN_IN_MANUAL_NETWORK_SCAN_BOOL,
                true);
        if (shouldFilterSatellitePlmn != mShouldFilterOutSatellitePlmn.get()) {
            mShouldFilterOutSatellitePlmn.set(shouldFilterSatellitePlmn);
        }
    }

    // modify by T2M.zhang renjie for FP4-2987 21-10-22 begin
    // modify by T2M.zhang renjie for FP4-3074 21-10-13 begin
    private List<CellInfo> processCellInfoList(List<CellInfo> cellInfoList){
        List<CellInfo> mCellInfoList = new ArrayList<>();
        //modify by T2M.sunhuan for FP4T-550/FP4T-551 23-07-26 begin
        String operator = mTelephonyManager.getNetworkOperator(mSubId);
        Log.d(TAG, "operator = "+operator);
        //modify by T2M.sunhuan for FP4T-550/FP4T-551 23-07-26 end

        for (int index = 0; index < cellInfoList.size(); index++) {
            CellInfo cellInfo = cellInfoList.get(index);
            CellIdentity cid = CellInfoUtil.getCellIdentity(cellInfo);
            mCellInfoList.add(cellInfo);
            // modify by T2M.sunhuan for FP4T-550/FP4T-551 23-07-26
            // modify by T2M.zhang renjie for FP5V-263 24-11-12
            if (TextUtils.isEmpty(operator) || !(operator.equals("26201") || operator.equals("26202"))) {
                for (CellInfo mCellInfo:mCellInfoList){
                    if (mCellInfo.equals(cellInfo)) continue;

                    CellIdentity mCid = CellInfoUtil.getCellIdentity(mCellInfo);
                    //[BUG]-Modify-Begin by shaopan.tang 2022-12-27 [FP4S-797]Manual NW shows unobnormal
                    if (mCid.getOperatorAlphaLong().equals(cid.getOperatorAlphaLong())
                            && !TextUtils.isEmpty(mCid.getPlmn())
                            && mCid.getPlmn().equals(cid.getPlmn())){
                        if (getAccessNetworkType(cid) < getAccessNetworkType(mCid)){
                            mCellInfoList.remove(cellInfo);
                            break;
                        }else{
                            mCellInfoList.remove(mCellInfo);
                            break;
                        }
                    }
                    //[BUG]-Modify-End by shaopan.tang
                }
            }
        }
              return mCellInfoList;
    }

    private List<CellInfo> removeUnusedCellInfo(List<CellInfo> cellInfoList){

        String imsi = mTelephonyManager.getSubscriberId();
        String operator = mTelephonyManager.getNetworkOperator(mSubId);
        Log.d(TAG, "imsi = " + imsi +",operator = "+operator);

        // add by T2M.dengxiangyu for FP4-2987 2022-01-05 begin
        if (operator == null || operator.isEmpty()) {
            int phoneid = SubscriptionManager.getSlotIndex(mSubId);
            operator = mSysRil.getDBStringValByPhoneid(ISysRilCmd.RIL_SUB_CMD_STRING_RPLMN, phoneid);
            Log.d(TAG, "get rplmn[" + phoneid + "]: " + operator + " by sub: " + mSubId);
        }
        // add by T2M.dengxiangyu for FP4-2987 2022-01-05 end

        if (imsi.startsWith("23457")) {
            // display one rat for each operator.
            cellInfoList = processCellInfoList(cellInfoList);
            //remove some operator.
            for (int i = cellInfoList.size() - 1; i >= 0; i--) {
                CellInfo cellInfo = cellInfoList.get(i);
                CellIdentity cid = CellInfoUtil.getCellIdentity(cellInfo);
                /*if (cid.getOperatorAlphaLong().toString().toLowerCase().contains("o2") || cid.getOperatorAlphaShort().toString().toLowerCase().contains("o2")) {
                    cellInfoList.remove(cellInfo);
                }*/
                if (cid.getOperatorAlphaLong().toString().toLowerCase().contains("virgin") || cid.getOperatorAlphaShort().toString().toLowerCase().contains("virgin")) {
                    cellInfoList.remove(cellInfo);
                }

            }
        }else if (imsi.startsWith("23438")) {
            for (int i = cellInfoList.size() - 1; i >= 0; i--) {
                CellInfo cellInfo = cellInfoList.get(i);
                CellIdentity cid = CellInfoUtil.getCellIdentity(cellInfo);
                if (mForbiddenPlmns != null && mForbiddenPlmns.contains(getOperatorNumeric(cid))){
                    cellInfoList.remove(cellInfo);
                    continue;
                }
                if (operator.startsWith("23415")){
                    if (cid.getOperatorAlphaLong().toString().toLowerCase().contains("vodafone") || cid.getOperatorAlphaShort().toString().toLowerCase().contains("vodafone")) {
                        cellInfoList.remove(cellInfo);
                    }
                }else if (operator.startsWith("23430") || operator.startsWith("23433")){
                    if (cid.getOperatorAlphaLong().toString().toLowerCase().contains("ee") || cid.getOperatorAlphaShort().toString().toLowerCase().contains("ee")) {
                        cellInfoList.remove(cellInfo);
                    }
                }
                //[BUG]-Modify-Begin by shaopan.tang 2022-12-22 [FP4S-686]Wrong behaivor for manual network selection
                else if (operator.startsWith("23410")){
                    if (cid.getOperatorAlphaLong().toString().toLowerCase().contains("o2") || cid.getOperatorAlphaShort().toString().toLowerCase().contains("o2")) {
                        cellInfoList.remove(cellInfo);
                    }else if (cid.getOperatorAlphaLong().toString().toLowerCase().contains("vodafone") || cid.getOperatorAlphaShort().toString().toLowerCase().contains("voda")) {
                        cellInfoList.remove(cellInfo);
                    }
                }
                //[BUG]-Modify-End by shaopan.tang
            }

        }

        return cellInfoList;
    }

    private int getAccessNetworkType(CellIdentity mCellId) {
        int cellInfoType = mCellId == null ? CellInfo.TYPE_UNKNOWN : mCellId.getType();
        int ant;
        switch (cellInfoType) {
            case CellInfo.TYPE_GSM:     ant = AccessNetworkConstants.AccessNetworkType.GERAN;
                break;
            case CellInfo.TYPE_LTE:     ant = AccessNetworkConstants.AccessNetworkType.EUTRAN;
                break;
            case CellInfo.TYPE_WCDMA:   // fallthrough
            case CellInfo.TYPE_TDSCDMA: ant = AccessNetworkConstants.AccessNetworkType.UTRAN;
                break;
            case CellInfo.TYPE_NR:      ant = AccessNetworkConstants.AccessNetworkType.NGRAN;
                break;
            default:                    ant = AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        }

        return ant;
    }
    // modify by T2M.zhang renjie for FP4-3074 21-10-13 end

    /**
     * Operator numeric of this cell
     */
    public String getOperatorNumeric(CellIdentity cellId) {
        if (cellId == null) {
            return null;
        }
        if (cellId instanceof CellIdentityGsm) {
            return ((CellIdentityGsm) cellId).getMobileNetworkOperator();
        }
        if (cellId instanceof CellIdentityWcdma) {
            return ((CellIdentityWcdma) cellId).getMobileNetworkOperator();
        }
        if (cellId instanceof CellIdentityTdscdma) {
            return ((CellIdentityTdscdma) cellId).getMobileNetworkOperator();
        }
        if (cellId instanceof CellIdentityLte) {
            return ((CellIdentityLte) cellId).getMobileNetworkOperator();
        }
        if (cellId instanceof CellIdentityNr) {
            final String mcc = ((CellIdentityNr) cellId).getMccString();
            if (mcc == null) {
                return null;
            }
            return mcc.concat(((CellIdentityNr) cellId).getMncString());
        }
        return null;
    }
    // modify by T2M.zhang renjie for FP4-2987 21-10-22 end

    @VisibleForTesting
    protected void scanResultHandler(NetworkScanRepository.NetworkScanResult results) {
        if (isFinishingOrDestroyed()) {
            Log.d(TAG, "scanResultHandler: activity isFinishingOrDestroyed, directly return");
            return;
        }

        mCellInfoList = filterOutSatellitePlmn(results.getCellInfos());
        Log.d(TAG, "CellInfoList: " + CellInfoUtil.cellInfoListToString(mCellInfoList));
        if (mCellInfoList != null && mCellInfoList.size() != 0) {
            // modify by T2M.zhang renjie for FP4-3074 21-10-13 begin
            mCellInfoList = processCellInfoList(mCellInfoList);
            mCellInfoList = removeUnusedCellInfo(mCellInfoList);
            Log.d(TAG, "new mCellInfoList size: " +  mCellInfoList.size());
            Log.d(TAG, "new mCellInfoList: " + CellInfoUtil.cellInfoListToString(mCellInfoList));
            // modify by T2M.zhang renjie for FP4-3074 21-10-13 end
        }
        updateAllPreferenceCategory();
        NetworkScanRepository.NetworkScanState state = results.getState();
        if (state == NetworkScanRepository.NetworkScanState.ERROR) {
            addMessagePreference(R.string.network_query_error);
        } else if (mCellInfoList.isEmpty()) {
            addMessagePreference(R.string.empty_networks_list);
        }
        // keep showing progress bar, it will be stopped when error or completed
        setProgressBarVisible(state == NetworkScanRepository.NetworkScanState.ACTIVE);
    }

    @Keep
    @VisibleForTesting
    protected NetworkOperatorPreference createNetworkOperatorPreference(CellInfo cellInfo) {
        if (mForbiddenPlmns == null) {
            updateForbiddenPlmns();
        }
        NetworkOperatorPreference preference =
            new NetworkOperatorPreference(getPrefContext(),
                mForbiddenPlmns, mShow4GForLTE,
                MobileNetworkUtils.getAccessMode(getPrefContext().getApplicationContext(),
                        mTelephonyManager.getSlotIndex()));
        if (!DomesticRoamUtils.isFeatureEnabled(getPrefContext())) {
            preference.updateCell(cellInfo);
        }
        return preference;
    }

    /**
     * Update the content of network operators list.
     */
    private void updateAllPreferenceCategory() {
        int numberOfPreferences = mPreferenceCategory.getPreferenceCount();

        // remove unused preferences
        while (numberOfPreferences > mCellInfoList.size()) {
            numberOfPreferences--;
            mPreferenceCategory.removePreference(
                    mPreferenceCategory.getPreference(numberOfPreferences));
        }

        // update the content of preference
        for (int index = 0; index < mCellInfoList.size(); index++) {
            final CellInfo cellInfo = mCellInfoList.get(index);

            NetworkOperatorPreference pref = null;
            if (index < numberOfPreferences) {
                final Preference rawPref = mPreferenceCategory.getPreference(index);
                if (rawPref instanceof NetworkOperatorPreference) {
                    // replace existing preference
                    pref = (NetworkOperatorPreference) rawPref;
                    pref.updateCell(cellInfo);
                } else {
                    mPreferenceCategory.removePreference(rawPref);
                }
            }
            if (pref == null) {
                // add new preference
                pref = createNetworkOperatorPreference(cellInfo);
                pref.setOrder(index);

                if (DomesticRoamUtils.isFeatureEnabled(getPrefContext())) {
                    pref.setSubId(mSubId);
                    pref.updateCell(cellInfo);
                }

                mPreferenceCategory.addPreference(pref);
            }
            pref.setKey(pref.getOperatorName());

            if (mCellInfoList.get(index).isRegistered()) {
                pref.setSummary(R.string.network_connected);
            } else {
                pref.setSummary(null);
            }
        }
    }

    /**
     * Config the network operator list when the page was created. When user get
     * into this page, the device might or might not have data connection.
     * - If the device has data:
     * 1. use {@code ServiceState#getNetworkRegistrationInfoList()} to get the currently
     * registered cellIdentity, wrap it into a CellInfo;
     * 2. set the signal strength level as strong;
     * 3. get the title of the previously connected network operator, since the CellIdentity
     * got from step 1 only has PLMN.
     * - If the device has no data, we will remove the connected network operators list from the
     * screen.
     */
    private void forceUpdateConnectedPreferenceCategory(
            NetworkSelectRepository.NetworkRegistrationAndForbiddenInfo info) {
        mPreferenceCategory.removeAll();
        for (NetworkRegistrationInfo regInfo : info.getNetworkList()) {
            final CellIdentity cellIdentity = regInfo.getCellIdentity();
            if (cellIdentity == null) {
                continue;
            }
            final NetworkOperatorPreference pref = new NetworkOperatorPreference(
                    getPrefContext(), info.getForbiddenPlmns(), mShow4GForLTE,
                    MobileNetworkUtils.getAccessMode(getPrefContext().getApplicationContext(),
                            mTelephonyManager.getSlotIndex()));
            pref.updateCell(null, cellIdentity);
            if (pref.isForbiddenNetwork()) {
                continue;
            }
            pref.setSummary(R.string.network_connected);
            // Update the signal strength icon, since the default signalStrength value
            // would be zero
            // (it would be quite confusing why the connected network has no signal)
            pref.setIcon(SignalStrength.NUM_SIGNAL_STRENGTH_BINS - 1);
            mPreferenceCategory.addPreference(pref);
            break;
        }
    }

    /**
     * Clear all of the preference summary
     */
    private void clearPreferenceSummary() {
        int idxPreference = mPreferenceCategory.getPreferenceCount();
        while (idxPreference > 0) {
            idxPreference--;
            final Preference networkOperator = mPreferenceCategory.getPreference(idxPreference);
            networkOperator.setSummary(null);
        }
    }

    protected void setProgressBarVisible(boolean visible) {
        if (mProgressHeader != null) {
            mProgressHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void addMessagePreference(int messageId) {
        mStatusMessagePreference.setTitle(messageId);
        mPreferenceCategory.removeAll();
        mPreferenceCategory.addPreference(mStatusMessagePreference);
    }

    private void addErrorMessagePreference(int messageId) {
        setProgressBarVisible(false);
        mErrorMsgPreference.setTitle(messageId);
        mErrorMsgCategory.addPreference(mErrorMsgPreference);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        mSubscriptionsChangeListener.stop();
        mNetworkScanExecutor.shutdown();
        super.onDestroy();
    }
}
