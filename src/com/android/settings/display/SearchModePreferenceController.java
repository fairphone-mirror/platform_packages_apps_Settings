package com.android.settings.display;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.core.BasePreferenceController;

public class SearchModePreferenceController extends BasePreferenceController {

    private static final String TAG = "SearchModePreferenceController";
    private static final String KEY_SEARCH_BAR = "qsb_search_bar";

    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "QsbPrefs";
    public static final int AVAILABLE = 1;

    public SearchModePreferenceController(Context context) {
        super(context, KEY_SEARCH_BAR);
        // Initialize SharedPreferences
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_SEARCH_BAR;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        try {
            SwitchPreference preference = screen.findPreference(KEY_SEARCH_BAR);
            if (preference != null) {
                boolean isSearchModeEnabled = sharedPreferences.getBoolean(KEY_SEARCH_BAR, true);
                preference.setChecked(isSearchModeEnabled);
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean isEnabled = (Boolean) newValue;
                        sharedPreferences.edit().putBoolean(KEY_SEARCH_BAR, isEnabled).apply(); // Save the new value to SharedPreferences
                        broadcastSwitchState(isEnabled);
                        return true;
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying preference", e);
        }
    }

    private void broadcastSwitchState(boolean isEnabled) {
        try {
            Intent intent = new Intent("com.android.display.ACTION_SWITCH_TOGGLED");
            intent.putExtra(KEY_SEARCH_BAR, isEnabled);
            int userId = ActivityManager.getCurrentUser();
            mContext.sendBroadcastAsUser(intent, new UserHandle(userId));
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast switch state", e);
        }
    }
}
