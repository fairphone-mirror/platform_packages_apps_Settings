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

package com.android.settings.network.telephony;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
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
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.telephony.OperatorInfo;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.network.SubscriptionUtil;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.utils.ThreadUtils;

import com.qualcomm.qcrilhook.QcRilHookCallback;
import com.qualcomm.sysrilcmd.SysRilCmd;
import com.qualcomm.sysrilcmd.ISysRilCmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.text.TextUtils;

/**
 * "Choose network" settings UI for the Settings app.
 */
public class NetworkSelectSettings extends DashboardFragment implements
         SubscriptionsChangeListener.SubscriptionsChangeListenerClient {

    private static final String TAG = "NetworkSelectSettings";

    private static final int EVENT_SET_NETWORK_SELECTION_MANUALLY_DONE = 1;
    private static final int EVENT_NETWORK_SCAN_RESULTS = 2;
    private static final int EVENT_NETWORK_SCAN_ERROR = 3;
    private static final int EVENT_NETWORK_SCAN_COMPLETED = 4;

    private static final String PREF_KEY_NETWORK_OPERATORS = "network_operators_preference";
    private static final int MIN_NUMBER_OF_SCAN_REQUIRED = 2;

    @VisibleForTesting
    PreferenceCategory mPreferenceCategory;
    @VisibleForTesting
    NetworkOperatorPreference mSelectedPreference;
    private View mProgressHeader;
    private Preference mStatusMessagePreference;
    @VisibleForTesting
    List<CellInfo> mCellInfoList;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    @VisibleForTesting
    TelephonyManager mTelephonyManager;
    SubscriptionManager mSubscriptionManager;
    private SubscriptionsChangeListener mSubscriptionsChangeListener;
    private List<String> mForbiddenPlmns;
    private boolean mShow4GForLTE = false;
    private NetworkScanHelper mNetworkScanHelper;
    private final ExecutorService mNetworkScanExecutor = Executors.newFixedThreadPool(1);
    private MetricsFeatureProvider mMetricsFeatureProvider;
    private boolean mUseNewApi;
    private boolean mIsAdvancedScanSupported;
    private long mRequestIdManualNetworkSelect;
    private long mRequestIdManualNetworkScan;
    private long mWaitingForNumberOfScanResults;
    @VisibleForTesting
    boolean mIsAggregationEnabled = false;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (TelephonyUtils.isServiceConnected()) {
            mIsAdvancedScanSupported = TelephonyUtils.isAdvancedPlmnScanSupported(
                    getContext());
        } else {
            Log.d(TAG, "ExtTelephonyService is not connected!!! ");
        }
        Log.d(TAG, "mIsAdvancedScanSupported: " + mIsAdvancedScanSupported);
        mSubId = getArguments().getInt(Settings.EXTRA_SUB_ID);

        mPreferenceCategory = findPreference(PREF_KEY_NETWORK_OPERATORS);
        mStatusMessagePreference = new Preference(getContext());
        mStatusMessagePreference.setSelectable(false);
        mSelectedPreference = null;
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mSubId);
        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
        mSubscriptionsChangeListener = new SubscriptionsChangeListener(getContext(), this);
        mNetworkScanHelper = new NetworkScanHelper(
                getContext(), mTelephonyManager, mCallback, mNetworkScanExecutor);
        PersistableBundle bundle = ((CarrierConfigManager) getContext().getSystemService(
                Context.CARRIER_CONFIG_SERVICE)).getConfigForSubId(mSubId);
        if (bundle != null) {
            mShow4GForLTE = bundle.getBoolean(
                    CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL);
        }

        mMetricsFeatureProvider = FeatureFactory
                .getFactory(getContext()).getMetricsFeatureProvider();
        mIsAggregationEnabled = getContext().getResources().getBoolean(
                R.bool.config_network_selection_list_aggregation_enabled);
        Log.d(TAG, "init: mUseNewApi:" + mUseNewApi
                + " ,mIsAggregationEnabled:" + mIsAggregationEnabled);

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

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Activity activity = getActivity();
        if (activity != null) {
            mProgressHeader = setPinnedHeaderView(R.layout.progress_header)
                    .findViewById(R.id.progress_bar_animation);
            setProgressBarVisible(false);
        }
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();
        mSubscriptionsChangeListener.start();

        updateForbiddenPlmns();
        if (isProgressBarVisible()) {
            return;
        }
        if (mWaitingForNumberOfScanResults <= 0) {
            // Clear the selected preference whenever the scan starts
            mSelectedPreference = null;
            startNetworkQuery();
        }
    }

    /**
     * Update forbidden PLMNs from the USIM App
     */
    @VisibleForTesting
    void updateForbiddenPlmns() {
        final String[] forbiddenPlmns = mTelephonyManager.getForbiddenPlmns();
        mForbiddenPlmns = forbiddenPlmns != null
                ? Arrays.asList(forbiddenPlmns)
                : new ArrayList<>();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop() mWaitingForNumberOfScanResults: " + mWaitingForNumberOfScanResults);
        mSubscriptionsChangeListener.stop();
        super.onStop();
        if (mWaitingForNumberOfScanResults <= 0) {
            stopNetworkQuery();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != mSelectedPreference) {
            stopNetworkQuery();

            // Refresh the last selected item in case users reselect network.
            clearPreferenceSummary();
            if (mSelectedPreference != null) {
                // Set summary as "Disconnected" to the previously connected network
                mSelectedPreference.setSummary(R.string.network_disconnected);
            }

            mSelectedPreference = (NetworkOperatorPreference) preference;
            mSelectedPreference.setSummary(R.string.network_connecting);

            mMetricsFeatureProvider.action(getContext(),
                    SettingsEnums.ACTION_MOBILE_NETWORK_MANUAL_SELECT_NETWORK);

            setProgressBarVisible(true);
            // Disable the screen until network is manually set
            getPreferenceScreen().setEnabled(false);

            mRequestIdManualNetworkSelect = getNewRequestId();
            mWaitingForNumberOfScanResults = MIN_NUMBER_OF_SCAN_REQUIRED;
            final OperatorInfo operator = mSelectedPreference.getOperatorInfo();
            ThreadUtils.postOnBackgroundThread(() -> {
                final Message msg = mHandler.obtainMessage(
                        EVENT_SET_NETWORK_SELECTION_MANUALLY_DONE);
                msg.obj = mTelephonyManager.setNetworkSelectionModeManual(
                        operator, true /* persistSelection */);
                msg.sendToTarget();
            });
        }

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
                    getPreferenceScreen().setEnabled(true);

                    if (mSelectedPreference != null) {
                        mSelectedPreference.setSummary(isSucceed
                                ? R.string.network_connected
                                : R.string.network_could_not_connect);
                    } else {
                        Log.e(TAG, "No preference to update!");
                    }
                    break;
                case EVENT_NETWORK_SCAN_RESULTS:
                    final List<CellInfo> results = (List<CellInfo>) msg.obj;
                    if (mRequestIdManualNetworkScan < mRequestIdManualNetworkSelect) {
                        Log.d(TAG, "CellInfoList (drop): "
                                + CellInfoUtil.cellInfoListToString(new ArrayList<>(results)));
                        break;
                    }
                    mWaitingForNumberOfScanResults--;
                    if ((mWaitingForNumberOfScanResults <= 0) && (!isResumed())) {
                        stopNetworkQuery();
                    }

                    mCellInfoList = doAggregation(results);
                    Log.d(TAG, "CellInfoList size: " +  mCellInfoList.size());
                    Log.d(TAG, "CellInfoList: " + CellInfoUtil.cellInfoListToString(mCellInfoList));
                    if (mCellInfoList != null && mCellInfoList.size() != 0) {
                        // modify by T2M.zhang renjie for FP4-3074 21-10-13 begin
                        mCellInfoList = processCellInfoList(mCellInfoList);
                        mCellInfoList = removeUnusedCellInfo(mCellInfoList);
                        Log.d(TAG, "new mCellInfoList size: " +  mCellInfoList.size());
                        Log.d(TAG, "new mCellInfoList: " + CellInfoUtil.cellInfoListToString(mCellInfoList));
                        // modify by T2M.zhang renjie for FP4-3074 21-10-13 end
                        final NetworkOperatorPreference connectedPref =
                                updateAllPreferenceCategory();
                        if (connectedPref != null) {
                            // update selected preference instance into connected preference
                            if (mSelectedPreference != null) {
                                mSelectedPreference = connectedPref;
                            }
                        } else if (!getPreferenceScreen().isEnabled()) {
                            if (connectedPref == null) {
                                mSelectedPreference.setSummary(R.string.network_connecting);
                            }
                        }
                        getPreferenceScreen().setEnabled(true);
                    } else if (getPreferenceScreen().isEnabled()) {
                        addMessagePreference(R.string.empty_networks_list);
                        // keep showing progress bar, it will be stopped when error or completed
                        setProgressBarVisible(true);
                    }
                    break;

                case EVENT_NETWORK_SCAN_ERROR:
                    stopNetworkQuery();
                    Log.i(TAG, "Network scan failure " + msg.arg1 + ":"
                            + " scan request 0x" + Long.toHexString(mRequestIdManualNetworkScan)
                            + ", waiting for scan results = " + mWaitingForNumberOfScanResults
                            + ", select request 0x"
                            + Long.toHexString(mRequestIdManualNetworkSelect));
                    if (mRequestIdManualNetworkScan < mRequestIdManualNetworkSelect) {
                        break;
                    }
                    if (!getPreferenceScreen().isEnabled()) {
                        clearPreferenceSummary();
                        getPreferenceScreen().setEnabled(true);
                    } else {
                        addMessagePreference(R.string.network_query_error);
                    }
                    break;

                case EVENT_NETWORK_SCAN_COMPLETED:
                    stopNetworkQuery();
                    Log.d(TAG, "Network scan complete:"
                            + " scan request 0x" + Long.toHexString(mRequestIdManualNetworkScan)
                            + ", waiting for scan results = " + mWaitingForNumberOfScanResults
                            + ", select request 0x"
                            + Long.toHexString(mRequestIdManualNetworkSelect));
                    if (mRequestIdManualNetworkScan < mRequestIdManualNetworkSelect) {
                        break;
                    }
                    if (!getPreferenceScreen().isEnabled()) {
                        clearPreferenceSummary();
                        getPreferenceScreen().setEnabled(true);
                    } else if (mCellInfoList == null) {
                        // In case the scan timeout before getting any results
                        addMessagePreference(R.string.empty_networks_list);
                    }
                    break;
            }
            return;
        }
    };

    @VisibleForTesting
    List<CellInfo> doAggregation(List<CellInfo> cellInfoListInput) {
        if (!mIsAggregationEnabled) {
            Log.d(TAG, "no aggregation");
            return new ArrayList<>(cellInfoListInput);
        }
        ArrayList<CellInfo> aggregatedList = new ArrayList<>();
        for (CellInfo cellInfo : cellInfoListInput) {
            String plmn = CellInfoUtil.getNetworkTitle(cellInfo.getCellIdentity(),
                    CellInfoUtil.getCellIdentityMccMnc(cellInfo.getCellIdentity()));
            Class className = cellInfo.getClass();

            if (aggregatedList.stream().anyMatch(
                    item -> {
                        String itemPlmn = CellInfoUtil.getNetworkTitle(item.getCellIdentity(),
                                CellInfoUtil.getCellIdentityMccMnc(item.getCellIdentity()));
                        return itemPlmn.equals(plmn) && item.getClass().equals(className);
                    })) {
                continue;
            }
            aggregatedList.add(cellInfo);
        }
        return aggregatedList;
    }

    // modify by T2M.zhang renjie for FP4-2987 21-10-22 begin
    // modify by T2M.zhang renjie for FP4-3074 21-10-13 begin
    private List<CellInfo> processCellInfoList(List<CellInfo> cellInfoList){
        List<CellInfo> mCellInfoList = new ArrayList<>();

        for (int index = 0; index < cellInfoList.size(); index++) {
            CellInfo cellInfo = cellInfoList.get(index);
            CellIdentity cid = CellInfoUtil.getCellIdentity(cellInfo);
            mCellInfoList.add(cellInfo);
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
    private final NetworkScanHelper.NetworkScanCallback mCallback =
            new NetworkScanHelper.NetworkScanCallback() {
                public void onResults(List<CellInfo> results) {
                    final Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_RESULTS, results);
                    msg.sendToTarget();
                }

                public void onComplete() {
                    final Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED);
                    msg.sendToTarget();
                }

                public void onError(int error) {
                    final Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_ERROR, error,
                            0 /* arg2 */);
                    msg.sendToTarget();
                }
            };

    /**
     * Update the content of network operators list.
     *
     * @return preference which shows connected
     */
    @VisibleForTesting
    NetworkOperatorPreference updateAllPreferenceCategory() {
        int numberOfPreferences = mPreferenceCategory.getPreferenceCount();

        // remove unused preferences
        while (numberOfPreferences > mCellInfoList.size()) {
            numberOfPreferences--;
            mPreferenceCategory.removePreference(
                    mPreferenceCategory.getPreference(numberOfPreferences));
        }

        // update the content of preference
        NetworkOperatorPreference connectedPref = null;
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

                // modify by T2M.zhang renjie for FP4-2987 21-10-22 begin
                pref = new NetworkOperatorPreference(getPrefContext(), mSubId,
                        cellInfo, mForbiddenPlmns, mShow4GForLTE);
                // modify by T2M.zhang renjie for FP4-2987 21-10-22 end
                pref.setOrder(index);
                mPreferenceCategory.addPreference(pref);

            }
            pref.setKey(pref.getOperatorName());

            if (mCellInfoList.get(index).isRegistered()) {
                pref.setSummary(R.string.network_connected);
                connectedPref = pref;
            } else {
                pref.setSummary(null);
            }
        }

        // update selected preference instance by index
        for (int index = 0; index < mCellInfoList.size(); index++) {
            final CellInfo cellInfo = mCellInfoList.get(index);

            if ((mSelectedPreference != null) && mSelectedPreference.isSameCell(cellInfo)) {
                mSelectedPreference = (NetworkOperatorPreference)
                        (mPreferenceCategory.getPreference(index));
            }
        }

        return connectedPref;
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
    private void forceUpdateConnectedPreferenceCategory() {
        if (mTelephonyManager.getDataState() == mTelephonyManager.DATA_CONNECTED) {
            // Try to get the network registration states
            final ServiceState ss = mTelephonyManager.getServiceState();
            if (ss == null) {
                return;
            }
            final List<NetworkRegistrationInfo> networkList =
                    ss.getNetworkRegistrationInfoListForTransportType(
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (networkList == null || networkList.size() == 0) {
                return;
            }
            // Due to the aggregation of cell between carriers, it's possible to get CellIdentity
            // containing forbidden PLMN.
            // Getting current network from ServiceState is no longer a good idea.
            // Add an additional rule to avoid from showing forbidden PLMN to the user.
            if (mForbiddenPlmns == null) {
                updateForbiddenPlmns();
            }

            Set<CellIdentity> cellIdentitySet = new HashSet<CellIdentity>();
            for (NetworkRegistrationInfo regInfo : networkList) {
                Log.d(TAG, "regInfo: " + regInfo.toString());
                // There can be multiple NetworkRegistrationInfos for the same CellIdentity,
                // e.g., one each for CS and PS. In such cases, show show only one entry,
                // otherwise it would be quite confusing to the user to see multiple entries for
                // the same CellIdentity instance.
                final CellIdentity cellIdentity = regInfo.getCellIdentity();
                if (cellIdentity != null) {
                    // Add each valid CellIdentity to a HashSet so that only unique values remain.
                    cellIdentitySet.add(cellIdentity);
                }
            }

            for (CellIdentity cellIdentity : cellIdentitySet) {
                if (cellIdentity == null) {
                    continue;
                }
                final NetworkOperatorPreference pref = new NetworkOperatorPreference(
                        getPrefContext(), mSubId,cellIdentity, mForbiddenPlmns, mShow4GForLTE);
                if (pref.isForbiddenNetwork()) {
                    continue;
                }
                pref.setSummary(R.string.network_connected);
                // Update the signal strength icon, since the default signalStrength value
                // would be zero
                // (it would be quite confusing why the connected network has no signal)
                pref.setIcon(SignalStrength.NUM_SIGNAL_STRENGTH_BINS - 1);
                mPreferenceCategory.addPreference(pref);
            }
        }
    }

    /**
     * Clear all of the preference summary
     */
    private void clearPreferenceSummary() {
        int idxPreference = mPreferenceCategory.getPreferenceCount();
        while (idxPreference > 0) {
            idxPreference--;
            final NetworkOperatorPreference networkOperator = (NetworkOperatorPreference)
                    (mPreferenceCategory.getPreference(idxPreference));
            networkOperator.setSummary(null);
        }
    }

    private long getNewRequestId() {
        return Math.max(mRequestIdManualNetworkSelect,
                mRequestIdManualNetworkScan) + 1;
    }

    private boolean isProgressBarVisible() {
        if (mProgressHeader == null) {
            return false;
        }
        return (mProgressHeader.getVisibility() == View.VISIBLE);
    }

    protected void setProgressBarVisible(boolean visible) {
        if (mProgressHeader != null) {
            mProgressHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void addMessagePreference(int messageId) {
        setProgressBarVisible(false);
        mStatusMessagePreference.setTitle(messageId);
        mPreferenceCategory.removeAll();
        mPreferenceCategory.addPreference(mStatusMessagePreference);
    }

    private void startNetworkQuery() {
        setProgressBarVisible(true);
        if (mNetworkScanHelper != null) {
            mRequestIdManualNetworkScan = getNewRequestId();
            mWaitingForNumberOfScanResults = MIN_NUMBER_OF_SCAN_REQUIRED;

            mNetworkScanHelper.startNetworkScan(
                    mIsAdvancedScanSupported
                            ? NetworkScanHelper.NETWORK_SCAN_TYPE_INCREMENTAL_RESULTS
                            : NetworkScanHelper.NETWORK_SCAN_TYPE_INCREMENTAL_RESULTS_LEGACY);
        }
    }

    private void stopNetworkQuery() {
        setProgressBarVisible(false);
        if (mNetworkScanHelper != null) {
            mWaitingForNumberOfScanResults = 0;
            mNetworkScanHelper.stopNetworkQuery();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        stopNetworkQuery();
        mNetworkScanExecutor.shutdown();
        super.onDestroy();
    }
}
