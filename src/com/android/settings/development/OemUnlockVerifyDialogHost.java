/**
 * Copyright (C) 2019 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
package com.android.settings.development;

/** Interface for OemUnlockDialogFragment callbacks. */
public interface OemUnlockVerifyDialogHost {

    /** Called when the user presses enable on the warning dialog. */
    void onOemUnlockVerifyDialogConfirmed(String password);

    /** Called when the user dismisses or cancels the warning dialog. */
    void onOemUnlockVerifyDialogDismissed();
}
