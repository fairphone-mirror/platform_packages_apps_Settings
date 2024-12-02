package com.android.settings.anc.util;


import static com.google.android.setupcompat.util.WizardManagerHelper.EXTRA_IS_FIRST_RUN;
import static com.google.android.setupcompat.util.WizardManagerHelper.EXTRA_IS_SETUP_FLOW;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.util.Arrays;
import java.util.List;


public class SetupWizardUtils {
    private static final String TAG = "SetupWizardUtils";

    public static String getThemeString(Intent intent) {
        String theme = intent.getStringExtra(WizardManagerHelper.EXTRA_THEME);
        if (theme == null) {
            theme = "";
        }
        return theme;
    }

    public static void copySetupExtras(Intent fromIntent, Intent toIntent) {
        WizardManagerHelper.copyWizardManagerExtras(fromIntent, toIntent);
    }

    public static Bundle copyLifecycleExtra(Bundle srcBundle, Bundle dstBundle) {
        for (String key :
                Arrays.asList(
                        EXTRA_IS_FIRST_RUN,
                        EXTRA_IS_SETUP_FLOW)) {
            dstBundle.putBoolean(key, srcBundle.getBoolean(key, false));
        }
        return dstBundle;
    }

    public static void returnToGoogleSetupWizard(Activity activity) {
        Intent nextIntent = WizardManagerHelper.getNextIntent(activity.getIntent(), -1);
        List<ResolveInfo> queryIntentActivities = activity.getPackageManager().queryIntentActivities(nextIntent, 0);
        if (queryIntentActivities == null || queryIntentActivities.isEmpty()) {
            Log.d(TAG, "No Activity found to handle Intent");
        } else {
            activity.startActivityForResult(nextIntent, 1000);
        }
    }
}