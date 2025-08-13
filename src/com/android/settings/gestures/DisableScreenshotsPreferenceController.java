package com.android.settings.gestures;


import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
/** The controller manages whether to take a screenshot of the key press. */
public class DisableScreenshotsPreferenceController extends TogglePreferenceController {
    private PrimarySwitchPreference mPreference;

    public DisableScreenshotsPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }


    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_sound;
    }
    @Override
    public boolean isChecked() {
        int defaultVal = 1;
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DISABLED_SCREENSHOT, defaultVal) != 0;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DISABLED_SCREENSHOT, isChecked
                        ? 1 : 0);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setEnabled(true);
        mPreference.setSwitchEnabled(true);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
