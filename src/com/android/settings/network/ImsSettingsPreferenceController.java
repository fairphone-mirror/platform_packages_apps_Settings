package com.android.settings.network;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import androidx.preference.PreferenceScreen;

public class ImsSettingsPreferenceController extends BasePreferenceController {

    public ImsSettingsPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
    }

    @Override
    public int getAvailabilityStatus() {

        boolean mState = (Settings.Global.getInt(mContext.getContentResolver(),
                "ims_enable_settings", 0) == 1) && (Settings.Global.getInt(mContext.getContentResolver(), "nv_ims_enable", 0) == 1);

        return mState ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                "ims_enable_settings", 0) == 1
                ? mContext.getString(R.string.ims_settings_summary)
                : "";
    }
}
