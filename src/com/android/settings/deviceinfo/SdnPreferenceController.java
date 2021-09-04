/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.core.BasePreferenceController;

import java.util.List;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

public class SdnPreferenceController extends BasePreferenceController{
    private static final String TAG = "SdnPreferenceController";

    private static final int MSG_READ_SDN = 1;

    Context mContext;
    private Preference mPreference;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;

    private SdnHandler mHandler = new SdnHandler();



    public SdnPreferenceController(Context context, String key) {
        super(context, key);
        mContext = context;
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSubscriptionManager = (SubscriptionManager)mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> list = mSubscriptionManager.getActiveSubscriptionInfoList();
        Log.i(TAG, "getActiveSubscriptionInfoList: " + list.size());
        Message mMsg = mHandler.obtainMessage(MSG_READ_SDN);
        mMsg.obj = list;
        mHandler.sendMessage(mMsg);
    }

    @Override
    public CharSequence getSummary() {
        return " ";
    }

    @Override
    public int getAvailabilityStatus() {
        Log.i(TAG, "getSimState = " + mTelephonyManager.getSimState());
        return  mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }


    class SdnHandler extends Handler{

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_READ_SDN) {
                String summary = "";

                List<SubscriptionInfo> list =(List<SubscriptionInfo>) msg.obj;

                for (SubscriptionInfo info:list) {

                    Log.i(TAG, "getSubscriptionId = " + info.getSubscriptionId());
                    Uri mUri = Uri.parse("content://icc/sdn/subId/"+info.getSubscriptionId());
                    ContentResolver mContentResolver = mContext.getContentResolver();
                    Cursor mCursor = mContentResolver.query(mUri, null, null, null, null);
                    while (mCursor.moveToNext()) {
                        Log.i(TAG, mCursor.getString(0) + mCursor.getString(1));
                        if (mPreference != null && !"".equals(mCursor.getString(0)) && !"".equals(mCursor.getString(1)) ) {
                            if (!summary.equals("")){
                                summary = summary + "\n";
                            }
                            summary = summary + mCursor.getString(0) + " - " + mCursor.getString(1);
                        }
                    }
                    mCursor.close();
                }

                mPreference.setSummary(summary);
            }
        }

    }


}
