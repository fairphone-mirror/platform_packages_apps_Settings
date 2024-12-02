package com.android.settings.anc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.settings.anc.util.SetupWizardUtils;


import com.android.settings.R;

public class SetupWizardFaceUnlockActivityOption extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceunlock_settings_option);
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = new Intent();
        SetupWizardUtils.copySetupExtras(getIntent(), intent);
        intent.setClassName("com.android.settings", "com.android.settings.anc.SetupWizardFaceUnlockActivity");
        startActivity(intent);
        finish();
    }
}