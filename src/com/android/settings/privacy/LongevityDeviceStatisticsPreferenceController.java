/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.settings.privacy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import android.text.TextUtils;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

/**
 * {@link PreferenceControllerMixin} that shows Dialog for wifi frequency band select.
 */
public class LongevityDeviceStatisticsPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String TAG = "LongevityDeviceStatisticsPreferenceController";

    private static final String KEY_LONGEVITY_DEVICE_STATISTICS = "longevity_device_statistics";

    public LongevityDeviceStatisticsPreferenceController(
            Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        // Always show preference.
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_LONGEVITY_DEVICE_STATISTICS;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference pref = screen.findPreference(KEY_LONGEVITY_DEVICE_STATISTICS);
        if (pref != null) {
            updateSummary(pref, "");
        }
    }

    private void updateSummary(Preference frequencyBandPref, String summary) {
        frequencyBandPref.setSummary(summary);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        updateSummary(preference, "");
        return true;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(getPreferenceKey(), preference.getKey())) {
            Intent activityIntent = new Intent();
            activityIntent.setComponent(new ComponentName("com.fairphone.activator","com.fairphone.activator.ui.ActivationSettingsActivity" ));
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (mContext != null) {
                mContext.getApplicationContext().startActivity(activityIntent);
            }
            return true;
        }
        return false;
    }
}
