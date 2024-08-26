/**
 * Copyright (C) 2019 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
package com.android.settings.development;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

//ref:EnableOemUnlockSettingWarningDialog
public class OemLockVerifyDialog extends InstrumentedDialogFragment implements
       TextWatcher, DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    private static final String TAG = "OemLockVerifyDialog";

    private AlertDialog mAlertDialog;
    private Button mOkButton;

    private TextView mPasswordView;

    // This flag is set when the name is updated by code, to distinguish from user changes
    private boolean mPasswordUpdated;

    // This flag is set when the user edits the name (preserved on rotation)
    private boolean mPasswordEdited;

    // Key to save the edited name and edit status for restoring after rotation
    private static final String KEY_PASSWORD = "verify_password";
    private static final String KEY_PASSWORD_EDITED = "verify_password_edited";

    public static void show(Fragment host) {
        final FragmentManager manager = host.getActivity().getSupportFragmentManager();
        if (manager.findFragmentByTag(TAG) == null) {
            final OemLockVerifyDialog dialog =
                    new OemLockVerifyDialog();
            dialog.setTargetFragment(host, 0 /* requestCode */);
            dialog.show(manager, TAG);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.DIALOG_ENABLE_OEM_UNLOCKING;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String password = "";

        if (savedInstanceState != null) {
            password = savedInstanceState.getString(KEY_PASSWORD, password);
            mPasswordEdited = savedInstanceState.getBoolean(KEY_PASSWORD_EDITED, false);
        }

        mAlertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.verification_confirm_your_password_header)
                .setMessage(com.android.settingslib.R.string.confirm_enable_oem_unlock_text)
                .setView(createDialogView())
                .setPositiveButton(R.string.enable_text, this /* onClickListener */)
                .setNegativeButton(android.R.string.cancel, this /* onClickListener */)
                .create();

        mAlertDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        return mAlertDialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_PASSWORD, mPasswordView.getText().toString());
        outState.putBoolean(KEY_PASSWORD_EDITED, mPasswordEdited);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAlertDialog = null;
        mPasswordView = null;
        mOkButton = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOkButton == null) {
            mOkButton = mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            mOkButton.setEnabled(mPasswordEdited);    // Ok button enabled after user edits
        }
    }

    public void afterTextChanged(Editable s) {
        if (mPasswordUpdated) {
            // Device name changed by code; disable Ok button until edited by user
            mPasswordUpdated = false;
            mOkButton.setEnabled(false);
        } else {
            mPasswordEdited = true;
            if (mOkButton != null) {
                mOkButton.setEnabled(s.toString().trim().length() != 0);
            }
        }
    }

    public void onConfigurationChanged(Configuration newConfig, CharSequence s) {
        super.onConfigurationChanged(newConfig);

        if (mOkButton != null) {
            mOkButton.setEnabled(s.length() != 0 && !(s.toString().trim().isEmpty()));
        }
    }

    /* Not used */
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    /* Not used */
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        final OemUnlockVerifyDialogHost host = (OemUnlockVerifyDialogHost) getTargetFragment();
        if (host == null) {
            return;
        }

        String mPassword = mPasswordView.getText().toString();

        if ("".equals(mPassword)) {
            return;
        }

        if (which == DialogInterface.BUTTON_POSITIVE) {
            host.onOemUnlockVerifyDialogConfirmed(mPassword);
        } else {
            host.onOemUnlockVerifyDialogDismissed();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final OemUnlockVerifyDialogHost host = (OemUnlockVerifyDialogHost) getTargetFragment();
        if (host == null) {
            return;
        }
        host.onOemUnlockVerifyDialogDismissed();
    }

    private View createDialogView() {

        final LayoutInflater layoutInflater = (LayoutInflater)getActivity()
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = layoutInflater.inflate(R.layout.dialog_confirmpassword, null);
        mPasswordView = (TextView) view.findViewById(R.id.edittext);
        mPasswordView.addTextChangedListener(this);

        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mAlertDialog.dismiss();
                    return true;    // action handled
                } else {
                    return false;   // not handled
                }
            }
        });

        return view;
    }
}
