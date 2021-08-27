package com.android.settings.qs;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
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

import com.android.settings.R;
import com.android.settings.network.ims.WifiCallingQueryImsState;

import java.util.List;

public class VoWifiTile extends TileService {

    private static final String TAG = "VoWifiTile";
    private static final String LEGACY_ACTION_CONFIGURE_PHONE_ACCOUNT =
            "android.telecom.action.CONNECTION_SERVICE_CONFIGURE";
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
            getQsTile().setState(Tile.STATE_INACTIVE);// 更改成非活跃状态
            updateWfcMode(false);
            getQsTile().setIcon(icon);//设置图标
            getQsTile().updateTile();//更新Tile
        } else if (state == Tile.STATE_INACTIVE) {
            icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_vowifi_calling);
            getQsTile().setState(Tile.STATE_ACTIVE);//更改成活跃状态
            updateWfcMode(true);
            getQsTile().setIcon(icon);//设置图标
            getQsTile().updateTile();//更新Tile
        }

        if (!"".equals(title)) {
            getQsTile().setLabel(title);
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

        // add by T2M.zhangrenjie for FP4-847 2021-07-22 begin
        boolean ims_enabled = Settings.Global.getInt(getContentResolver(), "ims_enable_settings",0) == 1;
        if (ims_enabled){
            Log.d(TAG, "wfc toggle show because of ims_enabled =" + ims_enabled);
            return true;
        }
        // add by T2M.zhangrenjie for FP4-847 2021-07-22 end
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
        getQsTile().setIcon(icon);//设置图标
        getQsTile().updateTile();//更新Tile
    }
}
