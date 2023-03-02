/**
 * Copyright (C) 2020 Arima Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
package com.arima.settings;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;

import javax.net.ssl.HttpsURLConnection;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;

import javax.net.ssl.*;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.HostnameVerifier;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import android.os.Build;

//<2020/09/23,lucygao,OEM unlock flow change.
public class OemLockVerifier {

    private static final String TAG = "OemLockVerifier";

    private static final int CONNECT_TIMEOUT = 30 * 1000;
    private static final int READ_TIMEOUT = 30 * 1000;

    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_VERIFY_FAIL_WRONG_CODE = 400;
    public static final int HTTP_VERIFY_FAIL_NO_SUCH_PHONE = 404;
    public static final int HTTP_VERIFY_FAIL_UNKNOWN = 900;

    private static final String SVR_URL = "https://factory.fairphone.com/api/unlock-codes";
    //private static final String DEBUG_SVR_URL = "https://app-26f9cbc0-2444-4200-a713-6575e61635b4.cleverapps.io/api/unlock-codes";

    //private static final String DEBOUG_X_API_KEY = "E/xUciBHocHSzETALqTk9Q==";
    //private static final String X_API_KEY = "p0C44XA4efzIqbchuzGpYw==";

    private static final String FP4_RELEASE_API_KEY = "nYhYMjXvVRd8SCNwPOTNuQ==";//warning !!!  Do not in code now !!!!!!

    private String mTargetUrl;

    private String mTargetUrlTest;

    private Context mContext;
    private PowerManager.WakeLock wakeLock = null;
    public onResponseListener mResponseListener = null;

    public OemLockVerifier(Context context, onResponseListener listener) {
        mContext = context;
        mResponseListener = listener;
    }

    public interface onResponseListener {
        void onFinish(int check_code, String msg);
    }

    public void queryVerifyResult(String imei, String sn) {
        //prepare imei, serial, user entered password
        mTargetUrlTest = SVR_URL + "/" + imei + "/" + sn;
        // if (isDebugOsBuild()) {
        //     mTargetUrlTest = DEBUG_SVR_URL + "/" + imei + "/" + sn;
        // }
        Log.d(TAG, "targetUrl=" + mTargetUrlTest);
        if (imei == null || sn == null) {
            if (mResponseListener != null)
                mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "Invalid parameters");
            return;
        }

        //If not available network, direct return
        if (false) {
            if (mResponseListener != null)
                mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "No network connection");
            return;
        }

        //setup http connection
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "oem Lock verifier Start");
                acquireWakeLock();
                try {
                    SSLContext sslcontext = SSLContext.getInstance("SSL");
                    sslcontext.init(null, new TrustManager[]{new MyX509TrustManager()}, new java.security.SecureRandom());

                    HostnameVerifier ignoreHostnameVerifier = new HostnameVerifier() {
                        public boolean verify(String s, SSLSession sslsession) {
                            Log.d(TAG, "WARNING: Hostname is not matched for cert.");
                            return true;
                        }
                    };

                    URL url = new URL(mTargetUrlTest);
                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    try {
                        conn.setDefaultHostnameVerifier(ignoreHostnameVerifier);
                        conn.setDefaultSSLSocketFactory(sslcontext.getSocketFactory());
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setDoOutput(true);
                        conn.setDoInput(true);
                        conn.setUseCaches(false);
                        conn.setConnectTimeout(CONNECT_TIMEOUT);
                        conn.connect();

                        int resp_code = conn.getResponseCode();
                        Log.d(TAG, "queryVerifyResult()--resp_code=" + resp_code);
                        String verify_code = "";
                        if (resp_code == HTTP_CREATED) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            String output;
                            while ((output = br.readLine()) != null) {
                                verify_code += output;
                            }
                            int begin = verify_code.indexOf(":");
                            int last = verify_code.length();
                            verify_code = verify_code.substring(begin + 2, last - 2);
                        }
                        Log.d(TAG, "queryVerifyResult()--verify_code=" + verify_code);

                        if (resp_code == HttpsURLConnection.HTTP_NOT_FOUND) {
                            if (mResponseListener != null)
                                mResponseListener.onFinish(resp_code, "URL not found");
                        } else {
                            if (mResponseListener != null)
                                mResponseListener.onFinish(resp_code, verify_code);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (mResponseListener != null)
                            mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "inner Unknown failure");
                    } finally {
                        conn.disconnect();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (mResponseListener != null)
                        mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "outer Unknown failure");
                }
                releaseWakeLock();
                Log.d(TAG, "oem Lock verifier - Thread end");
            }
        }).start();
    }

    public void queryVerifyResultGet(String user_pass, String imei, String sn) {
        //prepare imei, serial, user entered password
        mTargetUrl = SVR_URL + "/" + imei + "/" + sn + "/" + user_pass;
        // if (isDebugOsBuild()) {
        //     mTargetUrl = DEBUG_SVR_URL + "/" + imei + "/" + sn + "/" + user_pass;
        // }
        Log.d(TAG, "queryVerifyResultGet--targetUrl=" + mTargetUrl);
        if (imei == null || sn == null || user_pass == null) {
            if (mResponseListener != null)
                mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "Invalid parameters");
            return;
        }

        //If not available network, direct return
        if (false) {
            if (mResponseListener != null)
                mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "No network connection");
            return;
        }

        //setup http connection
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "queryVerifyResultGet--oem Lock verifier Start");
                acquireWakeLock();
                try {
                    String token;
                    // if (isDebugOsBuild()) {
                    //     token=DEBOUG_X_API_KEY;
                    // }else{
                    //     token=X_API_KEY;
                    // }
                    token = FP4_RELEASE_API_KEY;
                    SSLContext sslcontext = SSLContext.getInstance("SSL");
                    sslcontext.init(null, new TrustManager[]{new MyX509TrustManager()}, new java.security.SecureRandom());

                    HostnameVerifier ignoreHostnameVerifier = new HostnameVerifier() {
                        public boolean verify(String s, SSLSession sslsession) {
                            Log.d(TAG, "WARNING: Hostname is not matched for cert.");
                            return true;
                        }
                    };

                    URL url = new URL(mTargetUrl);
                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    try {
                        conn.setDefaultHostnameVerifier(ignoreHostnameVerifier);
                        conn.setDefaultSSLSocketFactory(sslcontext.getSocketFactory());
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("X-API-KEY", token);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setDoInput(true);
                        conn.setUseCaches(false);
                        conn.setConnectTimeout(CONNECT_TIMEOUT);
                        conn.connect();

                        int resp_code1 = conn.getResponseCode();
                        Log.d(TAG, "queryVerifyResultGet()--resp_code1=" + resp_code1);
                        String verify_code1 = "";
                        if (resp_code1 == HTTP_OK) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            String output;
                            while ((output = br.readLine()) != null) {
                                verify_code1 += output;
                            }
                        }
                        Log.d(TAG, "queryVerifyResultGet()--verify_code1=" + verify_code1);
                        if (resp_code1 == HttpsURLConnection.HTTP_NOT_FOUND) {
                            if (mResponseListener != null)
                                mResponseListener.onFinish(resp_code1, "URL not found");
                        } else {
                            if (mResponseListener != null)
                                mResponseListener.onFinish(resp_code1, verify_code1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (mResponseListener != null)
                            mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "inner Unknown failure");
                    } finally {
                        conn.disconnect();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (mResponseListener != null)
                        mResponseListener.onFinish(HTTP_VERIFY_FAIL_UNKNOWN, "outer Unknown failure");
                }
                releaseWakeLock();
                Log.d(TAG, "queryVerifyResultGet--oem Lock verifier - Thread end");
            }
        }).start();
    }

    private void acquireWakeLock() {
        Log.d(TAG, "acquireWakeLock");

        if (null == wakeLock) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OEM_UNLOCK");
            if (null != wakeLock) {
                Log.d(TAG, "wakeLock acquire.");
                wakeLock.acquire();
            }
        }
    }

    private void releaseWakeLock() {
        Log.d(TAG, "releaseWakeLock");
        if (null != wakeLock) {
            Log.d(TAG, "wakeLock release.");
            wakeLock.release();
            wakeLock = null;
        }
    }

    private boolean isDebugOsBuild() {
        return "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE);
    }

    public static class MyX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate certificates[], String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] ax509certificate, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
//>2020/09/23,lucygao.