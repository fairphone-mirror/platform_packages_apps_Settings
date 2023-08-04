package com.android.settings.fuelgauge.batterysaver;

import com.android.settings.R;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.widget.CandidateInfo;
import android.graphics.drawable.Drawable;
import android.app.settings.SettingsEnums;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.os.UserHandle;
import android.provider.Settings;
import android.os.SystemProperties;


/**
 * author : suntianhai
 * e-mail : tianhai.sun@t2mobile.com
 * time   : 2023/08/1
 * desc   : add for FP5-2351 to set Battery Charging mode 
 * version: 1.0
 * 
 */
public class BatteryChargingState extends RadioButtonPickerFragment {
	private static final String TAG = "BatteryChargingState";


    // charging_slow  charging_normal
    private String[] mEntries;

    // 1  0
    private String[] mValues;

    public BatteryChargingState() { }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.BATTERY_CHARGING_STATE;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.battery_charging_state_settings;
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        final List<CandidateInfo> candidates = new ArrayList<>();

        if (mEntries == null || mValues == null) {
            return candidates;
        }

        for (int i = 0; i < mValues.length; i++) {
            candidates.add(new ChargingStateCandidateInfo(mEntries[i], mValues[i], true));
        }

        return candidates;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mEntries = getContext().getResources().getStringArray(
                R.array.charging_mode);
        mValues = getContext().getResources().getStringArray(
                R.array.charging_mode_value);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected String getDefaultKey() {
        String charge_mode = SystemProperties.get("persist.sys.charge_mode");
        if (charge_mode != null &&( "1".equals(charge_mode))){
            //slow mode
            return mValues[0];
        }else {
            return mValues[1];
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected boolean setDefaultKey(String key) {
        String charge_mode = SystemProperties.get("persist.sys.charge_mode");
        switch (key) {
            case "0"://slow
                Settings.Global.putStringForUser(getContext().getContentResolver(),
                    Settings.Global.SET_BATTERY_CHARGING_MODE, "0",
                    UserHandle.myUserId());
                break;
            case "1"://normal
                Settings.Global.putStringForUser(getContext().getContentResolver(),
                    Settings.Global.SET_BATTERY_CHARGING_MODE, "1",
                    UserHandle.myUserId());
                break;
        }
        return true;
    }

    private static class ChargingStateCandidateInfo extends CandidateInfo {
        private final CharSequence mLabel;
        private final String mKey;

        ChargingStateCandidateInfo(CharSequence label, String key, boolean enabled) {
            super(enabled);
            mLabel = label;
            mKey = key;
        }

        @Override
        public CharSequence loadLabel() {
            return mLabel;
        }

        @Override
        public Drawable loadIcon() {
            return null;
        }

        @Override
        public String getKey() {
            return mKey;
        }
    }

}