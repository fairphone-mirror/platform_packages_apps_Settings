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
package com.android.settings.security;

import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.security.SetRefreshRate;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.ObservablePreferenceFragment;
import com.android.settingslib.core.lifecycle.events.OnResume;
import android.util.Log;
import android.content.Context;
import android.provider.Settings;

public class SetRefreshRatePreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnResume {

    @VisibleForTesting
    static final String SET_REFRESH_RATE = "set_refresh_rate";
    private static final int MY_USER_ID = UserHandle.myUserId();

    private final ObservablePreferenceFragment mParent;
    private RestrictedPreference mSetRefreshRatePref;
    private final Context mContext;


    public SetRefreshRatePreferenceController(Context context, ObservablePreferenceFragment parent) {
        super(context);
        mContext = context;
        mParent = parent;
        if (parent != null) {
            parent.getSettingsLifecycle().addObserver(this);
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mSetRefreshRatePref  = screen.findPreference(SET_REFRESH_RATE);
    }

    @Override
    public void onResume() {
        updateEnableState();
        updateSummary();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return SET_REFRESH_RATE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        int doze_ = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.DOZE_ALWAYS_ON, -1);
        Log.i("sth_","         handlePreferenceTreeClick:" + doze_);
        mSetRefreshRatePref.setEnabled(doze_ == 1? true:false);
        Log.i("sth_","    handlePreferenceTreeClick" + preference.getKey());
        if (TextUtils.equals(getPreferenceKey(), preference.getKey())) {
            Log.i("sth_","   ___  ");
            SetRefreshRate.show(mParent);
            return true;
        }
        return false;
    }

    public void updateEnableState() {
        int doze_ = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.DOZE_ALWAYS_ON, -1);
        Log.i("sth_","    updateEnableState  doze_：" + doze_);
        if (mSetRefreshRatePref == null) {
            return;
        }
        mSetRefreshRatePref.setEnabled(doze_ == 1? true:false);
    }

    public void updateSummary() {
        Log.i("sth_","    updateSummary");
        if (mSetRefreshRatePref != null) {
//                mSetRefreshRatePref.setSummary("60Hz");
        }
    }

}
