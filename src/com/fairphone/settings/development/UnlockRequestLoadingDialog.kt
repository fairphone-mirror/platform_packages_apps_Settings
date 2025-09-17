/*
 * Copyright (C) 2025 Fairphone B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.fairphone.settings.development

import android.app.Dialog
import android.app.settings.SettingsEnums
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.android.settings.R
import com.android.settings.core.instrumentation.InstrumentedDialogFragment

class UnlockRequestLoadingDialog(
    val onDismissListener: () -> Void?,
) : InstrumentedDialogFragment() {
    override fun getMetricsCategory(): Int = SettingsEnums.DIALOG_ENABLE_OEM_UNLOCKING

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity!!)
            .setTitle(com.android.settingslib.R.string.oem_unlock_enable)
            .setMessage(R.string.unlock_request_loading)
            .setOnDismissListener { onDismissListener() }
            .create()
    }

    companion object {
        const val TAG = "UnlockRequestLoadingDialog"

        fun show(host: Fragment?, onDismissListener: () -> Void?) {
            val manager = host?.childFragmentManager ?: return

            if (manager.findFragmentByTag(TAG) == null) {
                val dialog = UnlockRequestLoadingDialog(onDismissListener)
                dialog.show(manager, TAG)
            }
        }
    }
}
