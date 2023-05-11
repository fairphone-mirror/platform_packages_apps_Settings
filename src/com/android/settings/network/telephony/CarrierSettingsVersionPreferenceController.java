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
import android.text.TextUtils;
import android.util.Log;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.network.CarrierConfigCache;

public class CarrierSettingsVersionPreferenceController extends BasePreferenceController {

    private int mSubscriptionId;
    private CarrierConfigCache mCarrierConfigCache;
    private SubscriptionManager mSubscriptionManager;
    private String TAG = "CarrierSettingsVersion";

    public CarrierSettingsVersionPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mCarrierConfigCache = CarrierConfigCache.getInstance(context);
        mSubscriptionManager = SubscriptionManager.from(context);
        mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public void init(int subscriptionId) {
        mSubscriptionId = subscriptionId;
    }

    @Override
    public CharSequence getSummary() {
        final PersistableBundle config = mCarrierConfigCache.getConfigForSubId(mSubscriptionId);
        if (config == null) {
            return null;
        }

	//return config.getString(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING);
	// add for BSPA-232134 2023-1-16 begin
        String version = config.getString(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING);
        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(mSubscriptionId);
        String mccmnc = "";
        if (subInfo != null) {
            mccmnc = subInfo.getMccString() + subInfo.getMncString();
        }
        String summary = mccmnc + " " + version;

        return summary;
	// add for BSPA-232134 2023-1-16 end
    }

    @Override
    public int getAvailabilityStatus() {
        // modify for FP5-875, return unsupported directly just like FP4
        //return TextUtils.isEmpty(getSummary()) ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
        return UNSUPPORTED_ON_DEVICE;
    }
}
