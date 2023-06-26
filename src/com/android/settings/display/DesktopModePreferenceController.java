package com.android.settings.display;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settings.development.RebootConfirmationDialogFragment;
import com.android.settings.development.RebootConfirmationDialogHost;

/*
 * ADD by T2M yingyubin for Desktop mode
 */
public class DesktopModePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener, RebootConfirmationDialogHost {

    private static final String DESKTOP_MODE = "desktop_mode";
    private Fragment mFragment;

    public DesktopModePreferenceController(Context context, Fragment fragment) {
        super(context);
        mFragment = fragment;
    }

    @Override
    public String getPreferenceKey() {
        return DESKTOP_MODE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        int value = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 0);
        int freeformWindows = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0);
        ((SwitchPreference) preference).setChecked(value != 0 && freeformWindows != 0);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, value ? 1 : 0);
                Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, value ? 1 : 0);
        if (value) {
            RebootConfirmationDialogFragment.show(
                    mFragment, R.string.reboot_dialog_force_desktop_mode, this);
        } else {
            Toast.makeText(mContext, R.string.desktop_mode_off_info, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    public void onRebootConfirmed() {
        final Intent intent = new Intent(Intent.ACTION_REBOOT);
        mContext.startActivity(intent);
    }
}

