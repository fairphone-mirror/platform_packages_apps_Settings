/*
 * Copyright (C) 2020-2022 Fairphone B.V.
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

package com.android.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.setupwizardlib.util.ResultCodes;
import com.android.setupwizardlib.util.WizardManagerHelper;

import java.lang.reflect.Method;
import java.util.List;

public class LaunchMyFairphoneActivity extends Activity {
    private static final int NEXT_REQUEST_CODE = 1;
    private static final int RESULT_SKIP = ResultCodes.RESULT_SKIP;
    private static final String MY_FAIRPHONE_PACKAGE_NAME = "com.fairphone.myfairphone";
    private static final String MY_FAIRPHONE_CLASS_NAME = "com.fairphone.myfairphone.MainActivity";
    private boolean mIsResumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        if (isAppInstalled(MY_FAIRPHONE_PACKAGE_NAME)) {
            startMyFairphone();
        } else {
            finish(RESULT_OK);
            done(true);
        }
    }

    @Override
    protected void onResume() {
        if (mIsResumed) {
            finish(RESULT_OK);
            done(true);
        } else {
            mIsResumed = true;
        }
        super.onResume();
    }

    private void finish(int resultCode) {
        setResult(resultCode);
    }

    private void done(boolean success) {
        int resultCode = success ? Activity.RESULT_OK : RESULT_SKIP;
        Intent intent = WizardManagerHelper.getNextIntent(getIntent(), resultCode);
        startActivityForResult(intent, NEXT_REQUEST_CODE);
    }

    private void startMyFairphone() {
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
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
