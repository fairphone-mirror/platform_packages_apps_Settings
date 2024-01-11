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
import android.os.UserHandle;
import android.provider.Settings.Global;

/**
 * add for FP5-2137 set battery protect by suntianhai 2023.07.13
 *
 * */

public class BatteryProtectPreferenceController extends BasePreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {
    private static final String BATTERY_PROTECT_ENABLE = "persist.sys.battery.protect.enable";

    private Preference mPreference;
    private static final String MFG_DATE_PROPERTY = "ro.vendor.tct.mfg.date";
    private static final int BLACKOUT_TIME = 20231225;

    public BatteryProtectPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    private int formateDateCode(byte[] raw) {
        String dayString = "**";
        String monthString = "**";
        String yearString = "****";
        int i;

        if (raw.length != 3) {
            return -1;
        }

        String dayRule = "123456789ABCDEFGHIJKLMNOPQRSTUV";
        String monthRule = "EFGHIJKLMNOP";
        String yearRule = "UVWXYZ6ABCDEFGHIJKLMNOPQ";// "KLMNOPQRSTUVWXYZ";
        // get day value
        i = dayRule.indexOf(raw[0]);
        if (i >= 0) {
            dayString = String.format("%02d", i + 1);
        }
        // get month value
        i = monthRule.indexOf(raw[1]);
        if (i >= 0) {
            monthString = String.format("%02d", i + 1);
        }
        // get year value
        i = yearRule.indexOf(raw[2]);
        if (i >= 0) {
            yearString = String.format("20%02d", i + 10);
        }

        return Integer.valueOf(yearString + monthString + dayString);
    }

    @Override
    public int getAvailabilityStatus() {
        String date = SystemProperties.get(MFG_DATE_PROPERTY,"");
        int dateTime = -1;
        if(date != null && !"".equals(date)){
            dateTime = formateDateCode(date.getBytes());
            if (dateTime > BLACKOUT_TIME) {
                return AVAILABLE;
            }
        }
        return CONDITIONALLY_UNAVAILABLE;
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
            writeBatEn("1");
        }
        SystemProperties.set(BATTERY_PROTECT_ENABLE,protectBattery? "1":"0");
        SystemProperties.set("persist.sys.battery.icon.enable",protectBattery? "1":"0");
        return true;
    }

    private void writeBatEn(String value) {
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            fw = new FileWriter("/sys/class/power_supply/battery/charging_enabled");
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
