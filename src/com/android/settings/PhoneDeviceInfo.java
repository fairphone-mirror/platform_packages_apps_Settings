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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.view.View;
import android.os.SystemProperties;
import android.os.Build;
import com.android.settingslib.DeviceInfoUtils;
import com.android.settings.Utils;
/**
 * The "dialog" that shows from "Manual" in the Settings app.
 */
public class PhoneDeviceInfo extends Activity {
    private static final String TAG = "PhoneDeviceInfo";
    private TextView mdeviceInfo;
    static final String BASEBAND_PROPERTY = "gsm.version.baseband";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.deviceinfo_activity, null);
        mdeviceInfo = (TextView)view.findViewById(R.id.deviceinfo);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showAllDeviceInfo();
    }

    private void showAllDeviceInfo(){
        String AllDeviceInfo = "";
        AllDeviceInfo += "HW Stage : " + readHWStage() + "\n\n";
        AllDeviceInfo += "EMCP : " + readEMCP() + "\n\n";
        AllDeviceInfo += "Build type : " + readBuildtype() + "\n\n";
        AllDeviceInfo += "Factory SN : " + readFactorySN() + "\n\n";
        AllDeviceInfo += "Kernel Version : \n" + readKernelVersion() + "\n\n";
        AllDeviceInfo += "Baseband version : \n" + readBasebandversion() + "\n\n";
        AllDeviceInfo += "APN table version : " + readAPNtableversion() + "\n\n";
        AllDeviceInfo += "Audio Version : " + readAudioVersion() + "\n\n";
        AllDeviceInfo += "Audio Smart PA version : " + readAudioSmartPAversion() + "\n\n";
        AllDeviceInfo += "TP/LCM Version : " + readTPLCMVersion() + "\n\n";
        AllDeviceInfo += "MFG date : " + readMFGdate() + "\n\n";
        AllDeviceInfo += "TFT : " + readTFT() + "\n\n";
        mdeviceInfo.setText(AllDeviceInfo);
    }

    private String readHWStage(){
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

    private String readEMCP(){
        String version = null;
        return version;
    }

    private String readBuildtype(){
        return Build.TYPE;
    }

    private String readFactorySN(){
        String version = null;
        return version;
    }

    private String readKernelVersion(){
        return DeviceInfoUtils.getFormattedKernelVersion(this);
    }

    private String readBasebandversion(){
        if (Utils.isSupportCTPA(getApplicationContext())) {
            String baseBands = SystemProperties.get(BASEBAND_PROPERTY,
                   getString(R.string.device_info_default));
             if (null != baseBands) {
                String[] baseBandArray = baseBands.split(",");
                if ((baseBandArray != null) && (baseBandArray.length > 0)) {
                   return baseBandArray[0];
                }
            }
        }
        return SystemProperties.get(BASEBAND_PROPERTY,
               getString(R.string.device_info_default));
    }

    private String readAPNtableversion(){
        String version = null;
        return version;
    }

    private String readAudioVersion(){
        String version = null;
        return version;
    }

    private String readAudioSmartPAversion(){
        String version = null;
        return version;
    }

    private String readTPLCMVersion(){
        String version = null;
        return version;
    }

    private String readMFGdate(){
        String version = null;
        return version;
    }

    private String readTFT(){
        String version = null;
        return version;
    }
}

