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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.util.Log;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.security.OwnerInfoPreferenceController.OwnerInfoCallback;
import android.hardware.display.DisplayManagerGlobal;
import android.view.Display;

public class SetRefreshRate extends InstrumentedDialogFragment implements OnClickListener {

    private static final String TAG = "setrefreshrate";
    private Display display;

    private int mUserId;
    final String[] rate = {"30","60","120"};


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserId = UserHandle.myUserId();
        display = getActivity().getWindowManager().getDefaultDisplay();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle("Refresh Rate")
                .setSingleChoiceItems(rate, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        float refresh_Rate = display.getRefreshRate();
                        Log.i("sth_","         which:" + which + "refreshRate:" + refresh_Rate);
                        Display.Mode.Builder modeBuilder = new Display.Mode.Builder();
                        modeBuilder.setRefreshRate(30f);
                    }
                })
                .setPositiveButton(R.string.save, this)
                .setNegativeButton(R.string.cancel, this)
                .show();
    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            Log.i("sth_","  onClick       BUTTON_POSITIVE");
        }
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
}
