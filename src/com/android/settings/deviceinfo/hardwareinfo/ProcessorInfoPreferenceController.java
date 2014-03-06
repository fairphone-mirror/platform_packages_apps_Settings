/*
 * Copyright (C) 2021 Fairphone B.V.
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
import android.util.Log;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.slices.Sliceable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessorInfoPreferenceController extends BasePreferenceController {

    private static final String TAG = "ProcessorInfoPrefCtrl";

    private static final String FILENAME_PROC_CPUINFO = "/proc/cpuinfo";

    public ProcessorInfoPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public boolean isSliceable() {
        return true;
    }

    @Override
    public boolean isCopyableSlice() {
        return true;
    }

    @Override
    public void copy() {
        Sliceable.setCopyContent(mContext, getSummary(),
                mContext.getText(R.string.processor_info));
    }

    @Override
    public CharSequence getSummary() {
        return getProcessorInfo();
    }

    /**
     * Returns the Hardware value in /proc/cpuinfo, else returns "Unknown".
     * @return a string that describes the processor
     */
    private static String getProcessorInfo() {
        // Hardware : XYZ
        final String PROC_HARDWARE_REGEX = "Hardware\\s*:\\s*(.*)$"; /* hardware string */

        try {
            BufferedReader reader = new BufferedReader(new FileReader(FILENAME_PROC_CPUINFO));
            String cpuinfo;

            try {
                while (null != (cpuinfo = reader.readLine())) {
                    if (cpuinfo.startsWith("Hardware")) {
                        Matcher m = Pattern.compile(PROC_HARDWARE_REGEX).matcher(cpuinfo);
                        if (m.matches()) {
                            return m.group(1);
                        }
                    }
                }
                return "Unknown";
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            Log.e(TAG,
                "IO Exception when getting cpuinfo for Device Info screen",
                e);

            return "Unknown";
        }
    }
}
