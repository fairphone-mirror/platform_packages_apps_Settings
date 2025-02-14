/*
 * Copyright (C) 2025 Fairphone B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.fairphone.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class SwitchButtonSettingsPreferenceController extends BasePreferenceController {

    private static final String SWITCH_BUTTON_SETTINGS_PACKAGE_NAME =
            "com.fairphone.settings.switchbutton";
    private static final String SWITCH_BUTTON_SETTINGS_ACTIVITY =
            "com.fairphone.settings.switchbutton.SwitchButtonSettingsActivity";

    public SwitchButtonSettingsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(R.string.switch_button_setting_name);
    }

    @Override
    public int getAvailabilityStatus() {
        return SwitchButtonSettingsUtils.isSwitchButtonSettingsPackageAvailable(mContext)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (getPreferenceKey().equals(preference.getKey())) {
            ComponentName componentName = new ComponentName(
                    SWITCH_BUTTON_SETTINGS_PACKAGE_NAME,
                    SWITCH_BUTTON_SETTINGS_ACTIVITY
            );
            Intent intent = new Intent().setComponent(componentName);
            preference.getContext().startActivity(intent);
            return true;
        }
        return super.handlePreferenceTreeClick(preference);
    }
}