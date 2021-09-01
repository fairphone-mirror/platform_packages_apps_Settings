package com.android.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;


import java.lang.reflect.Method;
import java.util.List;


public class LaunchMyFairphoneActivity extends Activity {
    private static final int NEXT_REQUEST_CODE = 1;
    private static final String MY_FAIRPHONE_PACKAGE_NAME = "com.fairphone.myfairphone";
    private static final String MY_FAIRPHONE_CLASS_NAME = "com.fairphone.presentation.ui.activity.onboarding.DeviceOnboardingActivity";
    private boolean isResumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        if(isAppInstalled(MY_FAIRPHONE_PACKAGE_NAME)) {
            startMyFairphone();
        } else {
            returnToGoogleSetupWizard(1);
        }
    }

    @Override
    protected void onResume() {
        if(isResumed) {
            returnToGoogleSetupWizard(1);
        } else {
            isResumed = true;
        }
        super.onResume();
    }

    private void returnToGoogleSetupWizard(int code) {
        Intent i = new Intent("com.android.wizard.NEXT");
        //i.putExtra("scriptUri", getIntent().getStringExtra("scriptUri"));
        i.putExtra("actionId", getIntent().getStringExtra("actionId"));
        i.putExtra("wizardBundle", getIntent().getBundleExtra("wizardBundle"));
       // i.putExtra("theme", getIntent().getStringExtra("theme"));
        i.putExtra("com.android.setupwizard.ResultCode", code);
        startActivityForResult(i, 500);
    }

    private void startMyFairphone() {
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        ComponentName cn = new ComponentName(MY_FAIRPHONE_PACKAGE_NAME, MY_FAIRPHONE_CLASS_NAME);              
        launchIntent.setComponent(cn);  
        startActivity(launchIntent);  
    }

    private boolean isAppInstalled(String packageName) {
        PackageManager pm = getPackageManager();
        List<PackageInfo> pInfo = pm.getInstalledPackages(0);
        if (pInfo != null) {
            for (int i = 0; i < pInfo.size(); i++) {
                String pn = pInfo.get(i).packageName;
                if (packageName.equals(pn)) {
                    return true;
                }
            }
        }
        return false;
    }
}
