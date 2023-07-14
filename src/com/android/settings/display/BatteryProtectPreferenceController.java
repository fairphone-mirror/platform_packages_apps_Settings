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

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import android.os.SystemProperties;
import android.util.Log;

/**
 * add for FP5-2137 set battery protect by suntianhai 2023.07.13
 *
 * */

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
        String ss = SystemProperties.get(BATTERY_PROTECT_ENABLE);
        ((SwitchPreference) preference).setChecked((ss != null && "1".equals(ss))? true : false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean protectBattery = (Boolean) newValue;
        if (!protectBattery) {
            //charge enable
            writeBatEn("6000000");
            SystemProperties.set("persist.sys.battery.icon.enable","0");
        }
        SystemProperties.set(BATTERY_PROTECT_ENABLE,protectBattery? "1":"0");
        return true;
    }

        private void writeBatEn(String value) {
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            fw = new FileWriter("/sys/class/power_supply/battery/user_fcc");
            bw = new BufferedWriter(fw, 256);
            bw.write(value);
            bw.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try{
                if (bw != null)
                    bw.close();
                if (fw != null)
                    fw.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}
