/**
 * Copyright (C) 2019 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
//<2019/08/06-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Add oem_lock password protection feature.
package com.android.settings.development;

import android.util.Log;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class fp_password {

    //<2019/10/31-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Change oem_lock password protection algorithm with IMEI+SN.
    public boolean password(String s,String checksum) {
        String checksum_tmp = "";
    //>2019/10/31-kanewang

        String md5_tmp;

        if ("".equals(checksum)) {
            return false;
        }

        md5_tmp = getMd5Hash(s);
        checksum_tmp = getCheckSum(md5_tmp.getBytes());

        if (checksum_tmp.equals(checksum))
            return true;
        else
            return false;
    }

    //<2019/10/31-kanewang, [8901][FEATURE][COMMON][SETTINGS][][]Change oem_lock password protection algorithm with IMEI+SN.
    public String getCheckSum(byte[] bytes){
        int CheckSum_tmp = 0;
        int dataLegth = (bytes.length/4);
        int i=0;
        String result = "";

        for (i = 0; i < dataLegth; i+=4) {
            CheckSum_tmp += (bytes[i+3] *0x1000000) + (bytes[i+2] *0x10000) + (bytes[i+1] *0x100) + (bytes[i+0]);
        }

        CheckSum_tmp &= 0xffffffff;
        result = Integer.toHexString(CheckSum_tmp);

        //>2019/10/31-kanewang
        //Log.i("Checksum", Integer.toHexString(CheckSum));
        return result;
    }

    public String getMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger(1, messageDigest);
            String md5 = number.toString(16);
            while (md5.length() < 32) md5 = "0" + md5;
            return md5;
        } catch (NoSuchAlgorithmException e) {
            //Log.e("MD5", e.getLocalizedMessage());
            return null;
        }
    }
}
//>2019/08/06-kanewang