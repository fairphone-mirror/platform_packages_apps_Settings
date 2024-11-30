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
import android.os.AsyncTask;

import com.android.settings.core.BasePreferenceController;

import java.util.List;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

public class SdnPreferenceController extends BasePreferenceController {
    private static final String TAG = "SdnPreferenceController";
    Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;

    private final String mKey;

    private PreferenceScreen mPreferenceScreen;

    private boolean processed = false;

    public SdnPreferenceController(Context context, String key) {
        super(context, key);
        mKey = key;
        mContext = context;
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSubscriptionManager =
                (SubscriptionManager) mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

    }

    @Override
    public CharSequence getSummary() {

        if (!processed) {
            new QueryTask().execute();
            processed = true;
        }

        return "";
    }

    @Override
    public int getAvailabilityStatus() {
        Log.i(TAG, "getSimState = " + mTelephonyManager.getSimState());
        return mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY ? AVAILABLE :
                UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mPreferenceScreen = screen;
        super.displayPreference(screen);
    }

    public void setSummary(String summary) {
        Preference preference = mPreferenceScreen.findPreference(mKey);
        if (preference != null) {
            Log.i(TAG, "setSummary: " + summary);

            preference.setSummary(summary);
        }
    }


    private class QueryTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            List<SubscriptionInfo> list = mSubscriptionManager.getActiveSubscriptionInfoList();
            String summary = "  ";

            for (SubscriptionInfo info : list) {

                Log.i(TAG, "getSubscriptionId = " + info.getSubscriptionId());
                Uri mUri = Uri.parse("content://icc/sdn/subId/" + info.getSubscriptionId());
                ContentResolver mContentResolver = mContext.getContentResolver();
                Cursor mCursor = mContentResolver.query(mUri, null, null, null, null);
                while (mCursor.moveToNext()) {
                    Log.i(TAG, mCursor.getString(0) + mCursor.getString(1));
                    if (!"".equals(mCursor.getString(0)) && !"".equals(mCursor.getString(1))) {
                        if (!summary.equals("")) {
                            summary = summary + "\n";
                        }
                        summary = summary + mCursor.getString(0) + " - " + mCursor.getString(1);
                    }
                }
                mCursor.close();
            }
            Log.i(TAG, "summary = " + summary);
            return summary;
        }

        @Override
        protected void onPostExecute(String summary) {
            setSummary(summary);
        }
    }

}
