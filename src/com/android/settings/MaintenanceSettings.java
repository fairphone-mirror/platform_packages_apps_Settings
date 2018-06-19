/*
 * Copyright (C) 2018 Fairphone B.V.
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

import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import com.android.internal.logging.MetricsProto.MetricsEvent;

/**
 * Activity with the maintenance settings.
 */
public class MaintenanceSettings extends SettingsPreferenceFragment {

    // Preference categories
    private static final String CHECKUP_PREFERENCE_SCREEN =
            "checkup_settings";

    private static final String PROXIMITY_SENSOR_PREFERENCE_SCREEN =
            "proximity_sensor_settings";

    private static final String HICCUP_PREFERENCE_SCREEN =
            "hiccup_settings";

    // Preference controls.
    private PreferenceScreen mCheckupPreferenceScreen;
    private PreferenceScreen mProximitySensorPreferenceScreen;
    private PreferenceScreen mHiccupPreferenceScreen;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.FP_MAINTENANCE;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.maintenance_prefs);
    }
}
