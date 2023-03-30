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

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.content.res.Resources;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.widget.CandidateInfo;

import java.util.ArrayList;
import java.util.List;

public class OrientationTimingFragment extends RadioButtonPickerFragment {
    private static final String TAG = "OrientationTimingFragment";
    private String[] mInitialEntries;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.orientation_timing_settings;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.VR_DISPLAY_PREFERENCE;
    }

    @Override
    protected List<OrientationTimingCandidateInfo> getCandidates() {
        List<OrientationTimingCandidateInfo> candidates = new ArrayList<>();
        final Context context = getContext();
        candidates.add(new OrientationTimingCandidateInfo(context, 0,R.string.orientation_timing_spead_0_title));
        candidates.add(new OrientationTimingCandidateInfo(context, 1,R.string.orientation_timing_spead_1_title));
        candidates.add(new OrientationTimingCandidateInfo(context, 2,R.string.orientation_timing_spead_2_title));
        candidates.add(new OrientationTimingCandidateInfo(context, 3,R.string.orientation_timing_spead_3_title));
        candidates.add(new OrientationTimingCandidateInfo(context, 4,R.string.orientation_timing_spead_4_title));
        return candidates;
    }

    @Override
    protected String getDefaultKey() {
        int current = Settings.Secure.getInt(getContext().getContentResolver(),"def_orientation_timing",0);
        setDefaultKey(""+current);
        return ""+current;
    }

    @Override
    protected boolean setDefaultKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return false;
        }

        switch (key) {
            case "0":
                return Settings.Secure.putInt(getContext().getContentResolver(),"def_orientation_timing",0);
            case "1":
                return Settings.Secure.putInt(getContext().getContentResolver(),"def_orientation_timing",1);
            case "2":
                return Settings.Secure.putInt(getContext().getContentResolver(),"def_orientation_timing",2);
            case "3":
                return Settings.Secure.putInt(getContext().getContentResolver(),"def_orientation_timing",3);
            case "4":
                return Settings.Secure.putInt(getContext().getContentResolver(),"def_orientation_timing",4);
        }
        return false;
    }

    static class OrientationTimingCandidateInfo extends CandidateInfo {

        public final String label;
        public final int value;

        public OrientationTimingCandidateInfo(Context context, int value, int resId) {
            super(true);
            this.value = value;
            label = context.getString(resId);
        }

        @Override
        public CharSequence loadLabel() {
            return label;
        }

        @Override
        public Drawable loadIcon() {
            return null;
        }

        @Override
        public String getKey() {
            return ""+value;
        }
    }
}
