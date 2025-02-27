
package com.android.settings.display;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemProperties;
import android.os.Handler;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.accounts.AccountTypePreference;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Executor;

public class PeakRefreshRateListPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener, LifecycleObserver, OnStart, OnStop {

    @VisibleForTesting static float DEFAULT_REFRESH_RATE = 60f;

    private static final String KEY_PEAKREFRESH_LIST = "peak_refresh_rate_list";

    @VisibleForTesting float mPeakRefreshRate;
    private ArrayList<Float> mPeakRefreshRates = new ArrayList<>();
    private ArrayList<String> mPeakRefreshRateEntries = new ArrayList<>();
    private ArrayList<String> mPeakRefreshRateValues = new ArrayList<>();

    private static final String TAG = "RefreshRateListPrefCtr";
    private static final float INVALIDATE_REFRESH_RATE = -1f;

    private final Handler mHandler;
    private final IDeviceConfigChange mOnDeviceConfigChange;
    private final DeviceConfigDisplaySettings mDeviceConfigDisplaySettings;
    private Preference mPreference;

    private static final float REFRESHRATE_60 = 60f;
    private static final float REFRESHRATE_120 = 120f;

    //private static final String SETTINGS_KEY_DYNAMIC_REFRESH_RATE_ENABLE = "dynamic_refresh_rate_enable";

    private interface IDeviceConfigChange {
        void onDefaultRefreshRateChanged();
    }
    public PeakRefreshRateListPreferenceController(Context context) {
        super(context);
        mHandler = new Handler(context.getMainLooper());
        mDeviceConfigDisplaySettings = new DeviceConfigDisplaySettings();
        mOnDeviceConfigChange =
                new IDeviceConfigChange() {
                    public void onDefaultRefreshRateChanged() {
                        updateState(mPreference);
                    }
                };

        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);

        if (display == null) {
            Log.w(TAG, "No valid default display device");
            mPeakRefreshRate = DEFAULT_REFRESH_RATE;
            mPeakRefreshRates.add(DEFAULT_REFRESH_RATE);
            mPeakRefreshRateEntries.add("" + DEFAULT_REFRESH_RATE + " HZ");
            mPeakRefreshRateValues.add(String.valueOf(DEFAULT_REFRESH_RATE));
        } else {
            mPeakRefreshRate = findPeakRefreshRate(display.getSupportedModes());
            findPeakRefreshRateList(display.getSupportedModes());
        }

