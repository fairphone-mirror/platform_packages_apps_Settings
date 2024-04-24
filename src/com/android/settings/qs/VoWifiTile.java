package com.android.settings.qs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsMmTelManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SubSettings;
import com.android.settings.network.ims.WifiCallingQueryImsState;
import com.android.settings.wifi.calling.WifiCallingSettings;

import java.util.List;

public class VoWifiTile extends TileService {

    private static final String TAG = "VoWifiTile";
    private static final String LEGACY_ACTION_CONFIGURE_PHONE_ACCOUNT =
            "android.telecom.action.CONNECTION_SERVICE_CONFIGURE";
    private static final String SHARED_PREFERENCES_NAME = "wfc_disclaimer_prefs";
    @VisibleForTesting
    static final String KEY_HAS_AGREED_LOCATION_DISCLAIMER
            = "key_has_agreed_location_disclaimer";

    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private ImsMmTelManager mImsMmTelManager;
    private CarrierConfigManager mCarrierConfigManager;
    private String title = "";

    @Override
    public void onCreate() {
        super.onCreate();
        mCarrierConfigManager = getSystemService(CarrierConfigManager.class);
        getImsMmTelManager();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick() {
        super.onClick();
        if(getImsMmTelManager() == null || !getAvailabilityStatus(mSubId)) return;
        int state = getQsTile().getState();
        Log.d(TAG, "onClick, state = " + state);
        Icon icon;
        if (state == Tile.STATE_ACTIVE) {
            icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_vowifi_calling_disable);
            getQsTile().setState(Tile.STATE_INACTIVE);
            getQsTile().setIcon(icon);
            getQsTile().updateTile();
        } else if (state == Tile.STATE_INACTIVE) {
            icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_vowifi_calling);
            getQsTile().setState(Tile.STATE_ACTIVE);
            getQsTile().setIcon(icon);
            getQsTile().updateTile();
        }

        if (!"".equals(title)) {
            getQsTile().setLabel(title);
        }

