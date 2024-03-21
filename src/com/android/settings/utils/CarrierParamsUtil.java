/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.utils;

import android.content.Context;
import android.os.LocaleList;
import android.os.PersistableBundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Locale;

public final class CarrierParamsUtil {

    private static final String TAG = CarrierParamsUtil.class.getSimpleName();

    private static XmlPullParser mParser;

    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private Context mContext;

    private static CarrierParamsUtil mInstance = null;

    private static PersistableBundle mCache = null;
    private static int mLastSubid = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static String mLastLanguage = "";

    private CarrierParamsUtil(Context context) {
        mContext = context;
        init(context);
    }

    public static CarrierParamsUtil loadInstance(Context context) {
        if (mInstance == null) {
            mInstance = new CarrierParamsUtil(context);
        }
        return mInstance;
    }

    private void init(Context context) {
        mTelephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mSubscriptionManager = SubscriptionManager.from(context);
        mParser = context.getResources().getXml(R.xml.carrier_params);
    }

    public PersistableBundle getCarrierParams(int subId) {

        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return new PersistableBundle();

        String language = getCurrentLanguage();

        if (mLastSubid != subId || !language.equals(mLastLanguage) || mCache == null) {
            SubscriptionInfo subscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
            if (subscriptionInfo != null) {
                String mcc = subscriptionInfo.getMccString();
                String mnc = subscriptionInfo.getMncString();
                Log.i(TAG, "mcc = " + mcc + "mnc = " + mnc + "language = " + language);

                mCache = loadParam(mcc, mnc, language);
                mLastSubid = subId;
                mLastLanguage = language;
            }else {
                return new PersistableBundle();
            }
        }

        return mCache;

    }


    private PersistableBundle loadParam(String mcc, String mnc, String language) {

        if (mParser == null) {
            mParser = mContext.getResources().getXml(R.xml.carrier_params);
        }

        PersistableBundle param = new PersistableBundle();
        try {
            if (mParser == null) {
                return param;
            }
            Log.i(TAG, "readParamFromXml");

            int event;
            while (((event = mParser.next()) != XmlPullParser.END_DOCUMENT)) {
                if (event == XmlPullParser.START_TAG && "carrier_ui_params".equals(mParser.getName())) {
                    // Skip this fragment if it has filters that don't match.
                    if (!checkFilters(mParser, mcc, mnc, language)) {
                        continue;
                    }
                    PersistableBundle paramFragment = PersistableBundle.restoreFromXml(mParser);
                    param.putAll(paramFragment);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, e.toString());
        }

        mParser = null;

        return param;
    }


    private boolean checkFilters(XmlPullParser parser, String mcc, String mnc, String language) {

        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            boolean result = true;
            String attribute = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            switch (attribute) {
                case "mcc":
                    result = value.replaceFirst("^0+", "").equalsIgnoreCase(mcc.replaceFirst("^0" +
                            "+", ""));
                    break;
                case "mnc":
                    result = value.replaceFirst("^0+", "").equalsIgnoreCase(mnc.replaceFirst("^0" +
                            "+", ""));
                    break;
                case "language":
                    result = value.equalsIgnoreCase(language);
                    break;
                default:
                    Log.e(TAG, "Unknown attribute " + attribute + "=" + value);
                    result = false;
                    break;
            }
            if (!result) {
                return false;
            }
        }
        return true;
    }


    private String getCurrentLanguage() {
        Locale current = LocaleList.getDefault().get(0);
        return current.getLanguage();
    }

}
