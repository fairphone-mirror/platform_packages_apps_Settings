package com.android.settings.network.telephony

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.telephony.ims.ImsMmTelManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.ims.ImsConfig
import com.android.settings.R
import com.android.settings.network.ims.WifiCallingQueryImsState
import com.android.settingslib.core.lifecycle.LifecycleObserver
import com.android.settingslib.core.lifecycle.events.OnStart
import com.android.settingslib.core.lifecycle.events.OnStop
import com.android.settings.utils.CarrierParamsUtil

class WifiCallingPreferenceController(
    private val context: Context,
    key: String
) : TelephonyBasePreferenceController(context, key), LifecycleObserver, OnStart, OnStop {

    companion object {
        private const val TAG = "WifiCallingPreference"
        private val WFC_URI: Uri = Uri.parse("content://telephony/siminfo")
    }

    @VisibleForTesting
    var mCallState: Int? = null
    @VisibleForTesting
    var mCarrierConfigManager: CarrierConfigManager? = null
    private var mImsMmTelManager: ImsMmTelManager? = null
    @VisibleForTesting
    var mSimCallManager: PhoneAccountHandle? = null
    private var mTelephonyCallback: PhoneTelephonyCallback? = null
    private var mPreference: Preference? = null
    private val mContentResolver: ContentResolver = context.contentResolver
    private var mWfcObserver: ContentObserver? = null

    init {
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager::class.java)
        mTelephonyCallback = PhoneTelephonyCallback()
    }

    override fun getAvailabilityStatus(subId: Int): Int {
        Log.d(TAG, "getAvailabilityStatus $subId")
        val imsEnabled = Settings.Global.getInt(context.contentResolver, "ims_enable_settings", 0) == 1
        if (imsEnabled) {
            Log.d(TAG, "wfc toggle show because of ims_enabled = $imsEnabled")
            return AVAILABLE
        }
        return if (SubscriptionManager.isValidSubscriptionId(subId) &&
            MobileNetworkUtils.isWifiCallingEnabled(context, subId, null) &&
            isWfcEnabledByCarrierConfig(subId)
        ) AVAILABLE else UNSUPPORTED_ON_DEVICE
    }

    override fun onStart() {
        mTelephonyCallback?.register(context, mSubId)
        if (mWfcObserver == null) {
            mWfcObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    updateState(mPreference)
                }
            }
        }
        mContentResolver.registerContentObserver(WFC_URI, false, mWfcObserver!!)
    }

    override fun onStop() {
        mTelephonyCallback?.unregister()
        mContentResolver.unregisterContentObserver(mWfcObserver!!)
    }

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        mPreference = screen.findPreference(getPreferenceKey())
        mPreference?.intent?.putExtra(Settings.EXTRA_SUB_ID, mSubId)
    }

    private fun isWfcEnabledByCarrierConfig(mSubId: Int): Boolean {
        Log.d(TAG, "isWfcEnabledByCarrierConfig")
        val b = mCarrierConfigManager?.getConfigForSubId(mSubId)
        val isWFCEnabled = b?.getBoolean(CarrierConfigManager.KEY_WFC_TOGGLE_SHOW_BOOL, false) ?: false
        Log.d(TAG, "wfc toggle show: $isWFCEnabled")
        return isWFCEnabled
    }

    override fun updateState(preference: Preference?) {
        super.updateState(preference)
        if (mCallState == null || preference == null) {
            Log.d(TAG, "Skip update under mCallState=$mCallState")
            return
        }

        Log.d(TAG, "update WFC")
        var title = SubscriptionManager.getResourcesForSubId(context, mSubId)
            .getString(R.string.wifi_calling_settings_title)
        val carrierParams = CarrierParamsUtil.loadInstance(context).getCarrierParams(mSubId)
        carrierParams?.getString(CarrierConfigManager.KEY_WIFI_CALLING_TITLE, "")?.let {
            if (it.isNotEmpty()) {
                Log.d(TAG, "get title from carrierParams")
                title = it
            }
        }
        mCarrierConfigManager?.getConfigForSubId(mSubId)?.getString(CarrierConfigManager.KEY_WIFI_CALLING_TITLE, "")?.let {
            if (it.isNotEmpty()) {
                title = it
            }
        }

        var summaryText: CharSequence? = null
        if (mSimCallManager != null) {
            val intent = MobileNetworkUtils.buildPhoneAccountConfigureIntent(context, mSimCallManager)
            if (intent == null) {
                return
            }
            val pm = context.packageManager
            val resolutions = pm.queryIntentActivities(intent, 0)
            preference.title = title
            preference.intent = intent
        } else {
            preference.title = title
            summaryText = getResourceIdForWfcMode(mSubId)
        }
        preference.summary = summaryText
        preference.isEnabled = mCallState == TelephonyManager.CALL_STATE_IDLE
    }

    private fun getResourceIdForWfcMode(subId: Int): CharSequence {
        var resId = com.android.internal.R.string.wifi_calling_off_summary
        var showSummary = true
        if (queryImsState(subId).isEnabledByUser()) {
            var useWfcHomeModeForRoaming = false
            mCarrierConfigManager?.getConfigForSubId(subId)?.let { carrierConfig ->
                useWfcHomeModeForRoaming = carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_USE_WFC_HOME_NETWORK_MODE_IN_ROAMING_NETWORK_BOOL
                )
                showSummary = carrierConfig.getBoolean("show_wifi_calling_summary_bool", true)
            }
            val isRoaming = getTelephonyManager(context, subId).isNetworkRoaming
            val wfcMode = if (isRoaming && !useWfcHomeModeForRoaming)
                mImsMmTelManager?.voWiFiRoamingModeSetting
            else
                mImsMmTelManager?.voWiFiModeSetting

            Log.i(TAG, "getWfcModeSummary: wfcMode = $wfcMode")
            when (wfcMode) {
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY -> resId = com.android.internal.R.string.wfc_mode_wifi_only_summary
                ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED -> resId = com.android.internal.R.string.wfc_mode_cellular_preferred_summary
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED -> resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary
                ImsConfig.WfcModeFeatureValueConstants.IMS_PREFERRED -> resId = com.android.internal.R.string.wfc_mode_ims_preferred_summary
            }
        }
        return if (showSummary) {
            SubscriptionManager.getResourcesForSubId(context, subId).getText(resId)
        } else {
            ""
        }
    }
    fun init(
        subId: Int,
        callingPreferenceCategoryController: CallingPreferenceCategoryController,
    ): WifiCallingPreferenceController {
        mSubId = subId
        mImsMmTelManager = getImsMmTelManager(mSubId)
        mSimCallManager = context.getSystemService(TelecomManager::class.java)
            ?.getSimCallManagerForSubscription(mSubId)
        return this
    }

    @VisibleForTesting
    fun queryImsState(subId: Int): WifiCallingQueryImsState {
        return WifiCallingQueryImsState(context, subId)
    }

    protected fun getImsMmTelManager(subId: Int): ImsMmTelManager? {
        return if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            null
        } else {
            ImsMmTelManager.createForSubscriptionId(subId)
        }
    }

    @VisibleForTesting
    fun getTelephonyManager(context: Context, subId: Int): TelephonyManager {
        val telephonyMgr = context.getSystemService(TelephonyManager::class.java)
        return if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            telephonyMgr ?: throw IllegalStateException("TelephonyManager is null")
        } else {
            telephonyMgr?.createForSubscriptionId(subId) ?: telephonyMgr
            ?: throw IllegalStateException("TelephonyManager is null")
        }
    }

    private inner class PhoneTelephonyCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {

        private var mTelephonyManager: TelephonyManager? = null

        override fun onCallStateChanged(state: Int) {
            mCallState = state
            updateState(mPreference)
        }

        fun register(context: Context, subId: Int) {
            mTelephonyManager = getTelephonyManager(context, subId)
            mCallState = mTelephonyManager?.getCallState(subId)
            mTelephonyManager?.registerTelephonyCallback(context.mainExecutor, this)
        }

        fun unregister() {
            mCallState = null
            mTelephonyManager?.unregisterTelephonyCallback(this)
        }
    }

    private fun isWifiCallingEnabled(context: Context, subId: Int): Boolean {
        Log.d(TAG, "isWifiCallingEnabled $subId")
        val simCallManager = context.getSystemService(TelecomManager::class.java)
            ?.getSimCallManagerForSubscription(subId)
        val isWifiCallingEnabled = if (simCallManager != null) {
            val intent = MobileNetworkUtils.buildPhoneAccountConfigureIntent(context, simCallManager)
            intent != null
        } else {
            queryImsState(subId).isReadyToWifiCalling()
        }
        Log.d(TAG, "isWifiCallingEnabled: $isWifiCallingEnabled")
        return isWifiCallingEnabled
    }

}
