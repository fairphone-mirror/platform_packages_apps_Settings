package com.android.settings.display;

import static android.provider.Settings.System.SHOW_BATTERY_PERCENT;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.overlay.FeatureFactory;

import android.os.SystemProperties;
import android.util.Log;
import android.os.UserHandle;
import android.provider.Settings.Global;


public class BatteryProtectPreferenceController extends BasePreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {
    private static final String BATTERY_PROTECT_ENABLE = "persist.sys.battery.protect.enable";

    private Preference mPreference;

    public BatteryProtectPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public int getAvailabilityStatus() {
        return 0;
    }

    @Override
    public void updateState(Preference preference) {
        boolean isProtected = false;
        String bat_pro_en = SystemProperties.get(BATTERY_PROTECT_ENABLE);
        ((SwitchPreference) preference).setChecked((bat_pro_en != null && "1".equals(bat_pro_en))? true : false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean protectBattery = (Boolean) newValue;
        SystemProperties.set(BATTERY_PROTECT_ENABLE,protectBattery? "1":"0");
        updateBattery();
        return true;
    }

    private void updateBattery(){
        Settings.Global.putStringForUser(mContext.getContentResolver(),
                Settings.Global.UPDATE_BATTERY_CHARGING_MODE, System.currentTimeMillis() + "",
                UserHandle.myUserId());
    }

}
