package com.android.settings.applications.manageapplications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <pre>
 *     author : zhangtianwen
 *     e-mail : tianwen.zhang@t2mobile.com
 *     time   : 2021/06/30
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class CarrierPackageManager {

    private static final String TAG = "CarrierPackageManager";

    public static final ArrayList<String> sDtAppList =
            new ArrayList<String>(Arrays.asList("AppEnabler.apk"));

    public static final ArrayList<String> sOrangeAppCenter =
            new ArrayList<String>(Arrays.asList("orange_appcenter.apk", "Orange_Manual_Selector.apk"));


    public static final ArrayList<String> sOrangeSIMPlmn =
            new ArrayList<String>(Arrays.asList("21403", "21421", "20801", "26003", "22610", "23101",
                    "20610", "25901", "27099", "21419", "63203", "65202",
                    "61302", "62402", "34001", "62303", "61101", "61203",
                    "63086", "60201", "62701", "41677", "64700", "61807",
                    "64602", "61002", "61701", "60400", "61404", "60801",
                    "61901", "60501"));

    public static final ArrayList<String> sDtSIMPlmn =
            new ArrayList<String>(Arrays.asList("20416", "21630", "21901", "21920", "23203", "23207", "26201", "26206"));

    private String mMccMnc = "";
    private String mPropMcc = "";

    private static class HOLDER {
        private static final CarrierPackageManager instance = new CarrierPackageManager();
    }

    public static CarrierPackageManager getInstance() {
        return HOLDER.instance;
    }

    public CarrierPackageManager() {

    }

    public void init() {
        mPropMcc = SystemProperties.get("gsm.sim.operator.numeric");
        if (mPropMcc != null && mPropMcc.length() > 4) {
            mMccMnc = mPropMcc.substring(0, 5);
        }
    }

    public boolean isNeedKeep(String apkName){
        if (!sDtAppList.contains(apkName) && !sOrangeAppCenter.contains(apkName)){
            return true;
        }

        if (sDtAppList.contains(apkName) && sDtSIMPlmn.contains(mMccMnc)){
            return true;
        }

        if (sOrangeAppCenter.contains(apkName) && sOrangeSIMPlmn.contains(mMccMnc)) {
            return true;
        }
        return false;
    }

}
