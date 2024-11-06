package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import java.util.List;
import android.os.SystemProperties;
import android.app.AlertDialog;
import android.view.WindowManager;

import com.android.internal.telephony.PhoneConstants;
//import com.arima.settings.OemLockVerifier;
import android.content.DialogInterface;
import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import android.os.UserHandle;

public class PhoneCodeReceiver extends BroadcastReceiver {

    private static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static final String GMS_DISABLE = "persist.sys.gmsapp.disable";
    private static final String TAG = "PhoneCodeReceiver";
    private static final String HOST_CODE_GMS = "666";
    private static final String HOST_CODE_VERSIONINFO = "3228";
    private static final String HOST_CODE_DEVICEINFO = "02";
    private static final String HOST_CODE_REGULATORY_INFO = "3522";
    private static final String HOST_CODE_MODULEINFO = "001";
    private static final String HOST_CODE_IMS = "23486583";
    private static final String HOST_CODE_TEST_OEM_UNLOCK = "002";
    private static final String HOST_CODE_BATTERY_HEALTY = "2288379";
    private static final String BATTERY_HEALTY_ENABLE = "persist.sys.battery.healty.enable";
    private Context mContext;
    private String READ_ERROR_STR = "????????";

    // add for FP5-197 2023-03-27 begin
    private static final String HOST_CODE_ENDC = "36327";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive : " + intent.toString());
        mContext = context;
        
        final String action = intent.getAction();
        if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
            String host = intent.getData() != null ? intent.getData().getHost() : null;
            if(HOST_CODE_GMS.equals(host)){
                PackageManager pm = context.getPackageManager();
                int hasDisabled = SystemProperties.getInt(GMS_DISABLE, 0);
                List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
                for (PackageInfo pi : packages) {
                    if(pi.packageName != null && pi.packageName.startsWith("com.google")
                    && !pi.packageName.equals("com.google.android.dialer") 
                    && !pi.packageName.equals("com.google.android.networkstack.tethering") 
                    && !pi.packageName.equals("com.google.android.networkstack")
                    && !pi.packageName.equals("com.google.android.networkstack.permissionconfig")
                    && !pi.packageName.equals("com.google.android.setupwizard") 
                    && !pi.packageName.equals("com.google.android.packageinstaller") 
                    && !pi.packageName.equals("com.google.android.permissioncontroller")
                    && !pi.packageName.equals("com.google.android.apps.work.oobconfig")  
                    || pi.packageName.equals("com.android.chrome") 
                    ||pi.packageName.equals("com.android.vending"))
                    try{
                        if(hasDisabled == 0){
                            pm.setApplicationEnabledSetting(pi.packageName,PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
                        }else{
                            pm.setApplicationEnabledSetting(pi.packageName,PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
                        }
                    } catch (Exception e){
                        //do nothing
                    }
                }
                if(hasDisabled == 0){
                    SystemProperties.set(GMS_DISABLE, "1");
                }else{
                    SystemProperties.set(GMS_DISABLE, "0");
                }
            }else if(HOST_CODE_VERSIONINFO.equals(host)){
                String result = "";
                result = SystemProperties.get("ro.tct.sys.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.boot.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.rec.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.modem.ver", READ_ERROR_STR) + "\n";
                result += SystemProperties.get("ro.tct.study.ver", READ_ERROR_STR);
                openAlertDialog(result);
            }else if(HOST_CODE_DEVICEINFO.equals(host)){
                Intent i = new Intent(context, PhoneDeviceInfo.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }else if (HOST_CODE_REGULATORY_INFO.equals(host)) {
                Intent intent2 = new Intent("android.settings.SHOW_REGULATORY_INFO");
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent2);
            } else if (HOST_CODE_MODULEINFO.equals(host)) {
                Intent i = new Intent(context, ModuleDeviceInfo.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } else if (HOST_CODE_IMS.equals(host)) {
                /*boolean ims_enabled = Settings.Global.getInt(mContext.getContentResolver(), "ims_enable_settings", 0) == 1;
                Settings.Global.putInt(mContext.getContentResolver(), "ims_enable_settings",
                        !ims_enabled ? 1 : 0);
                String show = "";
                Log.i(TAG, "onReceive : ims_enabled = " + ims_enabled + ",ims_enabled set to " + !ims_enabled);
                if (!ims_enabled) {
                    show = context.getResources().getString(R.string.ims_settings_summary_on);
                } else {
                    show = context.getResources().getString(R.string.ims_settings_summary_off);
                }
                AlertDialog alert = new AlertDialog.Builder(context.getApplicationContext())
                        .setTitle(R.string.ims_settings_title)
                        .setMessage(show)
                        .setPositiveButton(android.R.string.ok, null)
                        .setCancelable(false)
                        .create();
                alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                alert.show();*/
            } else if (HOST_CODE_TEST_OEM_UNLOCK.equals(host)) {
                /*OemLockVerifier oemLockVerifier = new OemLockVerifier(context, (check_code, msg) -> Log.e(TAG, "oemLockVerifier queryVerifyResult msg > " + msg));
                oemLockVerifier.queryVerifyResult(getIMEI(), Build.getSerial());*/
            } else if (HOST_CODE_BATTERY_HEALTY.equals(host)){
                /*if(fp5txtIsExists()) {
                    String status = SystemProperties.get(BATTERY_HEALTY_ENABLE, "Close");
                    AlertDialog alert = new AlertDialog.Builder(context.getApplicationContext())
                            .setTitle(R.string.dialog_title_battery_healty)
                            .setMessage(status)
                            .setPositiveButton(R.string.launch_instant_app, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    SystemProperties.set(BATTERY_HEALTY_ENABLE, "Open");
                                }
                            })
                            .setNegativeButton(R.string.suggestion_button_close, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    SystemProperties.set(BATTERY_HEALTY_ENABLE, "Close");
                                    //charge enable
                                    writeBatEn("6000000");
                                    SystemProperties.set("persist.sys.battery.icon.enable","0");
                                    Settings.Global.putStringForUser(mContext.getContentResolver(),
                                        Settings.Global.UPDATE_BATTERY_CHARGING_MODE, System.currentTimeMillis() + "",UserHandle.myUserId());
                                }
                            })
                            .setCancelable(false)
                            .create();
                    alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    alert.show();
                }*/
            }

            // add for FP5-197 show ENDC info 2023-03-27 begin
            else if (HOST_CODE_ENDC.equals(host)) {
                /*Log.d(TAG, "ENCD code");
                Intent i = new Intent(context, BandCombination.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);*/
            }
            // add for FP5-197 show ENDC info 2023-03-27 end

        }

    }

    private void openAlertDialog(String message) {
        AlertDialog alert = new AlertDialog.Builder(mContext.getApplicationContext())
                    .setTitle(R.string.dialog_title_image_mapping)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(true)
                    .create();

            alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            alert.show();
    }

    private String getIMEI() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getImei(PhoneConstants.SIM_ID_1);
    }

    private void writeBatEn(String value) {
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            fw = new FileWriter("/sys/class/power_supply/battery/user_fcc");
            bw = new BufferedWriter(fw, 256);
            bw.write(value);
            bw.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try{
                if (bw != null)
                    bw.close();
                if (fw != null)
                    fw.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private boolean fp5txtIsExists(){
        try {
            File f = new File("/storage/emulated/0/FP5.txt");
            if(f.exists()) {
                return true;
            }else{
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
