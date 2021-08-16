package com.android.settings.security;

import android.app.KeyguardManager;
import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;

import com.android.settings.core.BasePreferenceController;


/**
 * <pre>
 *     author : zhangtianwen
 *     e-mail : tianwen.zhang@t2mobile.com
 *     time   : 2021/08/16
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class UnlockFailedResetController extends BasePreferenceController implements Preference.OnPreferenceChangeListener{

    public static final String KEY_UNLOCK_FAILED_RESET = "unlock_failed_reset";

    public UnlockFailedResetController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    private boolean getGlobalState() {
        return Settings.Global.getInt(
                mContext.getContentResolver(),
                KEY_UNLOCK_FAILED_RESET, 1)
                == 1;
    }

    public boolean isChecked() {
        return getGlobalState();
    }

    public boolean setChecked(boolean isChecked) {
        Settings.Global.putInt(
                mContext.getContentResolver(), KEY_UNLOCK_FAILED_RESET, isChecked ? 1 : 0);
        return true;
    }

    // handle UI change
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!preference.getKey().equals(getPreferenceKey())) {
            return false;
        }
        setChecked(!isChecked());
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        ((TwoStatePreference) preference).setChecked(getGlobalState());
    }
}
