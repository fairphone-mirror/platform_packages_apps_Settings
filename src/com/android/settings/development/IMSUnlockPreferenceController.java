/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.development;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.RestrictedSwitchPreference;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

import com.qualcomm.qcrilhook.QcRilHookCallback;
import com.qualcomm.sysrilcmd.ISysRilCmd;
import com.qualcomm.sysrilcmd.SysRilCmd;

public class IMSUnlockPreferenceController extends DeveloperOptionsPreferenceController implements
        Preference.OnPreferenceChangeListener, PreferenceControllerMixin, LifecycleObserver,
        OnStop {

    private static final String PREFERENCE_KEY = "ims_unlock_enable";
    private static final String TAG = "IMSUnlockPreferenceController";

    private boolean mRilHookReady;
    private SysRilCmd mSysRil;
    private QcRilHookCallback mQcrilHookCb;
    private boolean mEnabled;
    private PreferenceScreen mPreferenceScreen;

    private static final int MESSAGE_IMS_SWITCH = 1000;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_IMS_SWITCH:
                    Log.d(TAG, " receive MESSAGE_IMS_SWITCH,mEnabled = " + mEnabled);
                    setVisible(mPreferenceScreen, getPreferenceKey(), mEnabled);
                    break;
            }
        }
    };

    private RestrictedSwitchPreference mPreference;

    public IMSUnlockPreferenceController(Context context, Activity activity,
                                         DevelopmentSettingsDashboardFragment fragment,Lifecycle lifecycle) {
        super(context);
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }

        mQcrilHookCb = new QcRilHookCallback() {
            public void onQcRilHookReady() {
                Log.d(TAG, " onQcRilHookReady");
                mRilHookReady = true;
                try {
                    mEnabled = (mSysRil.getInt8Val(ISysRilCmd.RIL_SUB_CMD_INT8_IMS_ENABLE) == 1);
                    Message msg = mHandler.obtainMessage(MESSAGE_IMS_SWITCH);
                    mHandler.sendMessageDelayed(msg, 1000);
                } catch (Exception e) {
                    mEnabled = false;
                    Log.e(TAG, "SysRilCmd IOException" + e.getMessage());
                }
            }

            @Override
            public void onQcRilHookDisconnected() {
                Log.d(TAG, " onQcRilHookDisconnected");
            }
        };
        mSysRil = new SysRilCmd(context, mQcrilHookCb);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return PREFERENCE_KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceScreen = screen;

        mPreference = screen.findPreference(getPreferenceKey());

        boolean mState = Settings.Global.getInt(mContext.getContentResolver(),
                "ims_enable_settings", 0) == 1;
        mPreference.setChecked(mState);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.Global.putInt(mContext.getContentResolver(), "ims_enable_settings",
                (boolean) newValue ? 1 : 0);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
    }


    @Override
    public void onStop() {
        Log.i(TAG, ">onStop" );
        mSysRil.SysRilDispose();
    }
}
