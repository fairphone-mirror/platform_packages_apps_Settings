package com.android.settings.display;

import static android.provider.Settings.Secure.SUNLIGHT_MODE_ENABLED;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

/*
 * Add by t2m yingyubin for FP5-565 20230414
 */
public class SunlightModePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_SUNLIGHT_MODE = "sunlight_mode";

    public SunlightModePreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_SUNLIGHT_MODE;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        Settings.Secure.putInt(mContext.getContentResolver(), SUNLIGHT_MODE_ENABLED, value ? 1 : 0);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        int value = Settings.Secure.getInt(mContext.getContentResolver(), SUNLIGHT_MODE_ENABLED, 0);
        ((SwitchPreference) preference).setChecked(value != 0);
    }
}
