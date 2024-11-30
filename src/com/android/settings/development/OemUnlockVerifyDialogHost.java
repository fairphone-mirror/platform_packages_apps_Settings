/**
 * Copyright (C) 2019 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
//<2019/08/06-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Add oem_lock password protection feature.
package com.android.settings.development;

/**
 * Interface for OemUnlockDialogFragment callbacks.
 */
public interface OemUnlockVerifyDialogHost {

    /**
     * Called when the user presses enable on the warning dialog.
     */
    void onOemUnlockVerifyDialogConfirmed(String password);

    /**
     * Called when the user dismisses or cancels the warning dialog.
     */
    void onOemUnlockVerifyDialogDismissed();
}
//>2019/08/06-kanewang