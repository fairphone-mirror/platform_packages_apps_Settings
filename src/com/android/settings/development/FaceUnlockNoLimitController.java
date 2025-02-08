package com.android.settings.development;

import android.app.Activity;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.RestrictedSwitchPreference;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;


import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

public class FaceUnlockNoLimitController extends DeveloperOptionsPreferenceController implements
        Preference.OnPreferenceChangeListener, PreferenceControllerMixin{

    private static final String PREFERENCE_KEY = "face_unlock_no_limit";
    private static final String TAG = "FaceUnlockNoLimitController";

    private PreferenceScreen mPreferenceScreen;


    private RestrictedSwitchPreference mPreference;

    public FaceUnlockNoLimitController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        return true;

    }

    @Override
    public String getPreferenceKey() {
        return PREFERENCE_KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceScreen = screen;

        mPreference = screen.findPreference(getPreferenceKey());

        boolean mState = Settings.Global.getInt(mContext.getContentResolver(),
                "face_unlock_no_limit", 1) == 1;
        mPreference.setChecked(mState);

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.Global.putInt(mContext.getContentResolver(), "face_unlock_no_limit",
                (boolean) newValue ? 1 : 0);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
    }

}
