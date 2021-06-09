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
        mTvCpu.setText("CPU : \n" + buildCpuInfo(readCpuStage()));
        mTvRam.setText("RAM : \n" + readRam());
        mTvCamera.setText("Camera sensor : \n" + readCamera());
        mTvBattery.setText("Battary module info : \n" + readBattaryInfo());
        mTvDisplay.setText("Display info : \n" + readDisplayInfo());
        mTvSmartPa.setText("SmartPa info : \n" + readSmartPaInfo());
        mTvFinger.setText("fingler print info : \n" + readFinglerInfo());
        mTvNfc.setText("NFC info = \n" + readNfc());
    }

    private String readCpuStage() {
        String socId = null;
        try {
            InputStream is = new FileInputStream("/sys/devices/soc0/soc_id");
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
        return null;
    }

    private String readSmartPaInfo() {
        if (new File("/sys/bus/i2c/drivers/aw882xx_smartpa").isDirectory()) {
            return "AW88264A";
        } else {
            return ERROR_READ_STATE;
        }
    }

    private String readRam() {
        String version = null;
        return version;
    }

    private String readCamera() {
        return null;
    }

    private String readBattaryInfo() {
        String battaryInfo = null;
        try {
            InputStream is = new FileInputStream("/sys/class/power_supply/bms/resistance_id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            battaryInfo = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return battaryInfo;
    }

    private String readDisplayInfo() {
        return null;
    }


}

