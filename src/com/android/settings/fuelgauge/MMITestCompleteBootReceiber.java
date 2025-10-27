package com.android.settings.fuelgauge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.SystemProperties;

public class MMITestCompleteBootReceiber extends BroadcastReceiver {
    private static final String TAG = "MMITestCompleteBootReceiber";

    private static final String HOST_CODE_MMITEST = "2886";
    private static final String HOST_CODE_MMITEST_FRIENDLY = "8378";

    @Override
    public void onReceive(Context context, Intent intent) {
            try {
                String packageName = "com.android.mmifriendly";
                Intent intentMMI = new Intent();
                intentMMI.setClassName(packageName,
                        "com.android.mmi.MMITest");
                intentMMI.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentMMI);
                Log.d(TAG, "start MMI activity");
            } catch(Exception e) {
                Log.e(TAG,"Failed to start MMI activity ");
            }

    }
}
