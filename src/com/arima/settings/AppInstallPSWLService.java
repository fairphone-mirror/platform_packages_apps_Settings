/*
 * Copyright (C) 2020-2022 Fairphone B.V.
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

package com.arima.settings;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import com.android.settingslib.fuelgauge.PowerWhitelistBackend;

// SmitteStop package name need to be added into Power Save White List (PSWL).
public class AppInstallPSWLService extends Service {
    private static final String NETCOMPANY_SMITTESTOP_PACKAGE_NAME =
            "com.netcompany.smittestop_exposure_notification";
    private PowerWhitelistBackend mBackend;
    private static Context mContext;
    private AppPSWLBroadcastReceiver mAppPSWLBroadcastReceiver = null;

    private class AppPSWLBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() != null) {
                String packageName = intent.getData().getSchemeSpecificPart();
                if (packageName.equals(NETCOMPANY_SMITTESTOP_PACKAGE_NAME)) {
                    switch (intent.getAction()) {
                        case Intent.ACTION_PACKAGE_ADDED:
                            addAppPSWL(packageName);
                            break;
                        case Intent.ACTION_PACKAGE_REMOVED:
                            removeAppPSWL(packageName);
                            break;
                        default:
                            return;
                    }
                }
            }
        }
    }

    public static void Start(Context context) {
        mContext = context;

        Intent intent = new Intent("arima.settings.AppInstallPSWLService");
        intent.setPackage(context.getPackageName());
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;

        mAppPSWLBroadcastReceiver = new AppPSWLBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(mAppPSWLBroadcastReceiver, filter);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAppPSWLBroadcastReceiver != null) {
            unregisterReceiver(mAppPSWLBroadcastReceiver);
        }
    }

    private void addAppPSWL(String packageName) {
        mBackend = PowerWhitelistBackend.getInstance(mContext);
        if (!mBackend.isWhitelisted(packageName)) {
            mBackend.addApp(packageName);
        }
    }

    private void removeAppPSWL(String packageName) {
        mBackend = PowerWhitelistBackend.getInstance(mContext);
        if (mBackend.isWhitelisted(packageName)) {
            mBackend.removeApp(packageName);
        }
    }
}
