package com.android.settings.display;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

/*
 * ADD by T2M yingyubin for Desktop mode
 */
public class DesktopModePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String DESKTOP_MODE = "desktop_mode";

    public DesktopModePreferenceController(Context context) {
        super(context);
    }

    @Override
    public String getPreferenceKey() {
        return DESKTOP_MODE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        int value = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 0);
        int freeformWindows = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0);
        ((SwitchPreference) preference).setChecked(value != 0 && freeformWindows != 0);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, value ? 1 : 0);
                Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, value ? 1 : 0);
        return true;
    }
}

