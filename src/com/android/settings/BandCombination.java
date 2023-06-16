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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Message;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
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

import com.qualcomm.qcrilhook.QcRilHookCallback;
import com.qualcomm.sysrilcmd.SysRilCmd;
import com.qualcomm.sysrilcmd.ISysRilCmd;

import java.util.Arrays;
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
    private String mActualNetworkType;
    private String mLteBands, mNrBands;
    private SysRilCmd mSysRil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.bandcombo_activity);
        mBandTitle = (TextView) findViewById(R.id.bandcomb_title);
        mBandInfo = (TextView) findViewById(R.id.bandcomb_info);
        mSelectPhoneIndex = (Spinner) findViewById(R.id.phoneIndex);

        sPhoneIndexLabels = getPhoneIndexLabels(this);
        ArrayAdapter<String> phoneIndexAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, sPhoneIndexLabels);
        phoneIndexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSelectPhoneIndex.setAdapter(phoneIndexAdapter);
        mSelectedPhoneIndex = 0; //phone 0

        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int[] subIds = SubscriptionManager.getSubId(mSelectedPhoneIndex);
        if (subIds != null && subIds.length > 0) {
            subId = subIds[0];
        }

        updatePhoneIndex(mSelectedPhoneIndex, subId);
        mSysRil = new SysRilCmd(this, mQcrilHookCb);
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        updateAllFields();
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
        if (mTelephonyManager != null) {
            mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        }
    }

    private void registerPhoneStateListener() {
        if (mTelephonyManager != null) {
            mTelephonyCallback = new RadioInfoTelephonyCallback();
            mTelephonyManager.registerTelephonyCallback(new HandlerExecutor(mHandler),
                    mTelephonyCallback);
        }
    }

    private void updateAllFields() {
        // set phone index
        mSelectPhoneIndex.setSelection(mSelectedPhoneIndex, true);
        mSelectPhoneIndex.setOnItemSelectedListener(mSelectPhoneIndexHandler);

        mActualNetworkType = "NONE";
        mNrBands = "";
        mLteBands = "";
        mBandTitle.setText(getString(R.string.bandcomb) + ": " + mActualNetworkType);
        mBandInfo.setText(getString(R.string.bandreg) + ": N[" + mNrBands + "] B[" + mLteBands + "]");
    }

    private void updatePhoneIndex(int phoneIndex, int subId) {
        Log.d(TAG, "update phoneid: " + phoneIndex + " subid: " + subId);

        // update the subId
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            mTelephonyManager = TelephonyManager.from(this).createForSubscriptionId(subId);
        } else {
            mTelephonyManager = null;
        }
    }

    private String ArrToString(int bands[]) {
        StringBuilder sb = new StringBuilder();
        if (bands != null && bands.length > 0) {
            for (int i = 0; i < bands.length; i++) {
                sb.append(bands[i]);
                if (i < bands.length - 1) {
                    sb.append(",");
                }
            }
        }

        return sb.toString();
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
                    Log.d(TAG, "onItemSelected");


                    if (pos >= 0 && pos <= sPhoneIndexLabels.length - 1) {
                        // the array position is equal to the phone index
                        int phoneIndex = pos;

                        // getSubId says it takes a slotIndex, but it actually takes a phone index
                        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                        int[] subIds = SubscriptionManager.getSubId(phoneIndex);
                        if (subIds != null && subIds.length > 0) {
                            subId = subIds[0];
                        }

                        Log.d(TAG, "update phoneid: " + phoneIndex + " subid: " + subId);
                        mSelectedPhoneIndex = phoneIndex;
                        unregisterPhoneStateListener();
                        updatePhoneIndex(phoneIndex, subId);
                        updateAllFields();
                        registerPhoneStateListener();
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
            Log.d(TAG, "onCellInfoChanged: " + arrayCi);
            updateBand();
        }

        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            Log.d(TAG, "onDisplayInfoChanged: " + displayInfo);
            mTelephonyDisplayInfo = displayInfo;
            updateBand();
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private void updateBand() {
        updateNetworkType();
        updateCellInfo();
        mBandTitle.setText(getString(R.string.bandcomb) + ": " + mActualNetworkType);
        mBandInfo.setText(getString(R.string.bandreg) + ": N[" + mNrBands + "] B[" + mLteBands + "]");
    }

    private void updateNetworkType() {
        final int overrideNetworkType = mTelephonyDisplayInfo == null ?
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE :
                mTelephonyDisplayInfo.getOverrideNetworkType();
        if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
                || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA) {
            // check it in getCurrentOverrideNetworkType()
            mActualNetworkType = "ENDC";
        } else if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA) {
            mActualNetworkType = "CA";
        } else {
            mActualNetworkType = "NONE";
        }

        Log.d(TAG, "mActualNetworkType: " + mActualNetworkType);
    }

    private void updateCellInfo() {
        boolean getSCell = true;
        mLteBands = "";
        mNrBands = "";
        List<CellInfo> cells = mTelephonyManager.getAllCellInfo();
        for (final CellInfo cell : cells) {
            if (cell instanceof CellInfoLte) {
                CellIdentityLte cellid = (CellIdentityLte) cell.getCellIdentity();
                int bands[] = cellid.getBands();
                if (bands != null && bands.length > 0) {
                    mLteBands += ArrToString(bands) + ",";
                }
            } else if (cell instanceof CellInfoNr) {
                CellIdentityNr cellid = (CellIdentityNr) cell.getCellIdentity();
                int bands[] = cellid.getBands();
                if (bands != null && bands.length > 0) {
                    mNrBands += ArrToString(bands) + ",";
                }
            }
        }

        if (getSCell) {
            try {
                Log.d(TAG, "get scell band");
                String str = mSysRil.getDBStringValByPhoneid(ISysRilCmd.RIL_SUB_CMD_STRING_SCELL_INFO, mSelectedPhoneIndex);
                Log.d(TAG, "scell: " + str);
                String str_arr[] = str.split(",");
                mActualNetworkType = "CA";
                if (!mLteBands.isEmpty()) {
                    Log.d(TAG, "lte scell: " + str_arr[0]);
                    mLteBands += str_arr[0];
                }
                if (!mNrBands.isEmpty()) {
                    Log.d(TAG, "nr scell: " + str_arr[0]);
                    mNrBands += str_arr[0];
                }
            } catch (Exception e) {
                Log.d(TAG, "failed to get secell band: " + e.toString());
            }
        }
    }
}
