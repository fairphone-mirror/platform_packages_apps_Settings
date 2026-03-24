package com.android.settings.connecteddevice;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settings.core.BasePreferenceController;
import android.content.Intent;

public class AptxacuPreferenceController extends BasePreferenceController {
    private final String TAG = "aptxacu_apps_settings";
    private Context mContext;

    public AptxacuPreferenceController(Context context, String key) {
        super(context, key);
        mContext = context;
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if(TAG.equals(preference.getKey())) {
            try{
                Intent intent = new Intent();
                intent.setClassName("com.qualcomm.qtil.aptxacu",
                        "com.qualcomm.qtil.aptxacu.aptxacuSettingsActivity");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            }catch(Exception e){
                android.util.Log.e(TAG,"aptxacuSettingsActivity start fail ");
            }
            return true;
        }
        return false;
    }
}


