package com.android.settings.display;

import static android.provider.Settings.Secure.PICK_UP_GESTURE_ENABLED;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

/*
 * add for FP5-189 20230324
 */
public class PickUpToWakePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_PICK_UP_TO_WAKE = "pick_up_to_wake";

    public PickUpToWakePreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        SensorManager sensors = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE) != null;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_PICK_UP_TO_WAKE;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        Settings.Secure.putInt(mContext.getContentResolver(), PICK_UP_GESTURE_ENABLED, value ? 1 : 0);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        int value = Settings.Secure.getInt(mContext.getContentResolver(), PICK_UP_GESTURE_ENABLED, 0);
        ((SwitchPreference) preference).setChecked(value != 0);
    }
}
