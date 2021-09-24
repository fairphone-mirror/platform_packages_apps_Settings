/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.development;

import static com.android.settings.development.DevelopmentOptionsActivityRequestCodes.REQUEST_CODE_ENABLE_OEM_UNLOCK;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.oemlock.OemLockManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
//<2019/08/06-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Add oem_lock password protection feature.
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import android.widget.Toast;
import android.util.Log;
//>2019/08/06-kanewang

//<2019/10/31-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Change oem_lock password protection algorithm with IMEI+SN.
import android.os.Build;
//>2019/10/31-kanewang
//<2020/09/23,lucygao,OEM unlock flow change.
import com.arima.settings.OemLockVerifier;
import java.util.Timer;
import java.util.TimerTask;
//>2020/09/23,lucygao.

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settingslib.RestrictedSwitchPreference;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

public class OemUnlockPreferenceController extends DeveloperOptionsPreferenceController implements
        Preference.OnPreferenceChangeListener, PreferenceControllerMixin, OnActivityResultListener {

    private static final String PREFERENCE_KEY = "oem_unlock_enable";
    private static final String TAG = "OemUnlockPreferenceController";
    private static final String OEM_UNLOCK_SUPPORTED_KEY = "ro.oem_unlock_supported";
    private static final String UNSUPPORTED = "-9999";
    private static final String SUPPORTED = "1";
    private static final int HTTP_OK_RESULT = 0x01;
    private static final int HTTP_CREATED_RESULT = 0x02;
    private static final int HTTP_FAIL_RESULT = 0x03;
    private static final boolean DEBUG = true;
    private OemLockVerifier mVerifier = null;
    private AlertDialog mWaitingDlg = null;
    private final OemLockManager mOemLockManager;
    private final UserManager mUserManager;
    private final TelephonyManager mTelephonyManager;
    private final DevelopmentSettingsDashboardFragment mFragment;
    private final ChooseLockSettingsHelper mChooseLockSettingsHelper;
    private RestrictedSwitchPreference mPreference;

    //<2019/08/06-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Add oem_lock password protection feature.
    private fp_password password_ckecker = new fp_password();
    private Toast toast_msg = null;
    //>2019/08/06-kanewang

    public OemUnlockPreferenceController(Context context, Activity activity,
            DevelopmentSettingsDashboardFragment fragment) {
        super(context);

        if (!TextUtils.equals(SystemProperties.get(OEM_UNLOCK_SUPPORTED_KEY, UNSUPPORTED),
                SUPPORTED)) {
            mOemLockManager = null;
            Log.w(TAG, "oem_unlock not supported.");
        } else {
            mOemLockManager = (OemLockManager) context.getSystemService(Context.OEM_LOCK_SERVICE);
        }
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mFragment = fragment;
        if (activity != null || mFragment != null) {
            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(activity, mFragment);
        } else {
            mChooseLockSettingsHelper = null;
        }
    }

    @Override
    public boolean isAvailable() {
        return mOemLockManager != null;
    }

    @Override
    public String getPreferenceKey() {
        return PREFERENCE_KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean isUnlocked = (Boolean) newValue;
        if (isUnlocked) {
            if (!showKeyguardConfirmation(mContext.getResources(),
                    REQUEST_CODE_ENABLE_OEM_UNLOCK)) {
                confirmEnableOemUnlock();
            }
        } else {
            mOemLockManager.setOemUnlockAllowedByUser(false);
            OemLockInfoDialog.show(mFragment);
        }
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        mPreference.setChecked(isOemUnlockedAllowed());
        updateOemUnlockSettingDescription();
        // Showing mEnableOemUnlock preference as device has persistent data block.
        mPreference.setDisabledByAdmin(null);
        mPreference.setEnabled(enableOemUnlockPreference());
        if (mPreference.isEnabled()) {
            // Check restriction, disable mEnableOemUnlock and apply policy transparency.
            mPreference.checkRestrictionAndSetDisabled(UserManager.DISALLOW_FACTORY_RESET);
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ENABLE_OEM_UNLOCK) {
            if (resultCode == Activity.RESULT_OK) {
                if (mPreference.isChecked()) {
                    confirmEnableOemUnlock();
                } else {
                    mOemLockManager.setOemUnlockAllowedByUser(false);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onDeveloperOptionsSwitchEnabled() {
        handleDeveloperOptionsToggled();
    }

    public void onOemUnlockConfirmed() {
        mOemLockManager.setOemUnlockAllowedByUser(true);
    }

    public void onOemUnlockDismissed() {
        if (mPreference == null) {
            return;
        }
        updateState(mPreference);
    }

    //<2019/10/31-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Change oem_lock password protection algorithm with IMEI+SN.
    private String getKey() {
        String imei = getIMEI();
        String sn = Build.getSerial();

        //Log.e(TAG, "getKey input: imei=" + imei + ",sn=" + sn);

        return imei + sn;
    }
    //>2019/10/31-kanewang

    //<2019/08/06-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Add oem_lock password protection feature.
    private String getIMEI() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getImei(PhoneConstants.SIM_ID_1);
    }

    //<2020/09/23,lucygao,OEM unlock flow change.
    Handler uiUpdater = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what)
            {
                case HTTP_OK_RESULT:
                    if (mWaitingDlg.isShowing()) mWaitingDlg.dismiss();
                    EnableOemUnlockSettingWarningDialog.show(mFragment);
                    break;
                case HTTP_CREATED_RESULT:
                    if (mWaitingDlg.isShowing()) mWaitingDlg.dismiss();
                    OemLockVerifyDialog.show(mFragment);
                    break;
                case HTTP_FAIL_RESULT:
                    if (mWaitingDlg.isShowing()) mWaitingDlg.dismiss();
                    break;
            }
        }
    };
    public void onOemUnlockVerifyDialogConfirmed(String password) {
        //verify entered password with IMEI>MD5
        boolean verified = true;
        String md5_out = "";

        //<2019/10/31-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Change oem_lock password protection algorithm with IMEI+SN.
        int checksum = 0;
        int checksum_from_user = 0;
        String s_checksum_from_user = "";
        String key = getKey();

        if (("".equals(key)) || ("".equals(password))) {
            verified = false;
        }

       // checksum_from_user = Integer.parseUnsignedInt(password, 16);
        //s_checksum_from_user = Integer.toHexString(checksum_from_user);

        if (verified) {
            Log.i(TAG, "mVerifier == null ? " + (mVerifier == null));
            if (mVerifier == null) {
                mVerifier = new OemLockVerifier(mContext, new OemLockVerifier.onResponseListener() {
                    @Override
                    public void onFinish(final int check_code, final String msg) {
                        Log.i(TAG, "Verify oem lock Result : " + check_code + ",msg: " + msg);
                        String message = msg;

                        //process result
                        //200 for pass, 400 for code incorrect, 404 for no such phone
                        switch (check_code) {
                            case OemLockVerifier.HTTP_OK:
                                uiUpdater.obtainMessage(HTTP_OK_RESULT).sendToTarget();
                                message = "Correct code";
                                break;
                            case OemLockVerifier.HTTP_VERIFY_FAIL_WRONG_CODE:
                                uiUpdater.obtainMessage(HTTP_FAIL_RESULT).sendToTarget();
                                message = "Incorrect code";
                                break;
                            case OemLockVerifier.HTTP_VERIFY_FAIL_NO_SUCH_PHONE:
                                uiUpdater.obtainMessage(HTTP_FAIL_RESULT).sendToTarget();
                                message = "No such phone";
                                break;
                            default:
                                //prmpt fail message
                                break;
                        }
                        if (DEBUG) {
                            Looper.prepare();
                            Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
                            Looper.loop();
                        }
                    }
                });
            }
            Log.i(TAG, "invoke mVerifier.queryVerifyResult()----password="+password);
            mVerifier.queryVerifyResultGet(password, getIMEI(), Build.getSerial());
            //mVerifier.queryVerifyResultGet(password, "357811090354522", "A209FNJY0201");
            //Show waiting dialog            
            showWaitingLockQueryDialog();

        } else {
            Toast.makeText(mContext, "Password verification failed.", Toast.LENGTH_LONG).show();
        }

        /*
        if (verified) {
            EnableOemUnlockSettingWarningDialog.show(mFragment);
        } else {
            Toast.makeText(mContext, "Password verification failed.", Toast.LENGTH_LONG).show();
        }*/
        //>2019/10/31-kanewang
    }

    private void showWaitingLockQueryDialog() {
        if (mContext == null) return;

        mWaitingDlg = new AlertDialog.Builder(mContext)
                //.setTitle("Waiting")
                .setMessage("Processing...")
                .create();
        mWaitingDlg.show();
    }
    //>2020/09/23,lucygao.
    public void onOemUnlockVerifyDialogDismissed() {
        if (mPreference == null) {
            return;
        }
        updateState(mPreference);
    }
    //>2019/08/06-kanewang

    private void handleDeveloperOptionsToggled() {
        mPreference.setEnabled(enableOemUnlockPreference());
        if (mPreference.isEnabled()) {
            // Check restriction, disable mEnableOemUnlock and apply policy transparency.
            mPreference.checkRestrictionAndSetDisabled(UserManager.DISALLOW_FACTORY_RESET);
        }
    }

    private void updateOemUnlockSettingDescription() {
        int oemUnlockSummary = R.string.oem_unlock_enable_summary;
        if (isBootloaderUnlocked()) {
            oemUnlockSummary = R.string.oem_unlock_enable_disabled_summary_bootloader_unlocked;
        } else if (isSimLockedDevice()) {
            oemUnlockSummary = R.string.oem_unlock_enable_disabled_summary_sim_locked_device;
        } else if (!isOemUnlockAllowedByUserAndCarrier()) {
            // If the device isn't SIM-locked but OEM unlock is disallowed by some party, this
            // means either some other carrier restriction is in place or the device hasn't been
            // able to confirm which restrictions (SIM-lock or otherwise) apply.
            oemUnlockSummary =
                    R.string.oem_unlock_enable_disabled_summary_connectivity_or_locked;
        }
        mPreference.setSummary(mContext.getResources().getString(oemUnlockSummary));
    }

    /** Returns {@code true} if the device is SIM-locked. Otherwise, returns {@code false}. */
    private boolean isSimLockedDevice() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            if (mTelephonyManager.getAllowedCarriers(i).size() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the bootloader has been unlocked. Otherwise, returns {code false}.
     */
    @VisibleForTesting
    boolean isBootloaderUnlocked() {
        return mOemLockManager.isDeviceOemUnlocked();
    }

    private boolean enableOemUnlockPreference() {
        return !isBootloaderUnlocked() && isOemUnlockAllowedByUserAndCarrier();
    }


    @VisibleForTesting
    boolean showKeyguardConfirmation(Resources resources, int requestCode) {
        return mChooseLockSettingsHelper.launchConfirmationActivity(
                requestCode, resources.getString(R.string.oem_unlock_enable));
    }

    @VisibleForTesting
    void confirmEnableOemUnlock() {
        if (isDebugOsBuild()) {
            EnableOemUnlockSettingWarningDialog.show(mFragment);
        }else {
            OemLockVerifyDialog.show(mFragment);
        }
    }

    private boolean isDebugOsBuild() {
        return "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE);
    }

    /**
     * Returns whether OEM unlock is allowed by the user and carrier.
     *
     * This does not take into account any restrictions imposed by the device policy.
     */
    @VisibleForTesting
    boolean isOemUnlockAllowedByUserAndCarrier() {
        final UserHandle userHandle = UserHandle.of(UserHandle.myUserId());
        return mOemLockManager.isOemUnlockAllowedByCarrier()
                && !mUserManager.hasBaseUserRestriction(UserManager.DISALLOW_FACTORY_RESET,
                userHandle);
    }

    @VisibleForTesting
    boolean isOemUnlockedAllowed() {
        return mOemLockManager.isOemUnlockAllowed();
    }

}
