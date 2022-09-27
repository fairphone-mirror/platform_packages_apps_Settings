/*
 * Copyright (c) 2013 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

public class SSIDPreloadReceiver extends BroadcastReceiver {
    private static final String TAG = "SSIDPreloadReceiver";
    private static final String NETWORK_FACTORY_RESET_ACTION = "com.android.settings.wifi.NETWORK_FACTORY_RESET_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if ("android.intent.action.BOOT_COMPLETED".equals(action) || NETWORK_FACTORY_RESET_ACTION.equals(action)) {
            startService(context);
        }
    }

    private void startService(Context context) {
        Intent intent = new Intent("com.android.settings.wifi.SSIDPreloadService");
        intent.setClassName(context.getPackageName(), SSIDPreloadService.class.getName());
        context.startServiceAsUser(intent, UserHandle.CURRENT);
    }
}