        Log.d(
                TAG,
                "DEFAULT_REFRESH_RATE : "
                        + DEFAULT_REFRESH_RATE
                        + " mPeakRefreshRate : "
                        + mPeakRefreshRate);
    }

    @Override
    public String getPreferenceKey() {
        return KEY_PEAKREFRESH_LIST;
    }

    @Override
    public boolean isAvailable() {
        return mPeakRefreshRates.size() > 1;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        updateState(screen.findPreference(getPreferenceKey()));
    }

    @Override
    public void updateState(Preference preference) {
        ListPreference pref = (ListPreference) preference;
        pref.setEntries(mPeakRefreshRateEntries.toArray(new String[mPeakRefreshRateEntries.size()]));
        pref.setEntryValues(mPeakRefreshRateValues.toArray(new String[mPeakRefreshRateValues.size()]));
        String newValue = String.valueOf((int)getCurrentRefreshRate());
        Log.d(TAG, "update old=" + pref.getValue() + ",new="+ newValue);
        // pref.setValue(String.valueOf(getCurrentRefreshRate()));
        pref.setValue(newValue);
        // pref.setOnPreferenceChangeListener(this);

        refreshSummary(pref);
    }

    float findPeakRefreshRate(Display.Mode[] modes) {
        float peakRefreshRate = DEFAULT_REFRESH_RATE;
        for (Display.Mode mode : modes) {
            if (Math.round(mode.getRefreshRate()) > peakRefreshRate) {
                peakRefreshRate = mode.getRefreshRate();
            }
        }
        return peakRefreshRate;
    }


    void findPeakRefreshRateList(Display.Mode[] modes) {
        float peakRefreshRate = DEFAULT_REFRESH_RATE;
        ArrayList<Display.Mode> modeList = new ArrayList<>(Arrays.asList(modes));
        Collections.sort(modeList, new Comparator<Display.Mode>() {
            @Override
            public int compare(Display.Mode m1, Display.Mode m2) {
                return (int) m1.getRefreshRate() - (int) m2.getRefreshRate();
            }
        });
        for (Display.Mode mode : modeList) {
            mPeakRefreshRates.add(Float.valueOf(Math.round(mode.getRefreshRate())));
            mPeakRefreshRateEntries.add("" + ((int)Math.round(mode.getRefreshRate())) + " HZ");
            mPeakRefreshRateValues.add(String.valueOf((int)mode.getRefreshRate()));
        }
        // mPeakRefreshRateEntries.add(mContext.getResources().getString(R.string.dynamic_framerate_settings));
        // mPeakRefreshRateValues.add("0");
    }

    @Override
    public CharSequence getSummary() {
        // if (isDynamicRefreshRateEnabled()) {
        //     return mContext.getResources().getString(R.string.dynamic_framerate_settings);
        // }
        int refreshRate = (int) getCurrentRefreshRate();
        return "" + refreshRate + " HZ";
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "changed newValue=" + newValue);
        // if ("0".equals((String)newValue)) {
        //     Settings.System.putInt(mContext.getContentResolver(), SETTINGS_KEY_DYNAMIC_REFRESH_RATE_ENABLE, 1);
        //     Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, REFRESHRATE_60);
        //     Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, REFRESHRATE_120);
        //     refreshSummary(preference);
        //     return true;
        // }

        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.MATCH_CONTENT_FRAME_RATE, Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER);
        //Settings.System.putInt(mContext.getContentResolver(), SETTINGS_KEY_DYNAMIC_REFRESH_RATE_ENABLE, 0);

        float refreshRate = (float) Math.round(Float.valueOf((String)newValue));
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, refreshRate);
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, refreshRate);
        refreshSummary(preference);
        return true;
    }

    @Override
    public void onStart() {
        mDeviceConfigDisplaySettings.startListening();
    }

    @Override
    public void onStop() {
        mDeviceConfigDisplaySettings.stopListening();
    }

    private float getCurrentRefreshRate() {
        // if (isDynamicRefreshRateEnabled()) {
        //     return 0;
        // }
        return Settings.System.getFloat(
                        mContext.getContentResolver(),
                        Settings.System.PEAK_REFRESH_RATE,
                        getDefaultPeakRefreshRate());
    }

    // private boolean isDynamicRefreshRateEnabled() {
    //     return Settings.System.getInt(
    //                     mContext.getContentResolver(),
    //                     SETTINGS_KEY_DYNAMIC_REFRESH_RATE_ENABLE,
    //                     0) == 1;
    // }

    private class DeviceConfigDisplaySettings
            implements DeviceConfig.OnPropertiesChangedListener, Executor {
        public void startListening() {
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                    this /* Executor */,
                    this /* Listener */);
        }

        public void stopListening() {
            DeviceConfig.removeOnPropertiesChangedListener(this);
        }

        public float getDefaultPeakRefreshRate() {
            float defaultPeakRefreshRate =
                    DeviceConfig.getFloat(
                            DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                            DisplayManager.DeviceConfig.KEY_PEAK_REFRESH_RATE_DEFAULT,
                            INVALIDATE_REFRESH_RATE);
            Log.d(TAG, "DeviceConfigDisplaySettings getDefaultPeakRefreshRate : " + defaultPeakRefreshRate);

            return defaultPeakRefreshRate;
        }

        @Override
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
            // Got notified if any property has been changed in NAMESPACE_DISPLAY_MANAGER. The
            // KEY_PEAK_REFRESH_RATE_DEFAULT value could be added, changed, removed or unchanged.
            // Just force a UI update for any case.
            if (mOnDeviceConfigChange != null) {
                mOnDeviceConfigChange.onDefaultRefreshRateChanged();
                updateState(mPreference);
            }
        }

        @Override
        public void execute(Runnable runnable) {
            if (mHandler != null) {
                mHandler.post(runnable);
            }
        }
    }

    private float getDefaultPeakRefreshRate() {
        float defaultPeakRefreshRate = mDeviceConfigDisplaySettings.getDefaultPeakRefreshRate();
        if (defaultPeakRefreshRate == INVALIDATE_REFRESH_RATE) {
            defaultPeakRefreshRate = (float) mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_defaultPeakRefreshRate);
        }

        if (Math.round(defaultPeakRefreshRate) > Math.round(mPeakRefreshRate)) {
            defaultPeakRefreshRate = Math.round(mPeakRefreshRate);
        }
        Log.d(TAG, "DeviceConfig getDefaultPeakRefreshRate : " + defaultPeakRefreshRate);
        return defaultPeakRefreshRate;
    }
}
