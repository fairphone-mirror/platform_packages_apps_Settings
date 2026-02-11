package com.android.settings.display;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.PrimarySwitchPreference;
import android.provider.Settings;
public class FlipCoverPreferenceController extends TogglePreferenceController {
    private final String KEY_SLIP_COVER = Settings.System.ENABLE_SLIP_COVER;
    private final int SLIP_COVER_ENABLE_VALUE = Settings.System.SLIP_COVER_ENABLE; // 1
    private final int SLIP_COVER_DISABLE_VALUE = Settings.System.SLIP_COVER_DISABLE; // 0

    public FlipCoverPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }
    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
    }
    @Override
    public int getAvailabilityStatus() {
        return 1;
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(),
                KEY_SLIP_COVER, SLIP_COVER_ENABLE_VALUE) == SLIP_COVER_ENABLE_VALUE;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        Settings.System.putInt(mContext.getContentResolver(),KEY_SLIP_COVER,isChecked?SLIP_COVER_ENABLE_VALUE:SLIP_COVER_DISABLE_VALUE);
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return 0;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setEnabled(true);
    }
}
