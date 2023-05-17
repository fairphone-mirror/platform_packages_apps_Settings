/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.settings.display;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

import android.util.Log;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import android.os.SystemProperties;

public class TapToWakePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_TAP_TO_WAKE = "tap_to_wake";
    private static final String DOUBLE_TOP_EN = "/sys/devices/platform/goodix_ts.0/gesture/double_en";

    public TapToWakePreferenceController(Context context) {
        super(context);
    }

    @Override
    public String getPreferenceKey() {
        return KEY_TAP_TO_WAKE;
    }

    @Override
    public boolean isAvailable() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapWake);
    }

    @Override
    public void updateState(Preference preference) {
        int value = Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.DOUBLE_TAP_TO_WAKE, 0);
        String douTapEn = readDouEn();
        if ("disable".equals(douTapEn) && value == 1){
            Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.DOUBLE_TAP_TO_WAKE, 0);
            value = 0;
        }else if ("enable".equals(douTapEn) && value == 0){
            Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.DOUBLE_TAP_TO_WAKE, 1);
            value = 1;
        }
        ((SwitchPreference) preference).setChecked(value != 0);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        writeDouEn(value ? "1" : "0");
        SystemProperties.set("persist.sys.double_en",value ? "1" : "0");
        Settings.Secure.putInt(
                mContext.getContentResolver(), Settings.Secure.DOUBLE_TAP_TO_WAKE, value ? 1 : 0);
        return true;
    }

    private String readDouEn(){
        String value = "0";
        BufferedReader reader = null;
        FileReader fr = null;
        try {
            fr = new FileReader(DOUBLE_TOP_EN);
            reader = new BufferedReader(fr);
            value = reader.readLine();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                if (reader != null)
                    reader.close();
                if (fr != null)
                    fr.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return value;
    }

    private void writeDouEn(String value) {
        BufferedWriter writer = null;
        FileWriter fw = null;
        try {
            fw = new FileWriter(DOUBLE_TOP_EN);
            writer = new BufferedWriter(fw, 256);
            writer.write(value);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try{
                if (writer != null)
                    writer.close();
                if (fw != null)
                    fw.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