        // Launch disclaimer fragment before turning on WFC
       if (showWFCLocationPrivacyPolicyByCarrierConfig(mSubId) && !getBooleanSharedPrefs(KEY_HAS_AGREED_LOCATION_DISCLAIMER, false)) {
            Log.d(TAG, "onClick, start WifiCallingSettings");
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(getApplicationContext(), SubSettings.class);
            intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, WifiCallingSettings.class.getName());
            final Bundle args = new Bundle();
            args.putInt(Settings.EXTRA_SUB_ID, mSubId);
            intent.putExtras(args);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            if (state == Tile.STATE_ACTIVE) {
                updateWfcMode(false);
            } else if (state == Tile.STATE_INACTIVE) {
                updateWfcMode(true);
            }
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "onStartListening");
        updateIcon();
    }


    private ImsMmTelManager getImsMmTelManager() {
        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        int[] subscriptionIdList = subscriptionManager.getActiveSubscriptionIdList();
        if (subscriptionIdList.length > 0) mSubId = subscriptionIdList[0];
        Log.d(TAG, "getImsMmTelManager, mSubId = " + mSubId + ", subscriptionIdList.length = " + subscriptionIdList.length);
        if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
            mImsMmTelManager = null;
            return mImsMmTelManager;
        }

        if (mImsMmTelManager == null) {
            mImsMmTelManager = ImsMmTelManager.createForSubscriptionId(mSubId);
        }
        return mImsMmTelManager;
    }

    private boolean getAvailabilityStatus(int subId) {
        boolean ims_enabled = Settings.Global.getInt(getContentResolver(), "ims_enable_settings",0) == 1;
        if (ims_enabled) {
            Log.d(TAG, "wfc toggle show because of ims_enabled =" + ims_enabled);
            return true;
        }
        return SubscriptionManager.isValidSubscriptionId(subId)
                && isWifiCallingEnabled(this, subId)
                && isWfcEnabledByCarrierConfig(subId);
    }

    private boolean isWifiCallingEnabled(Context context, int subId) {
        final PhoneAccountHandle simCallManager =
                context.getSystemService(TelecomManager.class)
                       .getSimCallManagerForSubscription(subId);
        final int phoneId = SubscriptionManager.getSlotIndex(subId);

        boolean isWifiCallingEnabled;
        if (simCallManager != null) {
            final Intent intent = buildPhoneAccountConfigureIntent(
                    context, simCallManager);

            isWifiCallingEnabled = intent != null;
        } else {
            isWifiCallingEnabled = queryImsState(subId).isReadyToWifiCalling();
        }
        Log.d(TAG, "isWifiCallingEnabled = " + isWifiCallingEnabled);
        return isWifiCallingEnabled;
    }


    private boolean isWfcEnabledByCarrierConfig(int mSubId){
        Log.d(TAG, "update WFC");
        if (mCarrierConfigManager != null) {
            PersistableBundle b = mCarrierConfigManager.getConfigForSubId(mSubId);
            if (b != null) {
                boolean isWFCEnabled = b.getBoolean(CarrierConfigManager.KEY_WFC_TOGGLE_SHOW_BOOL
                        , false);
                title = b.getString(CarrierConfigManager.KEY_WIFI_CALLING_TITLE,"");
                if ("".equals(title)) {
                    title = SubscriptionManager.getResourcesForSubId(getApplicationContext(), mSubId)
                            .getString(R.string.wifi_calling_settings_title);
                }
                Log.d(TAG, "wfc toggle show: " + isWFCEnabled);
                return isWFCEnabled;
            }
        }
        return false;
    }

    WifiCallingQueryImsState queryImsState(int subId) {
        return new WifiCallingQueryImsState(this, subId);
    }

    private void updateWfcMode(boolean wfcEnabled) {
        Log.i(TAG, "updateWfcMode(" + wfcEnabled + ")");
        mImsMmTelManager.setVoWiFiSettingEnabled(wfcEnabled);
    }

    private Intent buildPhoneAccountConfigureIntent(
            Context context, PhoneAccountHandle accountHandle) {
        Intent intent = buildConfigureIntent(
                context, accountHandle, TelecomManager.ACTION_CONFIGURE_PHONE_ACCOUNT);

        if (intent == null) {
            // If the new configuration didn't work, try the old configuration intent.
            intent = buildConfigureIntent(context, accountHandle,
                    LEGACY_ACTION_CONFIGURE_PHONE_ACCOUNT);
        }
        return intent;
    }

    private Intent buildConfigureIntent(
            Context context, PhoneAccountHandle accountHandle, String actionStr) {
        if (accountHandle == null || accountHandle.getComponentName() == null
                || TextUtils.isEmpty(accountHandle.getComponentName().getPackageName())) {
            return null;
        }

        // Build the settings intent.
        Intent intent = new Intent(actionStr);
        intent.setPackage(accountHandle.getComponentName().getPackageName());
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);

        // Check to see that the phone account package can handle the setting intent.
        final PackageManager pm = context.getPackageManager();
        final List<ResolveInfo> resolutions = pm.queryIntentActivities(intent, 0);
        if (resolutions.size() == 0) {
            intent = null;  // set no intent if the package cannot handle it.
        }

        return intent;
    }


    private void updateIcon() {
        Icon icon;
        if(getImsMmTelManager() == null || !getAvailabilityStatus(mSubId)) {
            icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_vowifi_calling_disable);
            getQsTile().setState(Tile.STATE_UNAVAILABLE);
        } else if (getImsMmTelManager().isVoWiFiSettingEnabled()) {
            icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_vowifi_calling);
            getQsTile().setState(Tile.STATE_ACTIVE);
        } else {
            icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_vowifi_calling_disable);
            getQsTile().setState(Tile.STATE_INACTIVE);
        }
        if (!"".equals(title)) {
            getQsTile().setLabel(title);
        }
        getQsTile().setIcon(icon);
        getQsTile().updateTile();
    }

    private boolean showWFCLocationPrivacyPolicyByCarrierConfig(int mSubId) {
        Log.d(TAG, "showWFCLocationPrivacyPolicyByCarrierConfig");
        if (mCarrierConfigManager != null) {
            PersistableBundle b = mCarrierConfigManager.getConfigForSubId(mSubId);
            if (b != null) {
                boolean showWFCLocationPrivacyPolicy = b.getBoolean(CarrierConfigManager.KEY_SHOW_WFC_LOCATION_PRIVACY_POLICY_BOOL);
                boolean isWFCEnabledbyDefault = b.getBoolean(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL);
                Log.d(TAG, "showWFCLocationPrivacyPolicy: " + showWFCLocationPrivacyPolicy + " !isWFCEnabledbyDefault " + !isWFCEnabledbyDefault);
                return showWFCLocationPrivacyPolicy && !isWFCEnabledbyDefault;
            }
        }
        return false;
    }

    /**
     * Gets the boolean value from shared preferences.
     *
     * @param key The key for the preference item.
     * @param defValue Value to return if this preference does not exist.
     * @return The boolean value of corresponding key, or defValue.
     */
    private boolean getBooleanSharedPrefs(String key, boolean defValue) {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        Log.d(TAG, "getBooleanSharedPrefs: " + prefs.getBoolean(key + mSubId, defValue));
        return prefs.getBoolean(key + mSubId, defValue);
    }
}
