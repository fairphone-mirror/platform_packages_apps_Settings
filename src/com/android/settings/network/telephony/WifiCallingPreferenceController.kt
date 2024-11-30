/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.network.telephony

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.ims.ImsMmTelManager
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.ims.ImsConfig
import com.android.settings.R
import com.android.settings.core.BasePreferenceController
import com.android.settings.network.telephony.MobileNetworkSettingsSearchIndex.MobileNetworkSettingsSearchResult
import com.android.settings.network.telephony.MobileNetworkSettingsSearchIndex.MobileNetworkSettingsSearchItem
import com.android.settings.network.telephony.wificalling.WifiCallingRepository
import com.android.settings.utils.CarrierParamsUtil
import com.android.settingslib.core.lifecycle.LifecycleObserver
import com.android.settingslib.core.lifecycle.events.OnStart
import com.android.settingslib.core.lifecycle.events.OnStop
import com.android.settingslib.spa.framework.util.collectLatestWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Preference controller for "Wifi Calling".
 *
 * TODO: Remove the class once Provider Model is always enabled in the future.
 */
open class WifiCallingPreferenceController @JvmOverloads constructor(
    context: Context,
    key: String,
    private val callStateRepository: CallStateRepository = CallStateRepository(context),
    private val wifiCallingRepositoryFactory: (subId: Int) -> WifiCallingRepository = { subId ->
        WifiCallingRepository(context, subId)
    },
) : BasePreferenceController(context, key), LifecycleObserver, OnStart, OnStop {

    companion object {
        private const val TAG = "WifiCallingPreference"
        private val WFC_URI: Uri = Uri.parse("content://telephony/siminfo")
    }

    private var subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private lateinit var preference: Preference
    private lateinit var callingPreferenceCategoryController: CallingPreferenceCategoryController

    var mCarrierConfigManager: CarrierConfigManager? = null
    private val context: Context = context
    private val mContentResolver: ContentResolver = context.contentResolver
    private var mWfcObserver: ContentObserver? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val resourcesForSub by lazy {
        SubscriptionManager.getResourcesForSubId(mContext, subId)
    }

    fun init(
            subId: Int,
            callingPreferenceCategoryController: CallingPreferenceCategoryController,
    ): WifiCallingPreferenceController {
        this.subId = subId
        this.callingPreferenceCategoryController = callingPreferenceCategoryController
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager::class.java)
        return this
    }

    /**
     * Note: Visibility also controlled by [onViewCreated].
     */
    override fun getAvailabilityStatus(): Int {
        // add by T2M.renjiezhang for FPS-278 2024-11-15 begin
        Log.d(TAG, "getAvailabilityStatus $subId")
        val imsEnabled = Settings.Global.getInt(context.contentResolver, "ims_enable_settings", 0) == 1
        if (imsEnabled) {
            Log.d(TAG, "wfc toggle show because of ims_enabled = $imsEnabled")
            return AVAILABLE
        }
        return if (SubscriptionManager.isValidSubscriptionId(subId) && isWfcEnabledByCarrierConfig(subId)
        ) AVAILABLE else UNSUPPORTED_ON_DEVICE
        // add by T2M.renjiezhang for FPS-278 2024-11-15 end
    }

    // add by T2M.renjiezhang for FPS-278 2024-11-15 begin
    override fun onStart() {
        if (mWfcObserver == null) {
            mWfcObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    coroutineScope.launch {
                        Log.d(TAG, "onChange update")
                        update()
                    }
                }
            }
        }
        mContentResolver.registerContentObserver(WFC_URI, false, mWfcObserver!!)
    }

    override fun onStop() {
        mContentResolver.unregisterContentObserver(mWfcObserver!!)
    }

    // add by T2M.renjiezhang for FPS-278 2024-11-15 end

    override fun displayPreference(screen: PreferenceScreen) {
        // Not call super here, to avoid preference.isVisible changed unexpectedly
        preference = screen.findPreference(preferenceKey)!!
        preference.intent?.putExtra(Settings.EXTRA_SUB_ID, subId)
    }

    override fun onViewCreated(viewLifecycleOwner: LifecycleOwner) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            // Sub id could invalid, if this page is opened from external action and no sim is
            // active.
            // Ignore this case, since this page will be finished soon.
            return
        }
        // add by T2M.renjiezhang for FPS-278 2024-11-15 begin
        wifiCallingRepositoryFactory(subId).wifiCallingReadyFlow()
            .collectLatestWithLifecycle(viewLifecycleOwner) { isReady ->
                Log.d(TAG, "wifiCallingReady = $isReady")
                val isEnabled = isReady &&  getAvailabilityStatus() == AVAILABLE
                preference.isVisible = isEnabled
                callingPreferenceCategoryController.updateChildVisible(preferenceKey, isEnabled)
                if (isEnabled) update()
            }
        // add by T2M.renjiezhang for FPS-278 2024-11-15 end
        callStateRepository.callStateFlow(subId).collectLatestWithLifecycle(viewLifecycleOwner) {
            preference.isEnabled = (it == TelephonyManager.CALL_STATE_IDLE)
        }
    }

    // add by T2M.renjiezhang for FPS-278 2024-11-15 begin
    private fun isWfcEnabledByCarrierConfig(subId: Int): Boolean {
        Log.d(TAG, "isWfcEnabledByCarrierConfig")
        val b = mCarrierConfigManager?.getConfigForSubId(subId)
        val isWFCEnabled = b?.getBoolean(CarrierConfigManager.KEY_WFC_TOGGLE_SHOW_BOOL, false) ?: false
        Log.d(TAG, "wfc toggle show: $isWFCEnabled")
        return isWFCEnabled
    }
    // add by T2M.renjiezhang for FPS-278 2024-11-15 end

    private suspend fun update() {
        val simCallManager = mContext.getSystemService(TelecomManager::class.java)
                ?.getSimCallManagerForSubscription(subId)
        Log.d(TAG, "update WFC")
        var title = SubscriptionManager.getResourcesForSubId(context, subId)
            .getString(R.string.wifi_calling_settings_title)
        val carrierParams = CarrierParamsUtil.loadInstance(context).getCarrierParams(subId)
        carrierParams?.getString(CarrierConfigManager.KEY_WIFI_CALLING_TITLE, "")?.let {
            if (it.isNotEmpty()) {
                Log.d(TAG, "get title from carrierParams")
                title = it
            }
        }
        mCarrierConfigManager?.getConfigForSubId(subId)?.getString(CarrierConfigManager.KEY_WIFI_CALLING_TITLE, "")?.let {
            if (it.isNotEmpty()) {
                title = it
            }
        }

        if (simCallManager != null) {
            val intent = withContext(Dispatchers.Default) {
                MobileNetworkUtils.buildPhoneAccountConfigureIntent(mContext, simCallManager)
            } ?: return // Do nothing in this case since preference is invisible
            /*val title = withContext(Dispatchers.Default) {
                mContext.packageManager.resolveActivity(intent, 0)
                        ?.loadLabel(mContext.packageManager)
            } ?: return*/
            preference.intent = intent
            preference.title = title
            preference.summary = null
        } else {
            preference.title = title
            preference.summary = withContext(Dispatchers.Default) { getSummaryForWfcMode(subId) }
        }
    }

    // add by T2M.renjiezhang for FPS-278 2024-11-15 begin
    private fun getSummaryForWfcMode(): String {
        var resId = com.android.internal.R.string.wifi_calling_off_summary
        var showSummary = true
            mCarrierConfigManager?.getConfigForSubId(subId)?.let { carrierConfig ->
                showSummary = carrierConfig.getBoolean("show_wifi_calling_summary_bool", true)
            }
            val wfcMode = wifiCallingRepositoryFactory(subId).getWiFiCallingMode()

            Log.i(TAG, "getWfcModeSummary: wfcMode = $wfcMode")
            when (wfcMode) {
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY -> resId = com.android.internal.R.string.wfc_mode_wifi_only_summary
                ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED -> resId = com.android.internal.R.string.wfc_mode_cellular_preferred_summary
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED -> resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary
                ImsConfig.WfcModeFeatureValueConstants.IMS_PREFERRED -> resId = com.android.internal.R.string.wfc_mode_ims_preferred_summary
            }
        return if (showSummary) {
            resourcesForSub.getString(resId)
        } else {
            ""
        }
    }
    // add by T2M.renjiezhang for FPS-278 2024-11-15 end

    companion object {
        class WifiCallingSearchItem(
            private val context: Context,
        ) : MobileNetworkSettingsSearchItem {
            private fun isAvailable(subId: Int): Boolean = runBlocking {
                WifiCallingRepository(context, subId).wifiCallingReadyFlow().first()
            }

            override fun getSearchResult(subId: Int): MobileNetworkSettingsSearchResult? {
                if (!isAvailable(subId)) return null
                return MobileNetworkSettingsSearchResult(
                    key = "wifi_calling",
                    title = context.getString(R.string.wifi_calling_settings_title),
                )
            }
        }
    }
}
