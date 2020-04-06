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
import android.os.SystemProperties;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.slices.Sliceable;

public class ReceiverModulePreferenceController extends BasePreferenceController {

    private static final String TAG = "ReceiverModulePrefCtrl";

    private static final String KEY_RECEIVER_MODULE_INFO = "receiver_module_info";
    private static final String PROPERTY_FRONT_CAMERA_SENSOR = "fp2.cam.front.sensor";
    private static final String VALUE_FRONT_CAMERA_SENSOR_OV2685 = "ov2685";
    private static final String VALUE_FRONT_CAMERA_SENSOR_OV5670 = "ov5670";

    public ReceiverModulePreferenceController(Context context, String preferenceKey) {
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
                mContext.getText(R.string.receiver_module_info));
    }

    @Override
    public CharSequence getSummary() {
        return getReceiverModuleInfo();
    }

    private String getReceiverModuleInfo() {
        final String frontCameraSensor = SystemProperties.get(PROPERTY_FRONT_CAMERA_SENSOR);
        String info;
        if (VALUE_FRONT_CAMERA_SENSOR_OV2685.equals(frontCameraSensor)) {
            info = mContext.getResources().getString(R.string.receiver_module_ov2685);
        } else if (VALUE_FRONT_CAMERA_SENSOR_OV5670.equals(frontCameraSensor)) {
            info = mContext.getResources().getString(R.string.receiver_module_ov5670);
        } else {
            // Unexpected property value, or missing
            info = mContext.getResources().getString(R.string.device_info_default);
            Log.w(TAG, "Property " + PROPERTY_FRONT_CAMERA_SENSOR
                    + " has an unknown value of " + frontCameraSensor);
        }
        return info;
    }
}
