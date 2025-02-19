/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.DeviceInfoUtils;

import android.content.Intent;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import androidx.preference.Preference;

public class KernelVersionPreferenceController extends BasePreferenceController {

    private static final String TAG = "KernelPref";
    private static final String KEY_KERNEL_VERSION = "kernel_version";


    private long[] mHits = new long[5];

    public KernelVersionPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return DeviceInfoUtils.getFormattedKernelVersion(mContext);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), KEY_KERNEL_VERSION)) {
            return false;
        }
        System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
        mHits[mHits.length - 1] = SystemClock.uptimeMillis();
        if (mHits[0] >= (SystemClock.uptimeMillis() - 1000)) {

            for (int i = 0 ; i < mHits.length ;i++)
            {
                mHits[i] = 0;
            }

            final Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setClassName(
                            "com.android.mmi", "com.android.mmi.MMITest");
            try {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Unable to start activity " + intent.toString());
            }
        }
        return false;
    }
}
