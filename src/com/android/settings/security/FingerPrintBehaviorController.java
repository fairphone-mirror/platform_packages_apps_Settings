/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class FingerPrintBehaviorController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {
    public static final String KEY_UNLOCK_NEEDS_POWER_PRESS = "fingerprint_settings";
    private boolean mConfirmationDefaultOn;

    public FingerPrintBehaviorController(Context context, String key) {
        super(context, key);
        mConfirmationDefaultOn =
                context.getResources().getBoolean(R.bool.config_fingerprint_settings_behavior_on);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    private boolean getGlobalState() {
        return Settings.Global.getInt(
                        mContext.getContentResolver(),
                        KEY_UNLOCK_NEEDS_POWER_PRESS,
                        mConfirmationDefaultOn ? 1 : 0)
                == 1;
    }

    public boolean isChecked() {
        return getGlobalState();
    }

    public boolean setChecked(boolean isChecked) {
        Settings.Global.putInt(
                mContext.getContentResolver(), KEY_UNLOCK_NEEDS_POWER_PRESS, isChecked ? 1 : 0);
        return true;
    }

    // handle UI change
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (!preference.getKey().equals(getPreferenceKey())) {
            return false;
        }

        boolean nowState = (boolean) newValue;
        setChecked(nowState);

        return true;
    }

    @Override
    public void updateState(Preference preference) {
        if (preference instanceof TwoStatePreference) {
            ((TwoStatePreference) preference).setChecked(getGlobalState());
        }
    }
}
