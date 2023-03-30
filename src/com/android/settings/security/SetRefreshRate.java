/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.security;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.util.Log;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import android.view.Display;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.os.SystemProperties;

public class SetRefreshRate extends InstrumentedDialogFragment implements OnClickListener {

    private static final String TAG = "setrefreshrate";
    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;

    private int mUserId;
    final String[] rate = {"30","60","90"};


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserId = UserHandle.myUserId();
        mDisplayManager = getActivity().getSystemService(DisplayManager.class);
        mDefaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle("Refresh Rate")
                .setSingleChoiceItems(rate, getRateWhich(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        float refresh_Rate = mDefaultDisplay.getRefreshRate();
                        Log.i("sth_","   which:" + which + "____refreshRate:" + refresh_Rate);
                        SystemProperties.set("persist.sys.current_rate",Float.toString(refresh_Rate));
                        if(which == 0){
//                            Settings.System.putFloatForUser(getActivity().getContentResolver(),
//                                    Settings.System.MIN_REFRESH_RATE, 30f,
//                                    mUserId);
                            SystemProperties.set("persist.sys.doze_rate","30");
                        }else if (which == 1){
//                            Settings.System.putFloatForUser(getActivity().getContentResolver(),
//                                    Settings.System.MIN_REFRESH_RATE, 60f,
//                                    mUserId);
                            SystemProperties.set("persist.sys.doze_rate","60");
                        }else if (which ==2){
//                            Settings.System.putFloatForUser(getActivity().getContentResolver(),
//                                    Settings.System.MIN_REFRESH_RATE, 90f,
//                                    mUserId);
                            SystemProperties.set("persist.sys.doze_rate","90");
                        }
                    }
                })
//                .setPositiveButton(R.string.save, this)
//                .setNegativeButton(R.string.cancel, this)
                .show();
    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
    }

    public static void show(Fragment parent) {
        if (!parent.isAdded()) return;

        final SetRefreshRate dialog = new SetRefreshRate();
        dialog.setTargetFragment(parent, 0);
        dialog.show(parent.getFragmentManager(), TAG);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_OWNER_INFO_SETTINGS;
    }
    
    private int getRateWhich(){
        int rate_n = 0;
        String doze_rate = SystemProperties.get("persist.sys.doze_rate","0");
        if ("30".equals(doze_rate)){
            rate_n = 0;
        }else if ("60".equals(doze_rate)){
            rate_n = 1;
        }else if ("90".equals(doze_rate)){
            rate_n = 2;
        }
        return rate_n;
    }
}
