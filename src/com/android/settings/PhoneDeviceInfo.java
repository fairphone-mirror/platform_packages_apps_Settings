/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.view.View;
import android.os.SystemProperties;
import android.os.Build;
import com.android.settingslib.DeviceInfoUtils;
import com.android.settings.Utils;
import android.text.format.DateUtils;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Message;
import java.io.FileReader;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import android.os.Environment;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.FileNotFoundException;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import android.os.storage.StorageManager;
import android.text.format.Formatter;
import com.android.internal.util.MemInfoReader;
import java.lang.Math;
/**
 * The "dialog" that shows from "Manual" in the Settings app.
 */
public class PhoneDeviceInfo extends Activity {
    private static final String TAG = "PhoneDeviceInfo";
    private static final int EVENT_UPDATE_STATS = 500;
    private TextView mdeviceInfo;
    private TextView mHwstage;
    private TextView mEmcp;
    private TextView mBuildType;
    private TextView mFactorySN;
    private TextView mKernelVersion;
    private TextView mBaseband;
    private TextView mAPNtableversion;
    private TextView mAudioversion;
    private TextView mAudioSmartPAversion;
    private TextView mTPversion;
    private TextView mMFGDate;
    private TextView mTFT;
    private TextView mUpdateTime;
    private TextView mGMSversion;
    private TextView mCameraTuning;
    static final String BASEBAND_PROPERTY = "gsm.version.baseband";
    static final String FACTORY_SN_PROPERTY = "ro.vendor.fp.trace.bsn";
    static final String MFG_DATE_PROPERTY = "ro.vendor.fp.mfg.date";
    static final String TFT_DATE_PROPERTY = "persist.sys.fp.tft.date";
    static final String TFT_PERSIST_PROPERTY = "sys.fp.tft";
    private Handler mHandler;

