/**
 * Copyright (c) 2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package com.android.settings.network.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.os.RemoteException;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.telephony.TelephonyIntents;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import com.android.settings.R;
import com.android.settings.network.AllowedNetworkTypesListener;
import com.android.settings.network.telephony.MobileNetworkUtils;
import com.qti.extphone.Client;
import com.qti.extphone.ExtPhoneCallbackBase;
import com.qti.extphone.ExtTelephonyManager;
import com.qti.extphone.NrConfig;
import com.qti.extphone.ServiceCallback;
import com.qti.extphone.Status;
import com.qti.extphone.Token;

/**
 * Preference controller for "Enabled 5GSA Switch"
*/
public class Enabled5GNRModePreferenceController extends TelephonyTogglePreferenceController
         implements LifecycleObserver, OnStart, OnStop {
    private static final String TAG = "Enable5gSA";

    private int mSlotId;
    private int mSubId;
    private SubscriptionManager mSubscriptionManager;

    private static final int NR_MODE_NSA_SA = 0;
    private static final int NR_MODE_NSA = 1;
    private static final int NR_MODE_SA = 2;
    private static final int EVENT_SET_NR_CONFIG_STATUS = 101;
    private static final int EVENT_GET_NR_CONFIG_STATUS = 102;

    private Client mClient;
    private Context mContext;
    private ExtTelephonyManager mExtTelephonyManager;
    private String mPackageName;
    private SharedPreferences mSharedPreferences;
    private TelephonyManager mTelephonyManager;

    private PreferenceScreen mPreferenceScreen;
    private boolean mServiceConnected;
    private int userPrefNrConfig;
    Preference mPreference;

    private ExtPhoneCallbackBase mCallback = new ExtPhoneCallbackBase() {
        @Override
        public void onSetNrConfig(int slotId, Token token, Status status) throws
                RemoteException {
            Log.d(TAG, "onSetNrConfig: slotId = " + slotId + " token = " + token + " status = " +
                    status);
            if (status.get() == Status.SUCCESS) {
                updateSharedPreference(slotId, userPrefNrConfig);
            }
            mMainThreadHandler.sendMessage(mMainThreadHandler
                    .obtainMessage(EVENT_SET_NR_CONFIG_STATUS, slotId, -1));
        }

        @Override
        public void onNrConfigStatus(int slotId, Token token, Status status, NrConfig nrConfig)
                throws RemoteException {
            Log.d(TAG, "onNrConfigStatus: slotId = " + slotId + " token = " + token + " status = " +
                    status + " nrConfig = " + nrConfig);
            if (status.get() == Status.SUCCESS) {
                int nrconfigmode = nrConfig.get();
                if ((nrconfigmode == NR_MODE_NSA) || (nrconfigmode == NR_MODE_SA)){
                    updateSharedPreference(slotId, nrConfig.get());
                    mMainThreadHandler.sendMessage(mMainThreadHandler
                             .obtainMessage(EVENT_GET_NR_CONFIG_STATUS, slotId, -1));
                } else if (nrconfigmode == NR_MODE_NSA_SA){
                    if (mServiceConnected && mClient != null) {
                        userPrefNrConfig = NR_MODE_SA;
                        mExtTelephonyManager.setNrConfig(mSlotId, new NrConfig(NR_MODE_SA), mClient);
                        Log.d(TAG, "setNrConfig to SA only if current is NR_MODE_NSA_SA ");
                    }
                }

            }
        }
    };

    private Handler mMainThreadHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage msg.what = " + msg.what);
            switch (msg.what) {
                case EVENT_SET_NR_CONFIG_STATUS:
                case EVENT_GET_NR_CONFIG_STATUS: {
                    int slotId = msg.arg1;
                    update();
                    break;
                }
            }
        }
    };

    private ServiceCallback mServiceCallback = new ServiceCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "ExtTelephonyService connected");
            mServiceConnected = true;
            mClient = mExtTelephonyManager.registerCallback(mPackageName, mCallback);
            Log.d(TAG, "Client = " + mClient);

            Token token = mExtTelephonyManager.queryNrConfig(mSlotId, mClient);
            Log.d(TAG, "queryNrConfig: " + token);
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "ExtTelephonyService disconnected...");
            if (mServiceConnected) {
                mServiceConnected = false;
                mClient = null;
            }
        }
    };

    public Enabled5GNRModePreferenceController(Context context, String key) {
        super(context, key);
        mContext = context;
        mPackageName = mContext.getPackageName();

        mSubscriptionManager = SubscriptionManager.from(context);
        mSharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(),
                mContext.MODE_PRIVATE);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mExtTelephonyManager = ExtTelephonyManager.getInstance(mContext.getApplicationContext());
        Log.d(TAG, "Connect to ExtTelephony bound service...");
        mExtTelephonyManager.connectService(mServiceCallback);
    }

    public void init(int subId) {
        mSlotId = mSubscriptionManager.getSlotIndex(subId);
        mSubId = subId;
    }

    private void update() {
        Log.d(TAG, "update.");
        updatePreference();
    }

    private void updatePreference() {
        if (mPreference != null) {
            updateState(mPreference);
        }
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        final boolean showPreferred5GNRMode = isCarrierConfigManagerKeyEnabled(
                CarrierConfigManager.KEY_SHOW_5GNR_MODE_OPTION_BOOL, subId, true);
        Log.i(TAG, "getAvailabilityStatus showPreferred5GNRMode " + showPreferred5GNRMode);
        return showPreferred5GNRMode ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    private void updateSharedPreference(int slotId, int nrConfig) {
        if (mSharedPreferences != null) {
            Log.i(TAG, "updateSharedPreference nrConfig " + nrConfig);
            mSharedPreferences.edit().putInt("nr_mode_" + slotId, nrConfig).apply();
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        mExtTelephonyManager.unRegisterCallback(mCallback);
        mExtTelephonyManager.disconnectService();
    }

    @Override
    public void updateState(Preference preference) {
        Log.d(TAG, "updateState.");
        if (mTelephonyManager == null) {
            return;
        }
        super.updateState(preference);
        final SwitchPreference switchPreference = (SwitchPreference) preference;
        switchPreference.setVisible(isAvailable());

        switchPreference.setChecked(isChecked());
        switchPreference.setEnabled(true);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        if (!SubscriptionManager.isValidSubscriptionId(mSubId)
                || (mTelephonyManager == null)) {
            return false;
        }

        Log.d(TAG, "setChecked: " + isChecked);
        int newNrMode;
        if (isChecked){
            newNrMode = NR_MODE_SA;
        } else {
            newNrMode = NR_MODE_NSA;
        }
        Log.i(TAG, "setChecked for slot: " + mSlotId + ", setNrConfig: " + newNrMode);
        userPrefNrConfig = newNrMode;
        if (mServiceConnected && mClient != null) {
            Token token = mExtTelephonyManager.setNrConfig(
                    mSlotId, new NrConfig(newNrMode), mClient);
            Log.d(TAG, "setNrConfig: " + token);
        }

        return true;
    }

    @Override
    public boolean isChecked(){
        int nrConfig = mSharedPreferences.getInt("nr_mode_" + mSlotId,
                NrConfig.NR_CONFIG_COMBINED_SA_NSA);
        if (nrConfig == NrConfig.NR_CONFIG_NSA) {
            return false;
        } else if (nrConfig == NrConfig.NR_CONFIG_SA) {
            return true;
        } else {
            return false;
        }
    }

    private int getSummaryResId(int nrMode) {
        if (nrMode == NrConfig.NR_CONFIG_COMBINED_SA_NSA) {
            return R.string.nr_nsa_sa;
        } else if (nrMode == NrConfig.NR_CONFIG_NSA) {
            return R.string.nr_nsa_only;
        } else if (nrMode == NrConfig.NR_CONFIG_SA) {
            return R.string.nr_sa_only;
        } else {
            return R.string.nr_nsa_sa;
        }
    }

    /**
     * Returns {@code true} when the key is enabled for the carrier, and {@code false} otherwise.
     */
    private boolean isCarrierConfigManagerKeyEnabled(String key, int subId, boolean defaultValue) {
        boolean result = defaultValue;
        final PersistableBundle carrierConfig = getCarrierConfig(subId);
        if (carrierConfig != null) {
            result = carrierConfig.getBoolean(key, defaultValue);
        }
        return result;
    }

    private PersistableBundle getCarrierConfig(int subId) {
        final CarrierConfigManager configManager = getCarrierConfigManager();
        PersistableBundle bundle = null;
        if (configManager != null) {
            bundle = configManager.getConfigForSubId(subId);
        }
        return bundle;
    }

    protected CarrierConfigManager getCarrierConfigManager() {
        return mContext.getSystemService(CarrierConfigManager.class);
    }
}
