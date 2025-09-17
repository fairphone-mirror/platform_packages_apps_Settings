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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.setFragmentResult
import com.android.settings.core.instrumentation.InstrumentedDialogFragment
import com.android.settings.R

class UnlockRequestErrorDialog(private val errorMessage: String) : InstrumentedDialogFragment() {
    override fun getMetricsCategory(): Int = SettingsEnums.DIALOG_ENABLE_OEM_UNLOCKING

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity!!)
            .setTitle(getString(R.string.wifi_error))
            .setMessage(errorMessage)
            .setPositiveButton(getString(R.string.retry)) { _, _ -> onRetry() }
            .setNegativeButton(getString(R.string.dlg_cancel)) { _, _ -> onCancel() }
            .setOnDismissListener { onCancel() }
            .create()
    }

    private fun onRetry() {
        setFragmentResult(REQUEST_KEY, bundleOf(PARAM_RETRY to true))
    }

    private fun onCancel() {
        setFragmentResult(REQUEST_KEY, bundleOf(PARAM_RETRY to false))
    }

    companion object {
        const val TAG = "UnlockRequestErrorDialog"
        const val REQUEST_KEY = "UNLOCK_REQUEST_ERROR"
        const val PARAM_RETRY = "PARAM_RETRY"

        fun show(host: Fragment, errorMessage: String, listener: FragmentResultListener) {
            val manager = host.activity?.supportFragmentManager ?: return
            if (manager.findFragmentByTag(TAG) == null) {
                host.activity!!.runOnUiThread {
                    manager.setFragmentResultListener(
                        REQUEST_KEY,
                        host.activity!!,
                        listener
                    )
                    val dialog = UnlockRequestErrorDialog(errorMessage)
                    dialog.show(manager, TAG)
                }
            }
        }
    }
}
