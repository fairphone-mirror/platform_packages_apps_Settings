package com.android.settings.applications.manageapplications;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <pre>
 *     author : zhangtianwen
 *     e-mail : tianwen.zhang@t2mobile.com
 *     time   : 2021/09/03
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class CarrierAppUtils {

    public static final ArrayList<String> sPreInstall =
            new ArrayList<String>(Arrays.asList("AppEnabler.apk", "orange_appcenter.apk", "Orange_Manual_Selector.apk","fetself.apk","friday_vod.apk","friday_wallet.apk","go_happy.apk","Omusic.apk"));

    private static final String KEY_CARRIER_PREINSTALL = "carrier_preinstall";

    private static final String CARRIER_PREINSTALL_ARRAY[] = {"de.telekom.tsc", "com.orange.update", "com.orange.aura.oobe","com.fetself","net.fetnet.fetvod","com.fet.fridaywallet","com.gohappy.mobileapp","com.omusic.gPhone"};

    private static String[] sPreinstallList;

    public static void init(Context context){
        sPreinstallList = getKeyCarrierPreinstall(context.getApplicationContext());
    }

    public static String[] getKeyCarrierPreinstall(Context context) {
        String[] preInstallApps = {};
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle config = configManager.getConfig();
        if (config != null) {
            preInstallApps = config.getStringArray(KEY_CARRIER_PREINSTALL);
        }
        return preInstallApps;
    }

    private static boolean isKeepPreinstall(String apkName) {
        if (sPreinstallList == null) {
            return false;
        }
        if (sPreinstallList.length == 0) {
            return false;
        }
        int index = sPreInstall.indexOf(apkName);
        String checkingPkg = CARRIER_PREINSTALL_ARRAY[index];
        for (String s : sPreinstallList) {
            if (s.equals(checkingPkg)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isNeedKeep(String apkName) {
        if (!sPreInstall.contains(apkName)) {
            return true;
        }

        if (isKeepPreinstall(apkName)) {
            return true;
        }
        return false;
    }

}
