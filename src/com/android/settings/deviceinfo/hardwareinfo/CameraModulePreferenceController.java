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

public class CameraModulePreferenceController extends BasePreferenceController {

    private static final String TAG = "CameraModulePrefCtrl";

    private static final String KEY_CAMERA_MODULE_INFO = "camera_module_info";
    private static final String PROPERTY_MAIN_CAMERA_SENSOR = "fp2.cam.main.sensor";
    private static final String VALUE_MAIN_CAMERA_SENSOR_OV8865 = "ov8865_q8v18a";
    private static final String VALUE_MAIN_CAMERA_SENSOR_OV12870 = "ov12870";

    public CameraModulePreferenceController(Context context, String preferenceKey) {
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
                mContext.getText(R.string.camera_module_info));
    }

    @Override
    public CharSequence getSummary() {
        return getCameraModuleInfo();
    }

    private String getCameraModuleInfo() {
        final String mainCameraSensor = SystemProperties.get(PROPERTY_MAIN_CAMERA_SENSOR);
        String info;
        if (VALUE_MAIN_CAMERA_SENSOR_OV8865.equals(mainCameraSensor)) {
            info = mContext.getResources().getString(R.string.camera_module_ov8865);
        } else if (VALUE_MAIN_CAMERA_SENSOR_OV12870.equals(mainCameraSensor)) {
            info = mContext.getResources().getString(R.string.camera_module_ov12870);
        } else {
            // Unexpected property value, or missing
            info = mContext.getResources().getString(R.string.device_info_default);
            Log.w(TAG, "Property " + PROPERTY_MAIN_CAMERA_SENSOR
                    + " has an unknown value of " + mainCameraSensor);
        }
        return info;
    }
}
