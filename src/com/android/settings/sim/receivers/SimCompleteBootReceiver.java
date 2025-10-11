/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.sim.receivers;

import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.settings.sim.SimActivationNotifier;
import com.android.settings.sim.SimNotificationService;

import com.google.common.util.concurrent.MoreExecutors;

/** This class manage all SIM operations after device boot up. */
public class SimCompleteBootReceiver extends BroadcastReceiver {
    private static final String TAG = "SimCompleteBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e(TAG, "Invalid broadcast received.");
            return;
        }

        SharedPreferences sharedPreference = context.getSharedPreferences("default_payment_component", Context.MODE_PRIVATE);
        int isCommit= sharedPreference.getInt("isCommit",0);
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        String defaultApplication = roleManager.getDefaultApplication(RoleManager.ROLE_WALLET);
        boolean  isRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_WALLET);
        if (isCommit == 0 && defaultApplication == null && isRoleAvailable) {
            roleManager.setDefaultApplication(RoleManager.ROLE_WALLET,
                "com.google.android.gms", 0,
                MoreExecutors.directExecutor(), aBoolean -> {
                    Log.i(TAG,"Set default payment component:"+aBoolean);
                    if (aBoolean) {
                        boolean commit = sharedPreference.edit().putInt("isCommit",1).commit();
                        Log.i(TAG,"Submit a one-time flag:"+commit);
                    }
                });
        }

        if (SimActivationNotifier.getShowSimSettingsNotification(context)) {
            SimNotificationService.scheduleSimNotification(
                    context, SimActivationNotifier.NotificationType.NETWORK_CONFIG);
        }
    }
}
