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

package com.android.settings.deviceinfo.hardwareinfo;

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HardwareRevisionPreferenceController extends BasePreferenceController {

    private static final String TAG = "HwRevisionPref";

    public HardwareRevisionPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_device_model) &&
                !TextUtils.isEmpty(getSummary()) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public CharSequence getSummary() {
        String version = null;
        try {
            InputStream is = new FileInputStream("/sys/class/board_id/version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            version = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return version;
    }
}
