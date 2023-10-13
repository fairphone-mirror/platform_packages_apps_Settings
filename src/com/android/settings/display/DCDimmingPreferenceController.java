/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.provider.Settings;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.Message;
import android.text.TextUtils;
import androidx.annotation.NonNull;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

public class DCDimmingPreferenceController extends TogglePreferenceController
    implements LifecycleObserver, OnStart, OnStop {
    private static final String TAG = "DCDimmingPreferenceController";
    private static final String DCDIMMING_ENABLED = "def_dcdimming_enabled";
    private static final String SCREEN_BRIGHTNESS = Settings.System.SCREEN_BRIGHTNESS;
    private static final String SCREEN_BRIGHTNESS_MODE = Settings.System.SCREEN_BRIGHTNESS_MODE;
    private static final int TRANSITION_POINT = 1475;

    private Context mContext;
    private ContentObserver mSettingsContentObserver;
    private SwitchPreference mPreference;
    private int oldBrightness = -1;
    private int currentBrightness = 0;

    public DCDimmingPreferenceController(Context context, String key) {
        super(context, key);
        mContext = context;
        mSettingsContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())){
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                final String path = uri == null ? null : uri.getLastPathSegment();
                if (TextUtils.equals(path, DCDIMMING_ENABLED)) {
                    try {
                        int isEnableDCDimming = Settings.Secure.getInt(mContext.getContentResolver(), DCDIMMING_ENABLED,0);
                        currentBrightness = Settings.System.getInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS);
                        int currentBrightnessMode = Settings.System.getInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS_MODE);
                        android.util.Log.d(TAG, "iris :DCDIMMING_ENABLED-click DCDimming button-isEnableDCDimming:"+isEnableDCDimming+"    currentBrightness:"+currentBrightness+"    currentBrightnessMode:"+currentBrightnessMode);
                        mPreference.setEnabled(false);
                        Message message = Message.obtain();
                        //Delay for 7.2 seconds and wait for all brightness changes to end before clicking
                        dcdimmingHandler.sendMessageDelayed(message,7200);
                    } catch (Settings.SettingNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        int enable = Settings.Secure.getInt(mContext.getContentResolver(),DCDIMMING_ENABLED,0);
        if (1 == enable) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        if (isChecked) {
            Settings.Secure.putInt(mContext.getContentResolver(), DCDIMMING_ENABLED,1);
        } else {
            Settings.Secure.putInt(mContext.getContentResolver(), DCDIMMING_ENABLED,0);
        }
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }

    @Override
    public void onStart() {
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DCDIMMING_ENABLED),false, mSettingsContentObserver, UserHandle.USER_CURRENT);
        mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(SCREEN_BRIGHTNESS),false, mSettingsContentObserver, UserHandle.USER_CURRENT);

    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsContentObserver);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    private Handler dcdimmingHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            android.util.Log.d(TAG, "iris :DCDIMMING_ENABLED-click DCDimming button-Brightness change completed, button can be clicked again.");
            mPreference.setEnabled(true);
            return false;
        }
    });

}
