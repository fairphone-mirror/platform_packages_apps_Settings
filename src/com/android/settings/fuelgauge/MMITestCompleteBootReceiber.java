package com.android.settings.fuelgauge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.SystemProperties;

public class MMITestCompleteBootReceiber extends BroadcastReceiver {
    private static final String TAG = "MMITestCompleteBootReceiber";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && SystemProperties.getBoolean("dev.fp.MMITest", false)) {
            try{
                Intent intentMMI = new Intent();
                intentMMI.setClassName("com.android.mmi",
                        "com.android.mmi.MMITest");
                intentMMI.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentMMI);
                Log.e(TAG, "----- start mmi.");
            }catch(Exception e){
                Log.e(TAG,"SimCompleteBootReceiver start mmi fail ");
            }
        }
    }
}
