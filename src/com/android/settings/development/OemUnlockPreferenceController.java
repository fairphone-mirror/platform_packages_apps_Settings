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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.oemlock.OemLockManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settingslib.RestrictedSwitchPreference;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

import com.fairphone.settings.development.OemUnlockHelper;
import com.fairphone.settings.development.OemUnlockRequestResultListener;
import com.fairphone.settings.development.OemUnlockResult;
import com.fairphone.settings.development.OemUnlockUtils;
import com.fairphone.settings.development.UnlockRequestErrorDialog;
import com.fairphone.settings.development.UnlockRequestLoadingDialog;

public class OemUnlockPreferenceController extends DeveloperOptionsPreferenceController implements
        Preference.OnPreferenceChangeListener, PreferenceControllerMixin, OnActivityResultListener,
        OemUnlockRequestResultListener, FragmentResultListener {

    private static final String PREFERENCE_KEY = "oem_unlock_enable";
    private static final String TAG = "OemUnlockPreferenceController";
    private static final String OEM_UNLOCK_SUPPORTED_KEY = "ro.oem_unlock_supported";
    private static final String UNSUPPORTED = "-9999";
    private static final String SUPPORTED = "1";

    private final OemLockManager mOemLockManager;
    private final UserManager mUserManager;
    private final TelephonyManager mTelephonyManager;
    private final Activity mActivity;
    private final DevelopmentSettingsDashboardFragment mFragment;
    private RestrictedSwitchPreference mPreference;
    private final OemUnlockHelper mOemUnlockHelper;

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
        mActivity = activity;
        mFragment = fragment;
        mOemUnlockHelper = new OemUnlockHelper(this);
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

    /**
     * Handles the result from a fragment.
     *
     * This method is called when a fragment that was started for a result
     * (e.g., a dialog) returns a result. It checks if the request key
     * matches {@link UnlockRequestErrorDialog#REQUEST_KEY}. If it does,
     * it then checks the result bundle for the
     * {@link UnlockRequestErrorDialog#PARAM_RETRY} boolean. If this
     * parameter is true, it calls {@link #sendUnlockRequestToFactory()}
     * to retry the unlock request.
     *
     * @param requestKey The unique key identifying the request.
     * @param result A Bundle containing the result data.
     */
    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (requestKey.equals(UnlockRequestErrorDialog.REQUEST_KEY)) {
            if (result.getBoolean(UnlockRequestErrorDialog.PARAM_RETRY)) {
                sendUnlockRequestToFactory();
            }
        }
    }

    @Override
    protected void onDeveloperOptionsSwitchEnabled() {
        handleDeveloperOptionsToggled();
    }

    public void onOemUnlockConfirmed() {
        if (OemUnlockUtils.INSTANCE.isDebugOsBuild()) {
            mOemLockManager.setOemUnlockAllowedByUser(true);
            updatePreferenceState();
        } else if (OemUnlockUtils.INSTANCE.isNetworkAvailable(mActivity)) {
            sendUnlockRequestToFactory();
        } else {
            onNetworkNotAvailableError();
        }
    }

    public void onOemUnlockDismissed() {
        updatePreferenceState();
    }

    private void updatePreferenceState() {
        if (mPreference == null) {
            return;
        }
        updateState(mPreference);
    }

    /**
     * Sends a request to the OEM's factory service to determine if the device
     * is eligible for OEM unlocking. This is typically an asynchronous operation.
     * After sending the request, a dialog is shown to the user indicating that
     * the system is waiting for a response from the server.
     */
    private void sendUnlockRequestToFactory() {
        mOemUnlockHelper.sendUnlockRequest(mContext);
        showWaitingServerResponseDialog();
    }

    /**
     * Callback method invoked when the result of an OEM unlock request is received
     * from the factory service.
     * <p>
     * This method dismisses any "waiting for server response" dialog. It then processes
     * the {@code result} to determine if the request was successful, resulted in a
     * connection error, or a generic error, and calls the appropriate handler method.
     *
     * UPDATE: Since we will not be releasing any more SW updates on FP3, from now on, we will
     * always allow OEM unlocking, aside from Forbidden cases.
     *
     * @param result The {@link OemUnlockResult} object containing the outcome of the
     *               OEM unlock request. Cannot be null.
     */
    @Override
    public void onOemRequestResult(@NonNull OemUnlockResult result) {
        dismissWaitingServerResponseDialog();

        if (result instanceof OemUnlockResult.Allowed || result instanceof OemUnlockResult.Error) {
            onOemUnlockRequestSuccess();
        } else {
            onOemUnlockRequestError();
        }
    }

    /**
     * Handles the successful outcome of an OEM unlock request.
     * <p>
     * This method updates the system state to allow OEM unlocking by the user.
     * The {@code MissingPermission} lint suppression is present because setting
     * OEM unlock allowance might require specific system permissions that are assumed
     * to be held by the Settings app.
     */
    @SuppressLint("MissingPermission")
    private void onOemUnlockRequestSuccess() {
        mOemLockManager.setOemUnlockAllowedByUser(true);
        updatePreferenceState();

        new AlertDialog.Builder(mFragment.getContext())
                .setTitle(R.string.unlock_request_success_title)
                .setMessage(R.string.unlock_request_success_message)
                .setPositiveButton(
                        R.string.reboot_dialog_reboot_now,
                        (dialog, which) -> rebootDevice(mActivity)
                )
                .setNegativeButton(R.string.reboot_dialog_reboot_later, null)
                .show();
    }

    private void rebootDevice(Context context) {
        final Intent intent = new Intent(Intent.ACTION_REBOOT).setPackage("android");
        context.startActivity(intent);
    }

    private void onNetworkNotAvailableError() {
        if (mFragment == null) return;

        String message = mFragment
                .getResources()
                .getString(R.string.oem_unlock_error_no_internet_connection);

        UnlockRequestErrorDialog.Companion
                .show(mFragment, message, (requestKey, result) -> {
                    if (requestKey.equals(UnlockRequestErrorDialog.REQUEST_KEY)) {
                        if (result.getBoolean(UnlockRequestErrorDialog.PARAM_RETRY)) {
                            confirmEnableOemUnlock();
                        } else {
                            onOemUnlockDismissed();
                        }
                    }
                });
    }

    /**
     * Handles errors encountered during the OEM unlock request process.
     * <p>
     * This method displays an error dialog to the user with the provided {@code message}.
     * The dialog offers options to retry the request or dismiss the process.
     * The outcome of the user's interaction with the dialog (retry or dismiss)
     * is handled by the provided lambda callback.
     */
    private void onOemUnlockRequestError() {
        if (mFragment == null) return;

        String message = mFragment.getResources().getString(R.string.oem_unlock_error_forbidden);

        UnlockRequestErrorDialog.Companion
                .show(mFragment, message, (requestKey, result) -> {
                    if (requestKey.equals(UnlockRequestErrorDialog.REQUEST_KEY)) {
                        dismissWaitingServerResponseDialog();

                        if (result.getBoolean(UnlockRequestErrorDialog.PARAM_RETRY)) {
                            confirmEnableOemUnlock();
                        } else {
                            onOemUnlockDismissed();
                        }
                    }
                });
    }

    private void showWaitingServerResponseDialog() {
        if (mContext == null) return;

        UnlockRequestLoadingDialog.Companion.show(mFragment, () -> {
            onOemUnlockDismissed();
            return null;
        });
    }

    private void dismissWaitingServerResponseDialog() {
        if (mFragment != null) {
            final FragmentManager manager = mFragment.getChildFragmentManager();
            UnlockRequestLoadingDialog dialog = (UnlockRequestLoadingDialog) manager
                    .findFragmentByTag(UnlockRequestLoadingDialog.TAG);
            if (dialog != null) {
                dialog.dismissAllowingStateLoss();
            }
        }
    }

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
        final ChooseLockSettingsHelper.Builder builder =
                new ChooseLockSettingsHelper.Builder(mActivity, mFragment);
        return builder.setRequestCode(requestCode)
                .setTitle(resources.getString(R.string.oem_unlock_enable))
                .show();
    }

    @VisibleForTesting
    void confirmEnableOemUnlock() {
        EnableOemUnlockSettingWarningDialog.show(mFragment);
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
