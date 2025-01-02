/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Message;
import android.telephony.Annotation;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;

import androidx.annotation.NonNull;

import com.qualcomm.qcrilhook.QcRilHookCallback;
import com.qualcomm.sysrilcmd.SysRilCmd;
import com.qualcomm.sysrilcmd.ISysRilCmd;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.List;

public class BandCombination extends Activity {
    private static final String TAG = "BandCombination";
    private TextView mBandInfo;
    private TextView mBandTitle;
    private Spinner mSelectPhoneIndex;
    private static String[] sPhoneIndexLabels;
    private int mSelectedPhoneIndex;
    private TelephonyManager mTelephonyManager;
    public TelephonyDisplayInfo mTelephonyDisplayInfo;
    private String mLtePrimaryBnad, mLteSecondaryBand;
    private String mNrPrimaryBand, mNrSecondaryBand;
    private String mLteRAT, mNrRAT;
    private String mIsEndc;
    private SysRilCmd mSysRil;
    private final int MSG_UPDATE_CELL_INFO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.bandcombo_activity);
        mBandTitle = (TextView) findViewById(R.id.bandcomb_title);
        mBandInfo = (TextView) findViewById(R.id.bandcomb_info);
        mSelectPhoneIndex = (Spinner) findViewById(R.id.bandcomb_phoneIndex);

        mSelectedPhoneIndex = 0;
        sPhoneIndexLabels = getPhoneIndexLabels(this);
        ArrayAdapter<String> phoneIndexAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, sPhoneIndexLabels);
        phoneIndexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSelectPhoneIndex.setAdapter(phoneIndexAdapter);
        mSelectPhoneIndex.setOnItemSelectedListener(mSelectPhoneIndexHandler);
        mSelectPhoneIndex.setSelection(mSelectedPhoneIndex);

        mSysRil = new SysRilCmd(this, mQcrilHookCb);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        initBandCombVar();
        initBandCombView();
        registerPhoneStateListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        unregisterPhoneStateListener();
    }

    @Override
    public void onStart() {
        super.onStart();
    }
    @Override
    public void onStop() {
        super.onStop();
    }

    private void unregisterPhoneStateListener() {
        Log.d(TAG, "unregisterPhoneStateListener[" + mSelectedPhoneIndex + "]");
        if (mTelephonyManager != null) {
            mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        }
    }

    private void registerPhoneStateListener() {
        int subId = SubscriptionManager.getSubscriptionId(mSelectedPhoneIndex);
        Log.d(TAG, "registerPhoneStateListener[" + mSelectedPhoneIndex + "][" + subId + "]");
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            mTelephonyManager = getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
            mTelephonyCallback = new RadioInfoTelephonyCallback();
            mTelephonyManager.registerTelephonyCallback(new HandlerExecutor(mHandler),
                    mTelephonyCallback);
        }
    }

    private static String[] getPhoneIndexLabels(Context context) {
        TelephonyManager tm = TelephonyManager.from(context);
        int phones = tm.getActiveModemCount();
        String[] labels = new String[phones];
        for (int i = 0; i < phones; i++) {
            labels[i] = "Phone " + i;
        }
        return labels;
    }

    AdapterView.OnItemSelectedListener mSelectPhoneIndexHandler =
            new AdapterView.OnItemSelectedListener() {

                public void onItemSelected(AdapterView parent, View v, int pos, long id) {
                    Log.d(TAG, "onItemSelected: " + pos);

                    if (pos >= 0 && pos <= sPhoneIndexLabels.length - 1) {
                        if (mSelectedPhoneIndex != pos) {
                            unregisterPhoneStateListener();
                            initBandCombVar();
                            initBandCombView();
                            mSelectedPhoneIndex = pos;
                            registerPhoneStateListener();
                        }
                    }
                }

                public void onNothingSelected(AdapterView parent) {
                }
            };

    private TelephonyCallback mTelephonyCallback = new RadioInfoTelephonyCallback();
    private class RadioInfoTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.CellInfoListener,
            TelephonyCallback.DisplayInfoListener {
        @Override
        public void onCellInfoChanged(List<CellInfo> arrayCi) {
            int subId = mTelephonyManager.getSubscriptionId();
            Log.d(TAG, "onCellInfoChanged[" + SubscriptionManager.getPhoneId(subId) + "]: " + arrayCi);
            updateCellInfo();
        }

        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            int subId = mTelephonyManager.getSubscriptionId();
            Log.d(TAG, "onDisplayInfoChanged[" + SubscriptionManager.getPhoneId(subId) + "]: " + displayInfo);
            mTelephonyDisplayInfo = displayInfo;
            updateCellInfo();
        }
    }

    private class CellInfoResultsCallback extends TelephonyManager.CellInfoCallback {

        @Override
        public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
            int subId = mTelephonyManager.getSubscriptionId();
            Log.d(TAG, "onCellInfo[" + SubscriptionManager.getPhoneId(subId) + "]");

            initBandCombVar();
            initBandCombView();
            if (cellInfo != null && cellInfo.size() > 0) {
                Log.d(TAG, "size = " + cellInfo.size() + " list = " + cellInfo);
                handleCellInfoUpdate(cellInfo);
            }
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case MSG_UPDATE_CELL_INFO:
                    mBandTitle.setText(getString(R.string.bandcomb) + ": " + mIsEndc);
                    mBandInfo.setText(getString(R.string.bandreg) + ":\n"
                            + (mNrRAT.isEmpty() ? "NR" : mNrRAT) + " [" + mNrPrimaryBand + " " + mNrSecondaryBand + "]\n"
                            + (mLteRAT.isEmpty() ? "LTE" : mLteRAT) + " [" + mLtePrimaryBnad + " " + mLteSecondaryBand + "]");
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private void updateCellInfo() {
        if (mTelephonyManager == null) {
            Log.d(TAG, "invalid TelephonyManager");
            return;
        }

        CellInfoResultsCallback resultsCallback = new CellInfoResultsCallback();
        mTelephonyManager.requestCellInfoUpdate(new HandlerExecutor(mHandler), resultsCallback);
        // set UI elements from non-main threads will cause CalledFromWrongThreadException
        // so use mHandler in main thread or send message to main thread handler if it's busy
        //mTelephonyManager.requestCellInfoUpdate(mSimpleExecutor, resultsCallback);
    }

    private void handleCellInfoUpdate(List<CellInfo> cellInfos) {
        int subId = SubscriptionManager.getSubscriptionId(mSelectedPhoneIndex);
        final int networkType = mTelephonyManager == null ?
                TelephonyManager.NETWORK_TYPE_UNKNOWN :
                mTelephonyManager.getDataNetworkType(subId);
        final int overrideNetworkType = mTelephonyDisplayInfo == null ?
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE :
                mTelephonyDisplayInfo.getOverrideNetworkType();
        final boolean isOverrideNwTypeNrAdvancedOrNsa =
                overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                        || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA;
        Log.d(TAG, "handleCellInfoUpdate[" + mSelectedPhoneIndex
                + "], networkType = " + getNetworkTypeName(networkType)
                + ", nr overrideNetworkType: " + overrideNetworkType);

        List<Integer> scells = new ArrayList<>();
        for (final CellInfo cell : cellInfos) {
            if (cell instanceof CellInfoLte) {
                mLteRAT = getNetworkTypeName(networkType);
                CellInfoLte lte = (CellInfoLte) cell;
                int bands[] = lte.getCellIdentity().getBands();
                if (bands != null && bands.length > 0) {
                    if (lte.isRegistered()) {
                        mLtePrimaryBnad += "BAND" + String.valueOf(bands[0]);
                    } else {
                        boolean repeatSCell = false;
                        for (final Integer scell : scells) {
                            if (scell == bands[0]) {
                                repeatSCell = true;
                                break;
                            }
                        }
                        if (!repeatSCell) {
                            scells.add(bands[0]);
                            mLteSecondaryBand += "BAND" + String.valueOf(bands[0]) + " ";
                        }
                    }
                }
            } else if (cell instanceof CellInfoNr) {
                CellInfoNr nr = (CellInfoNr) cell;
                int bands[] = ((CellIdentityNr) nr.getCellIdentity()).getBands();
                if (bands != null && bands.length > 0) {
                    mNrPrimaryBand += "BAND" + bands[0];
                }

                if (nr.isRegistered()) {
                    mIsEndc = "not ENDC";
                    mNrRAT = "NR SA";
                } else if (isOverrideNwTypeNrAdvancedOrNsa
                        && (networkType == TelephonyManager.NETWORK_TYPE_LTE
                        || networkType == TelephonyManager.NETWORK_TYPE_LTE_CA)) {
                    Log.d(TAG, "ENDC");
                    mIsEndc = "ENDC";
                    mNrRAT = "NR NSA";
                }
            }
        }

        // LTE SCELL Info can get from CELL_INFO_LIST
        // get SCELL Info through SYSRIL only when network type is NR SA
        if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
            try {
                Log.d(TAG, "get nr scell band");
                String str = mSysRil.getDBStringValByPhoneid(
                        ISysRilCmd.RIL_SUB_CMD_STRING_SCELL_INFO, mSelectedPhoneIndex);
                Log.d(TAG, "scell: " + str);
                String str_arr[] = str.split(",");
                if (str_arr != null && str_arr.length > 0) {
                    if (Integer.valueOf(str_arr[0]) != 0) {
                        mNrSecondaryBand = "BAND" + str_arr[0];
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "failed to get nr secell band: " + e.toString());
            }
        }

        mBandTitle.setText(getString(R.string.bandcomb) + ": " + mIsEndc);
        mBandInfo.setText(getString(R.string.bandreg) + ":\n"
                + (mNrRAT.isEmpty() ? "NR" : mNrRAT) + " [" + mNrPrimaryBand + " " + mNrSecondaryBand + "]\n"
                + (mLteRAT.isEmpty() ? "LTE" : mLteRAT) + " [" + mLtePrimaryBnad + " " + mLteSecondaryBand + "]");

        // set UI elements from non-main threads will cause CalledFromWrongThreadException
        // so send message to main thread handler
        //Message msg = mHandler.obtainMessage(MSG_UPDATE_CELL_INFO, null);
        //msg.sendToTarget();
    }

    private QcRilHookCallback mQcrilHookCb = new QcRilHookCallback() {
        @Override
        public void onQcRilHookReady() {
            Log.d(TAG, " onQcRilHookReady");
        }

        @Override
        public void onQcRilHookDisconnected() {
            Log.d(TAG, " onQcRilHookDisconnected");
        }
    };

    private Executor mSimpleExecutor = new Executor() {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private String getNetworkTypeName(@Annotation.NetworkType int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return "CDMA - EvDo rev. 0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return "CDMA - EvDo rev. A";
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "CDMA - EvDo rev. B";
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return "CDMA - 1xRTT";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                return "LTE CA";
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return "CDMA - eHRPD";
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "iDEN";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "GSM";
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return "TD_SCDMA";
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return "IWLAN";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "NR SA";
            default:
                return "UNKNOWN";
        }
    }

    private void initBandCombVar() {
        mLtePrimaryBnad = "";
        mLteSecondaryBand = "";
        mLteRAT = "";
        mNrPrimaryBand = "";
        mNrSecondaryBand = "";
        mNrRAT = "";
        mIsEndc = "not ENDC";
    }

    private void initBandCombView() {
        mBandTitle.setText(getString(R.string.bandcomb) + ": ");
        mBandInfo.setText(getString(R.string.bandreg) + ": ");
    }
}
