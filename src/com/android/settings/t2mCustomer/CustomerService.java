package com.android.settings.t2mCustomer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

/**
 * <pre>
 *     author : zhangtianwen
 *     e-mail : tianwen.zhang@t2mobile.com
 *     time   : 2021/07/28
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class CustomerService extends Service {

    private static final String TAG = "CustomerService";


    @Override
    public IBinder onBind(Intent intent) {
        return new CustomerSettingsInterface();
    }


    public class CustomerSettingsInterface extends ISettingsInterface.Stub {

        public Bundle getCustomerCarrierConfig() {
            Bundle bundle = new Bundle();
            Log.d(TAG, "Stub is return success getGid1FromSettingsProcess =!!!!");
            try {
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TelephonyManager.class);
                CarrierConfigManager configManager = (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
                SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                String subGid1 = "";
                String gid1 = telephonyManager.getGroupIdLevel1();
                if (gid1.length() > 2) {
                    subGid1 = gid1.substring(0, 4);
                    bundle.putString("gid1", subGid1);
                    Log.d(TAG, "Stub is return success gid = " + gid1);
                }

                int subId;
                int[] subscriptionIdList = subscriptionManager.getActiveSubscriptionIdList();
                if (subscriptionIdList.length > 0) {
                    subId = subscriptionIdList[0];
                    PersistableBundle config = configManager.getConfigForSubId(subId);
                    String[] booksmarks = config.getStringArray("carrier_bookmarks");
                    if (booksmarks != null && booksmarks.length > 0) {
                        bundle.putStringArray("bookmarks", booksmarks);
                    }

                    String homePage = config.getString("carrier_home_page");
                    if (!TextUtils.isEmpty(homePage)) {
                        bundle.putString("home_page", homePage);
                        Log.d(TAG, "Stub is return success homePage = " + homePage);
                    }
                }

            } catch (Exception e) {
                Log.d(TAG, "getGid1FromSettingsProcess error = " + e.getMessage());
            }
            return bundle;
        }

    }
}
