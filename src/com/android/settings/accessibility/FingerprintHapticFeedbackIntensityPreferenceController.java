/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.os.VibrationAttributes;
import android.provider.Settings;

/** Preference controller for haptic feedback intensity */
public class FingerprintHapticFeedbackIntensityPreferenceController
        extends VibrationIntensityPreferenceController {

    /** General configuration for haptic feedback intensity settings. */
    public static final class FingerprintHapticFeedbackVibrationPreferenceConfig
            extends VibrationPreferenceConfig {

        public FingerprintHapticFeedbackVibrationPreferenceConfig(Context context) {
            super(context, Settings.System.FINGERPRINT_HAPTIC_FEEDBACK_INTENSITY,
                    VibrationAttributes.USAGE_FINGERPRINT_TOUCH);
        }

        @Override
        public int readIntensity() {
            return super.readIntensity();
        }

        @Override
        public boolean updateIntensity(int intensity) {
            return super.updateIntensity(intensity);
        }
    }

    public FingerprintHapticFeedbackIntensityPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey, new FingerprintHapticFeedbackVibrationPreferenceConfig(context));
    }

    protected FingerprintHapticFeedbackIntensityPreferenceController(Context context, String preferenceKey,
            int supportedIntensityLevels) {
        super(context, preferenceKey, new FingerprintHapticFeedbackVibrationPreferenceConfig(context),
                supportedIntensityLevels);
    }

    @Override
    public int getAvailabilityStatus() {
        return UNSUPPORTED_ON_DEVICE;
    }
}
