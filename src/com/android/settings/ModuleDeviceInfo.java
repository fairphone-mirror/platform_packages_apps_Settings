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

import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * The "dialog" that shows from "Manual" in the Settings app.
 */
public class ModuleDeviceInfo extends Activity {

    private static final String TAG = "ModuleDeviceInfo";

    private TextView mTvCpu;
    private TextView mTvRam;
    private TextView mTvCamera;
    private TextView mTvBattery;
    private TextView mTvDisplay;
    private TextView mTvSmartPa;
    private TextView mTvFinger;
    private TextView mTvNfc;

    private static final String ERROR_READ_STATE = "can't read info";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.moduleinfo_activity);
        mTvCpu = findViewById(R.id.cpu);
        mTvRam = findViewById(R.id.ram);
        mTvCamera = findViewById(R.id.camera);
        mTvBattery = findViewById(R.id.battery);
        mTvDisplay = findViewById(R.id.display);
        mTvSmartPa = findViewById(R.id.smartPa);
        mTvFinger = findViewById(R.id.finger);
        mTvNfc = findViewById(R.id.nfc);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showAllDeviceInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void showAllDeviceInfo() {
        mTvCpu.setText("CPU : \n" + readCpuStage());
        mTvRam.setText("RAM : \n" + readRam());
        mTvCamera.setText("Camera sensor : \n" + readCamera());
        mTvBattery.setText("Battery module info : \n" + readBattaryInfo());
        mTvDisplay.setText("Display info : \n" + readDisplayInfo());
        mTvSmartPa.setText("SmartPa info : \n" + readSmartPaInfo());
        mTvFinger.setText("fingler print info : \n" + readFinglerInfo());
        mTvNfc.setText("NFC info = \n" + readNfc());
    }

    private String readCpuStage() {
        String socId = null;
        try {
            InputStream is = new FileInputStream("/sys/emkit/info/emkit_cpu");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            socId = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return socId;
    }


    private String buildCpuInfo(String socId) {

        if ("459".equals(socId)) {
            return "Snapdragon 750G";
        } else {
            return ERROR_READ_STATE;
        }
    }

    private String readNfc() {
        if (new File("/sys/bus/i2c/drivers/st21nfc").isDirectory()) {
            return "ST21NFCD";
        } else {
            return ERROR_READ_STATE;
        }
    }

    private String readFinglerInfo() {
        String chipId = SystemProperties.get("vendor.t2m.fingerprint.chipid", "7312");
        return chipId;
    }

    private String readSmartPaInfo() {
        if (new File("/sys/bus/i2c/drivers/aw882xx_smartpa").isDirectory()) {
            return "AW88261FCR";
        } else {
            return ERROR_READ_STATE;
        }
    }

    private String readRam() {
        String model = readHwInfo("/sys/devices/platform/soc/1d84000.ufshc/host0/target0:0:0/0:0:0:49476/model");
        String brand = readHwInfo("/sys/devices/platform/soc/1d84000.ufshc/host0/target0:0:0/0:0:0:49476/vendor");
        String ramInfo = "model : " + model + "\n" + "brand : " + brand;
        return ramInfo;
    }

    private String readCamera() {
        String frontInfo = "front camera info = " + readHwInfo("/sys/devices/virtual/deviceinfo/device_info/CamNameF");
        String auxInfo = "aux camera info = " + readHwInfo("/sys/devices/virtual/deviceinfo/device_info/CamNameB2");
        String mainInfo = "main camera info = " + readHwInfo("/sys/devices/virtual/deviceinfo/device_info/CamNameB");
        String cameraInfo = frontInfo + "\n" + auxInfo + "\n" + mainInfo;
        return cameraInfo;
    }

    private String readBattaryInfo() {
        return readHwInfo("/sys/emkit/info/battery_id");
    }

    private String readDisplayInfo() {
        StringBuffer info = new StringBuffer("");
        try {
            InputStream is = new FileInputStream("/sys/emkit/info/display");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = "";
            while ((line = reader.readLine()) != null) {
                if (line.contains("display_ic")) {
                    info = info.append(line);
                    info = info.append("\n");
                }
                if (line.contains("vendor")) {
                    info = info.append(line);
                }
            }
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return info.toString();
    }

    private String readHwInfo(String path) {
        String info = null;
        try {
            InputStream is = new FileInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            info = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return info;
    }
}
