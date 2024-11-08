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

package com.android.settings.network.telephony;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.network.CarrierConfigCache;

public class CarrierSettingsVersionPreferenceController extends BasePreferenceController {

    private int mSubscriptionId;
    private CarrierConfigCache mCarrierConfigCache;
    private SubscriptionManager mSubscriptionManager;
    private String TAG = "CarrierSettingsVersion";

    // add by T2M.dengxiangyu for FP5-2386 2023-07-26
    private TelephonyManager mTelephonyManager;

    public CarrierSettingsVersionPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mCarrierConfigCache = CarrierConfigCache.getInstance(context);
        mSubscriptionManager = SubscriptionManager.from(context);
        mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
    }

    public void init(int subscriptionId) {
        mSubscriptionId = subscriptionId;
    }

    @Override
    public CharSequence getSummary() {
        String summary = "";

        if (mSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            for (int slotId = 0; slotId < mTelephonyManager.getActiveModemCount();
                    slotId++) {
                SubscriptionInfo info = mSubscriptionManager.
                    getActiveSubscriptionInfoForSimSlotIndex(slotId);
                if (info != null) {
                    int subId = info.getSubscriptionId();
                    String summaryBySubId = getSummaryBySubId(subId);
                    if (summaryBySubId != null) {
                        summary += "SIM" + (slotId + 1) + ": " + summaryBySubId + System.lineSeparator();
                    }
                }
            }
        } else {
            summary = getSummaryBySubId(mSubscriptionId);
        }

        return summary;



    }

    @Override
    public int getAvailabilityStatus() {
        if (mSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return TextUtils.isEmpty(getSummary()) ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
        } else {
            return UNSUPPORTED_ON_DEVICE;
        }
    }

    private String getSummaryBySubId(int subId) {
        final PersistableBundle config = mCarrierConfigCache.getConfigForSubId(subId);
        if (config == null) {
            return null;
        }

        String version = config.getString(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING);
        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        String mccmnc = "";
        if (subInfo != null) {
            mccmnc = subInfo.getMccString() + subInfo.getMncString();
        }
        String summary = mccmnc + " " + version;

        return summary;
    }
}
