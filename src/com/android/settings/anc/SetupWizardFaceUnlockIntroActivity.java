package com.android.settings.anc;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.anc.enroll.EnrollActivity;
import com.android.settings.anc.intro.DisclaimerActivity;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;

public class SetupWizardFaceUnlockIntroActivity extends Activity {

    protected FooterBarMixin mFooterBarMixin;
    Button mShowDisclaimerBtn;
    private final int REQUEST_START_ENROLL = 104;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_wizard_face_unlock_intro);
        setHeaderText(R.string.face_add_title);
        mShowDisclaimerBtn = findViewById(R.id.show_disclaimer);
        mShowDisclaimerBtn.setOnClickListener(view ->
                startActivity(new Intent(SetupWizardFaceUnlockIntroActivity.this, DisclaimerActivity.class)));
        mFooterBarMixin = (FooterBarMixin) getLayout().getMixin(FooterBarMixin.class);
        mFooterBarMixin.setPrimaryButton(new FooterButton.Builder(this)
                .setText(R.string.security_settings_faceunlock_next)
                .setListener(mContinueBtnOnClickListener)
                .setButtonType(FooterButton.ButtonType.NEXT)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary).build());
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, R.style.SudThemeGlifV3_DayNight, first);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_ENROLL && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            finish();
        }
    }


    protected GlifLayout getLayout() {
        return (GlifLayout) findViewById(R.id.setup_wizard_layout);
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

    private View.OnClickListener mContinueBtnOnClickListener = view -> {
        startActivityForResult(new Intent(getBaseContext(), EnrollActivity.class), REQUEST_START_ENROLL);
    };
}