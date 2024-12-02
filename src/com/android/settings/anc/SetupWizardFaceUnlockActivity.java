package com.android.settings.anc;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.anc.util.SetupWizardUtils;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;

public class SetupWizardFaceUnlockActivity extends Activity {
    protected FooterBarMixin mFooterBarMixin;
    private static final int REQUEST_START_INTRO = 103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.setup_wizard_face_unlock);
        setHeaderText(R.string.faceunlock_setupwizard);
        mFooterBarMixin = (FooterBarMixin) getLayout().getMixin(FooterBarMixin.class);
        mFooterBarMixin.setSecondaryButton(new FooterButton.Builder(this).setText(R.string.security_settings_faceunlock_cancel_setup)
                .setListener(this.mCancelBtnOnClickListener).setButtonType(FooterButton.ButtonType.SKIP)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Tertiary).build());
        mFooterBarMixin.setPrimaryButton(new FooterButton.Builder(this).setText(R.string.security_settings_faceunlock_next)
                .setListener(this.mNextBtnOnClickListener).setButtonType(FooterButton.ButtonType.NEXT)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary).build());
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, R.style.GlifV4Theme_DayNight, first);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_START_INTRO && resultCode == RESULT_OK){
            SetupWizardUtils.returnToGoogleSetupWizard(this);
            finish();
        }
    }

    protected GlifLayout getLayout() {
        return (GlifLayout) findViewById(R.id.setup_wizard_layout);
    }


    private View.OnClickListener mCancelBtnOnClickListener = view -> onCancelButtonClick();


    private View.OnClickListener mNextBtnOnClickListener = view -> onNextButtonClick();

    protected void onCancelButtonClick() {
        SetupWizardUtils.returnToGoogleSetupWizard(this);
        finish();
    }

    protected void onNextButtonClick() {
        Intent intent = new Intent(this, SetupWizardFaceUnlockIntroActivity.class);
        SetupWizardUtils.copySetupExtras(getIntent(), intent);
        startActivityForResult(intent, REQUEST_START_INTRO);
    }

    protected void setHeaderText(int strId) {
        TextView headerTextView = getLayout().getHeaderTextView();
        CharSequence text = headerTextView.getText();
        CharSequence text2 = getText(strId);
        if (text != text2) {
            if (!TextUtils.isEmpty(text)) {
                headerTextView.setAccessibilityLiveRegion(1);
            }
            getLayout().setHeaderText(text2);
            setTitle(text2);
        }
    }
}