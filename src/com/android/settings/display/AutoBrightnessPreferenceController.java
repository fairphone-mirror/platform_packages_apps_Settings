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

import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.Message;
import android.text.TextUtils;

import androidx.preference.PreferenceScreen;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.annotation.NonNull;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.PrimarySwitchPreference;

public class AutoBrightnessPreferenceController extends TogglePreferenceController {

    private final String SYSTEM_KEY = SCREEN_BRIGHTNESS_MODE;
    private final int DEFAULT_VALUE = SCREEN_BRIGHTNESS_MODE_MANUAL;

    private static final String TAG = "AutoBrightnessPreferenceController.java";
    public Context mContext;
    private ContentObserver mSettingsContentObserver;
    private static final String DCDIMMING_ENABLED = "def_dcdimming_enabled";
    private static final String SCREEN_BRIGHTNESS = Settings.System.SCREEN_BRIGHTNESS;
    private static final String SCREEN_BRIGHTNESS_MODE = Settings.System.SCREEN_BRIGHTNESS_MODE;
    private PrimarySwitchPreference mPreference;
    private PreferenceScreen mScreen;
    private int mCurrentBrightnessMode;

    public AutoBrightnessPreferenceController(Context context, String key) {
        super(context, key);
        mContext = context;
        mSettingsContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())){
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                final String path = uri == null ? null : uri.getLastPathSegment();
                if (TextUtils.equals(path, DCDIMMING_ENABLED)) {
                        Message message = Message.obtain();
                        message.what = 1;
                        dcdimmingHandler.sendMessageDelayed(message,20);
                }
            }
        };
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DCDIMMING_ENABLED),false, mSettingsContentObserver, UserHandle.USER_CURRENT);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mScreen = screen;
    }

    private Handler dcdimmingHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            int what = msg.what;
            if (1 == what) {
                    mCurrentBrightnessMode = DCDimmingPreferenceController.CURRENT_BRIGHTNESS_MODE;
                    android.util.Log.d(TAG, "iris-AutoBrightnessPreferenceController:DCDIMMING_ENABLED-click DCDimming button,Adaptive brightness button unavailable-mCurrentBrightnessMode:"+mCurrentBrightnessMode);
                    if (mScreen != null && (mScreen.findPreference(getPreferenceKey())instanceof PrimarySwitchPreference) && mCurrentBrightnessMode != -1) {

                        mPreference = mScreen.findPreference(getPreferenceKey());
                        mPreference.setSwitchEnabled(false);
                        setChecked(false);
                        mPreference.setChecked(false);
                        mPreference.setEnabled(false);

                        Message message = Message.obtain();
                        message.what = 2;
                        //Delay for 7.2 seconds and wait for all brightness changes to end before clicking
                        dcdimmingHandler.sendMessageDelayed(message,7200);
                    }
            } else if (2 == what){
                android.util.Log.d(TAG, "iris-AutoBrightnessPreferenceController:Brightness change completed,Adaptive brightness button can be clicked again.currentBrightnessMode-"+mCurrentBrightnessMode);
                if (mCurrentBrightnessMode != -1) {
                    mPreference.setSwitchEnabled(true);
                    mPreference.setEnabled(true);
                    if (mCurrentBrightnessMode == 1) {
                        mPreference.setChecked(true);
                        setChecked(true);
                    } else {
                        mPreference.setChecked(false);
                        setChecked(false);
                    }
                }
            }
            return false;
        }
    });

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(),
                SYSTEM_KEY, DEFAULT_VALUE) != DEFAULT_VALUE;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        Settings.System.putInt(mContext.getContentResolver(), SYSTEM_KEY,
                isChecked ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : DEFAULT_VALUE);
        return true;
    }

    @Override
    @AvailabilityStatus
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available)
                ? AVAILABLE_UNSEARCHABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(isChecked()
                ? R.string.auto_brightness_summary_on
                : R.string.auto_brightness_summary_off);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }
}
