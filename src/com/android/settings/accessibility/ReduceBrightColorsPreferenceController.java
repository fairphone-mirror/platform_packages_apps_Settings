/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings.accessibility;

import static com.android.internal.accessibility.AccessibilityShortcutController.REDUCE_BRIGHT_COLORS_TILE_SERVICE_COMPONENT_NAME;

import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import android.provider.Settings.Secure;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.hardware.Sensor;

/** PreferenceController that shows the Reduce Bright Colors summary */
public class ReduceBrightColorsPreferenceController
        extends AccessibilityQuickSettingsPrimarySwitchPreferenceController
        implements LifecycleObserver, OnStart, OnStop, Preference.OnPreferenceClickListener{
    private ContentObserver mSettingsContentObserver;
    private PrimarySwitchPreference mPreference;
    private final Context mContext;
    private final ColorDisplayManager mColorDisplayManager;
    private final SensorManager mSensorManager;
    private final float CAN_ENTRY_EXTRA_DIM_VALUE = 80;
    private int mSmallLuxCounter = 0;
    private int mLageLuxCounter = 0 ;
    private final String ACCESSIBILITY_BUTTON_TARGETS_STRING = "com.android.server.accessibility/ReduceBrightColors";

    public ReduceBrightColorsPreferenceController(Context context,
            String preferenceKey) {
        super(context, preferenceKey);
        mContext = context;
        mSettingsContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())){
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                final String path = uri == null ? null : uri.getLastPathSegment();
                if (TextUtils.equals(path, Settings.Secure.REDUCE_BRIGHT_COLORS_ACTIVATED)) {
                    updateState(mPreference);
                }
                if (TextUtils.equals(path, Settings.Secure.ENABLE_REDUCE_BRIGHT_COLORS)) {
                    boolean isEnableExtraDim = Secure.getInt(mContext.getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,0) == 1;
                    if(isEnableExtraDim){
                        mPreference.setSwitchEnabled(true);
                    } else {
                        mPreference.setSwitchEnabled(false);
                    }
                }
            }
        };

        mColorDisplayManager = mContext.getSystemService(ColorDisplayManager.class);
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {

        boolean isEnableExtraDim = Secure.getInt(mContext.getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,0) == 1;
        if(!isEnableExtraDim){
            Toast.makeText(mContext,mContext.getText(R.string.toast_extra_dim_info), Toast.LENGTH_LONG).show();
        }

        return false;
    }

    @Override
    public boolean isChecked() {
        return mColorDisplayManager.isReduceBrightColorsActivated();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        super.setChecked(isChecked);
        return mColorDisplayManager.setReduceBrightColorsActivated(isChecked);
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(
                R.string.reduce_bright_colors_preference_summary);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        refreshSummary(preference);
    }

    @Override
    public int getAvailabilityStatus() {
        return ColorDisplayManager.isReduceBrightColorsAvailable(mContext) ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());

        if (mPreference != null) {

            boolean isEnableExtraDim = Secure.getInt(mContext.getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,0) == 1;
            if(isEnableExtraDim){
                mPreference.setSwitchEnabled(true);
            } else {
                mPreference.setSwitchEnabled(false);
            }
            mPreference.setOnPreferenceClickListener(this);
        }

    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_accessibility;
    }

    @Override
    public void onStart() {
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.REDUCE_BRIGHT_COLORS_ACTIVATED),
                false, mSettingsContentObserver, UserHandle.USER_CURRENT);
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.ENABLE_REDUCE_BRIGHT_COLORS),
                false, mSettingsContentObserver, UserHandle.USER_CURRENT);
        mSensorManager.registerListener(mLightSensorListener,mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT_BACK),
                  SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsContentObserver);
        mSensorManager.unregisterListener(mLightSensorListener);
    }

    private final SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final float lux = event.values[0];
            if(lux <= CAN_ENTRY_EXTRA_DIM_VALUE){
                mSmallLuxCounter++;
                mLageLuxCounter = 0;
            }else {
                mSmallLuxCounter = 0;
                mLageLuxCounter++;
            }
            if(mLageLuxCounter == 10){
                mColorDisplayManager.setReduceBrightColorsActivated(false);
                Secure.putInt(mContext.getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,0);
                Secure.putString(mContext.getContentResolver(),Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,"");
                Secure.putString(mContext.getContentResolver(),Secure.ACCESSIBILITY_BUTTON_TARGETS,getButtonTargetsString());
            }
            if(mSmallLuxCounter == 10){
                Secure.putInt(mContext.getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,1);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used.
        }
    };

    private String getButtonTargetsString() {
        String mCurrentAccessibilityButtonTargets = "";
        if (Settings.Secure.getString(mContext.getContentResolver(),Secure.ACCESSIBILITY_BUTTON_TARGETS) != null) {
            mCurrentAccessibilityButtonTargets = Settings.Secure.getString(mContext.getContentResolver(),Secure.ACCESSIBILITY_BUTTON_TARGETS);
        }
        String[] strings = mCurrentAccessibilityButtonTargets.split(":");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (!ACCESSIBILITY_BUTTON_TARGETS_STRING.equals(strings[i])){
                if (i == 0) {
                    result.append(strings[i]);
                } else {
                    result.append(":");
                    result.append(strings[i]);
                }
            }
        }
        return result.toString();
    }

    @Override
    protected ComponentName getTileComponentName() {
        return REDUCE_BRIGHT_COLORS_TILE_SERVICE_COMPONENT_NAME;
    }

    @Override
    CharSequence getTileTooltipContent() {
        return mContext.getText(
                R.string.accessibility_reduce_bright_colors_auto_added_qs_tooltip_content);
    }
}
