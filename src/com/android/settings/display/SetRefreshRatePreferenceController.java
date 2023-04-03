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
import androidx.preference.Preference;
import android.util.Log;
import android.view.Display;
import android.hardware.display.DisplayManager;
import androidx.appcompat.app.AlertDialog;
import android.provider.Settings;
import android.os.UserHandle;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class SetRefreshRatePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin {

    private static final String KEY_SCREEN_SAVER = "refresh_rate";
    private final Context mContext;
    private int mUserId;
    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;
    final String[] rate = {"60Hz","90Hz"};

    public SetRefreshRatePreferenceController(Context context) {
        super(context);
        mContext = context;
        mUserId = UserHandle.myUserId();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDefaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_SCREEN_SAVER;
    }

    @Override
    public void updateState(Preference preference) {
//        String refresh_Rate = Float.toString(mDefaultDisplay.getRefreshRate());
//        Log.i("sth_", "____refreshRate:" + refresh_Rate);
//        if (refresh_Rate.contains("60")){
//            preference.setSummary("60Hz");
//        }else if (refresh_Rate.contains("90")){
//            preference.setSummary("90Hz");
//        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if ("refresh_rate".equals(preference.getKey())){
            new AlertDialog.Builder(mContext)
                    .setTitle("Set Refresh Rate")
                    .setSingleChoiceItems(rate, getRateWhich(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(which == 0){
                                Settings.System.putFloatForUser(mContext.getContentResolver(),
                                        Settings.System.MIN_REFRESH_RATE, 60f,
                                        mUserId);
//                                updateState(preference);
                            }else if (which == 1){
                                Settings.System.putFloatForUser(mContext.getContentResolver(),
                                        Settings.System.MIN_REFRESH_RATE, 90f,
                                        mUserId);
//                                updateState(preference);
                            }
                        }
                    })
//                .setPositiveButton(R.string.save, this)
//                .setNegativeButton(R.string.cancel, this)
                    .show();
        }
        return true;
    }

    private int getRateWhich(){
        int rate_n = 0;
        String refresh_Rate = Float.toString(mDefaultDisplay.getRefreshRate());
        Log.i("sth_", "____refreshRate:" + refresh_Rate);
        if (refresh_Rate.contains("60")){
            rate_n = 0;
        }else if (refresh_Rate.contains("90")){
            rate_n = 1;
        }
        return rate_n;
    }

}
