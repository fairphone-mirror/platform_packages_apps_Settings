package com.android.settings.t2mCustomer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
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
            try {
                SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                CarrierConfigManager carrierConfigManager = (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
                Log.d(TAG, "getActiveSubscriptionIdList " + subscriptionManager.getActiveSubscriptionIdList()[0]);

                int subIds[] = subscriptionManager.getActiveSubscriptionIdList();
                if (subIds.length > 0) {
                    PersistableBundle config = carrierConfigManager.getConfigForSubId(subIds[0]);
                    bundle.putAll(config);
                } else {
                    PersistableBundle config = carrierConfigManager.getConfigForSubId(0);
                    bundle.putAll(config);
                }
                for (String s : bundle.keySet()) {
                    Log.d(TAG, "getCustomerCarrierConfig <" + s + ">" + " " + bundle.get(s));
                }


            } catch (Exception e) {
                Log.d(TAG, "getCustomerCarrierConfig error = " + e.getMessage());
            }
            return bundle;
        }
    }
}
