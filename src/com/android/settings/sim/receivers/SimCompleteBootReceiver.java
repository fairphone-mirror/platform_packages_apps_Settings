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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import com.android.internal.app.LocalePicker;
import com.android.settings.sim.SimActivationNotifier;
import com.android.settings.sim.SimNotificationService;

import java.util.Locale;

/** This class manage all SIM operations after device boot up. */
public class SimCompleteBootReceiver extends BroadcastReceiver {
    private static final String TAG = "SimCompleteBootReceiver";
    private static boolean isLaunguageChanged = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isLaunguageChanged) {
            Configuration configuration = context.getResources().getConfiguration();
            Locale oldLocale = Locale.getDefault();
            Locale newLocal = new Locale("en_US");
            if ("en_US".equals(oldLocale)) {
                newLocal = new Locale("zh_CN");
            }
            LocalePicker.updateLocale(newLocal);
            isLaunguageChanged = true;
            LocalePicker.updateLocale(oldLocale);
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e(TAG, "Invalid broadcast received.");
            return;
        }
        if (SimActivationNotifier.getShowSimSettingsNotification(context)) {
            SimNotificationService.scheduleSimNotification(
                    context, SimActivationNotifier.NotificationType.NETWORK_CONFIG);
        }
    }
}
