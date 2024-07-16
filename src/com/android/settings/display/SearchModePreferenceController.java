package com.android.settings.display;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class SearchModePreferenceController extends BasePreferenceController {

    private static final String TAG = "SearchModePreferenceController";
    private static final String KEY_SEARCH_BAR = "qsb_search_bar";

    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "QsbPrefs";
    public static final int AVAILABLE = 1;

    private SwitchPreference switchPreference;
    private boolean previousState;

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
            switchPreference = screen.findPreference(KEY_SEARCH_BAR);
            if (switchPreference != null) {
                boolean isSearchModeEnabled = sharedPreferences.getBoolean(KEY_SEARCH_BAR, true);
                switchPreference.setChecked(isSearchModeEnabled);
                switchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean isEnabled = (Boolean) newValue;
                        previousState = switchPreference.isChecked(); // Save the previous state
                        showRebootDialog(isEnabled);
                        return false; // Do not change the state immediately
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying preference", e);
        }
    }

    private void showRebootDialog(boolean isEnabled) {
        new AlertDialog.Builder(mContext)
            .setTitle(mContext.getString(R.string.reboot_required_title))
            .setMessage(mContext.getString(R.string.reboot_required_message))
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Save the new value to SharedPreferences
                    sharedPreferences.edit().putBoolean(KEY_SEARCH_BAR, isEnabled).apply();
                    broadcastSwitchState(isEnabled);
                    rebootDevice();
                }
            })
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // User cancelled the dialog
                    dialog.dismiss();
                    // Reset the toggle to its previous state
                    switchPreference.setChecked(previousState);
                }
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
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

    private void rebootDevice() {
        try {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.reboot(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reboot the device", e);
            Toast.makeText(mContext, "Failed to reboot the device", Toast.LENGTH_SHORT).show();
        }
    }
}