    private static final String PARTNER_APNS_PATH = "etc/apns-conf.xml";
    private static final String OEM_APNS_PATH = "telephony/apns-conf.xml";
    private static final String OTA_UPDATED_APNS_PATH = "misc/apns/apns-conf.xml";
    private static final String OLD_APNS_PATH = "etc/old-apns-conf.xml";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.deviceinfo_activity, null);
        mHwstage = (TextView)view.findViewById(R.id.hwstage);
        mEmcp = (TextView)view.findViewById(R.id.emcp);
        mBuildType = (TextView)view.findViewById(R.id.buildtype);
        mFactorySN = (TextView)view.findViewById(R.id.factorycn);
        mKernelVersion = (TextView)view.findViewById(R.id.kernelversion);
        mBaseband = (TextView)view.findViewById(R.id.baseband);
        mAPNtableversion = (TextView)view.findViewById(R.id.apntableversion);
        mAudioversion = (TextView)view.findViewById(R.id.audioversion);
        mAudioSmartPAversion = (TextView)view.findViewById(R.id.audiosmartpaversion);
        mTPversion = (TextView)view.findViewById(R.id.tpversion);
        mMFGDate = (TextView)view.findViewById(R.id.mfgdate);
        mTFT = (TextView)view.findViewById(R.id.tft);
        mUpdateTime = (TextView)view.findViewById(R.id.updatetime);
        mGMSversion = (TextView)view.findViewById(R.id.gmsversion);
        mCameraTuning = (TextView)view.findViewById(R.id.cameratuning);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showAllDeviceInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
        getHandler().sendEmptyMessage(EVENT_UPDATE_STATS);
    }
    @Override
    public void onStop() {
        super.onStop();
        getHandler().removeMessages(EVENT_UPDATE_STATS);
    }

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new MyHandler(mUpdateTime,mTFT);
        }
        return mHandler;
    }

    private void showAllDeviceInfo(){
        mHwstage.setText("HW Stage : " + readHWStage());
        mEmcp.setText("EMCP : \n" + readEMCP());
        mBuildType.setText("Build type : " + readBuildtype());
        mFactorySN.setText("Factory SN : " + readFactorySN());
        mKernelVersion.setText("Kernel Version : \n" + readKernelVersion());
        mBaseband.setText("Baseband version : \n" + readBasebandversion());
        mAPNtableversion.setText("APN table version : " + readAPNtableversion());
        mAudioversion.setText("Audio Version : " + readAudioVersion());
        mAudioSmartPAversion.setText("Audio Smart PA version : " + readAudioSmartPAversion());
        mTPversion.setText("TP/LCM Version : " + readTPLCMVersion());
        mMFGDate.setText("MFG date : " + readMFGdate());
        mTFT.setText("TFT : \n" + readTFT());
        mUpdateTime.setText("Up time \n" + DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000));
        mGMSversion.setText("GMS version : \n" + readGMSversion());
        mCameraTuning.setText("Camera tuning Version : \n" + readCameraTuningversion());
    }

    private String readGMSversion(){
        return SystemProperties.get("ro.com.google.gmsversion");
    }

    private String readHWStage(){
        /*String version = null;
        try {
            InputStream is = new FileInputStream("/sys/class/board_id/version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            version = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }*/
        return SystemProperties.get("ro.vendor.hw_version");
    }


    private String readEMCP(){
        String emcpinfo = null;
        String vendor = null;
        String model = null;
        try {
            InputStream is = new FileInputStream("sys/emkit/info/emkit_memory_vendor");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            vendor = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "readEMCP fail" + e);
        }

        try {
            InputStream is = new FileInputStream("sys/emkit/info/emkit_memory");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            model = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "readEMCP fail" + e);
        }

        final StorageManagerVolumeProvider smvp = new StorageManagerVolumeProvider(getSystemService(StorageManager.class));
        final PrivateStorageInfo info = PrivateStorageInfo.getPrivateStorageInfo(smvp);
        final long privateTotalBytes = info.totalBytes;
        String totalRomBytes = Formatter.formatFileSize(this, privateTotalBytes);

        MemInfoReader memInfo = new MemInfoReader();
        memInfo.readMemInfo();
        double totalkb = memInfo.getTotalSize();
        double totalGB = totalkb/1024/1024/1024;
        int realGB = (int)Math.ceil(totalGB);
        String totalRamBytes = Formatter.formatFileSize(this, (long)realGB*1000*1000*1000);
        emcpinfo = vendor + " " + model + " " + totalRamBytes + " " + totalRomBytes;
        return emcpinfo;
    }

    private String readBuildtype(){
        return Build.TYPE;
    }

    private String readFactorySN(){
        return SystemProperties.get(FACTORY_SN_PROPERTY,
            getString(R.string.device_info_default));
    }

    private String readKernelVersion(){
        return DeviceInfoUtils.getFormattedKernelVersion(this);
    }

    private String readBasebandversion(){
        if (Utils.isSupportCTPA(getApplicationContext())) {
            String baseBands = SystemProperties.get(BASEBAND_PROPERTY,
                   getString(R.string.device_info_default));
             if (null != baseBands) {
                String[] baseBandArray = baseBands.split(",");
                if ((baseBandArray != null) && (baseBandArray.length > 0)) {
                   return baseBandArray[0];
                }
            }
        }
        return SystemProperties.get(BASEBAND_PROPERTY,
               getString(R.string.device_info_default));
    }

    private File pickSecondIfExists(File sysApnFile, File altApnFile) {
        if (altApnFile.exists()) {
            return altApnFile;
        } else {
            return sysApnFile;
        }
    }

    private File getApnConfFile() {
        File confFile = new File(Environment.getRootDirectory(), PARTNER_APNS_PATH);
        File oemConfFile =  new File(Environment.getOemDirectory(), OEM_APNS_PATH);
        File updatedConfFile = new File(Environment.getDataDirectory(), OTA_UPDATED_APNS_PATH);
        File productConfFile = new File(Environment.getProductDirectory(), PARTNER_APNS_PATH);
        confFile = pickSecondIfExists(confFile, oemConfFile);
        confFile = pickSecondIfExists(confFile, productConfFile);
        confFile = pickSecondIfExists(confFile, updatedConfFile);
        return confFile;
    }

    private String readAPNtableversion(){
        String version = null;
        XmlPullParser confparser = null;
        File confFile = getApnConfFile();
        FileReader confreader = null;
        try {
            confreader = new FileReader(confFile);
            confparser = Xml.newPullParser();
            confparser.setInput(confreader);
            XmlUtils.beginDocument(confparser, "apns");

            // Sanity check. Force internal version and confidential versions to agree
            int confversion = Integer.parseInt(confparser.getAttributeValue(null, "version"));
            version = confversion + "";
        } catch (FileNotFoundException e) {
            Log.e(TAG, "readAPNtableversion FileNotFoundException ");
        } catch (Exception e) {
        }
        return version;
    }

    private String readAudioVersion(){
        String version = null;
        try {
            InputStream is = new FileInputStream("/vendor/etc/audio_ver");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            version = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return version;
    }

    private String readAudioSmartPAversion(){
        String version = null;
        try {
            InputStream is = new FileInputStream("/vendor/etc/aw882xx_ver");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            version = reader.readLine();
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        return version;
    }

    private String readTPLCMVersion(){
        String version = null;
        try {
            InputStream is = new FileInputStream("sys/emkit/info/touch");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            while ((version = reader.readLine()) != null) {
                Log.e(TAG, "readTPLCMVersion " + version);
                if(version.contains("fw_ver")){
                    break;
                }
            }
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getVersion fail" + e);
        }
        //read null try again
        if(version == null){
            try {
                InputStream is = new FileInputStream("sys/emkit/info/touch");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                while ((version = reader.readLine()) != null) {
                    Log.e(TAG, "readTPLCMVersion " + version);
                    if(version.contains("fw_ver")){
                        break;
                    }
                }
                reader.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "getVersion fail" + e);
            }
        }
        return version != null ? version.substring(7):getString(R.string.device_info_default);
    }

    private String formatDateCode(byte[] raw) {
        String dayString = "**";
        String monthString = "**";
        String yearString = "****";
        int i;

        if (raw.length != 3) {
            return null;
        }

        String dayRule = "123456789ABCDEFGHIJKLMNOPQRSTUV";
        String monthRule = "EFGHIJKLMNOP";
        String yearRule = "UVWXYZ6ABCDEFGHIJKLMNOPQ";// "KLMNOPQRSTUVWXYZ";
        // get day value
        i = dayRule.indexOf(raw[0]);
        if (i >= 0) {
            dayString = String.format("%02d", i + 1);
        }
        // get month value
        i = monthRule.indexOf(raw[1]);
        if (i >= 0) {
            monthString = String.format("%02d", i + 1);
        }
        // get year value
        i = yearRule.indexOf(raw[2]);
        if (i >= 0) {
            yearString = String.format("20%02d", i + 10);
        }

        return yearString + monthString + dayString;
    }

    private String readMFGdate(){
        String date = SystemProperties.get(MFG_DATE_PROPERTY,"");
        if(date != null && !"".equals(date)){
            return formatDateCode(date.getBytes());
        }
        return getString(R.string.device_info_default);
    }

    private String readTFT(){
        long date = SystemProperties.getLong(TFT_DATE_PROPERTY,0);
        if(date == 0){
            long persistTFTdate = SystemProperties.getLong(TFT_PERSIST_PROPERTY,0);
            if(persistTFTdate != 0){
                date = persistTFTdate;
            }
        }
        if(date == 0){
            return DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000);
        }else{
            if(SystemClock.elapsedRealtime() > date){
                return DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000);
            }else{
                return DateUtils.formatElapsedTime(date/1000);
            }
        }
    }

    private String readCameraTuningversion(){
        String cameratuning = "";
        String version = null;
        try {
            InputStream is = new FileInputStream("vendor/etc/camera/tuningversion_fp5.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            while ((version = reader.readLine()) != null) {
                Log.e(TAG, "readCameraTuningversion version " + version);
                if(version.contains("==imx800")){
                    cameratuning += "Camera_IMX800:\n";
                }else if(version.contains("==imx858")){
                    cameratuning += "\nCamera_IMX858:\n";
                }else if(version.contains("==s5kjn1")){
                    cameratuning += "\nCamera_S5KJN1:\n";
                }else{
                    cameratuning += version.substring(7) +";";
                }
            }
            reader.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "readCameraTuningversion fail" + e);
        }
        return cameratuning;
    }

    private class MyHandler extends Handler {
        private TextView m_updatetime;
        private TextView m_tft;
        private long m_tftdate;
        public MyHandler(TextView updatetime, TextView tft) {
            m_updatetime = updatetime;
            m_tft = tft;
            m_tftdate = SystemProperties.getLong(TFT_DATE_PROPERTY,0);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_STATS:
                    m_updatetime.setText("Up time \n" + DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000));
                    m_tftdate += 1000;
                    if(m_tftdate < SystemClock.elapsedRealtime()){
                        m_tft.setText("TFT :  \n" + DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000));
                    }else{
                        m_tft.setText("TFT :  \n" + DateUtils.formatElapsedTime(m_tftdate/1000));
                    }
                    
                    sendEmptyMessageDelayed(EVENT_UPDATE_STATS, 1000);
                    break;

                default:
                    throw new IllegalStateException("Unknown message " + msg.what);
            }
        }
    }
}

