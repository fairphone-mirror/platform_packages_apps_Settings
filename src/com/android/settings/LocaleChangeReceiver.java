package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.provider.Settings.Secure;
import android.database.ContentObserver;
import android.provider.Settings;
import android.util.Log;
import android.os.LocaleList;

import com.android.internal.app.LocalePicker;
import com.android.settings.sim.receivers.SimCompleteBootReceiver;

import java.util.Locale;

import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.SimState;

public class LocaleChangeReceiver extends BroadcastReceiver {

    private String TAG = "LocaleChangeReceiver";
    private boolean DEBUG = true;
    private SubscriptionManager mSubscriptionManager = null;
    private static final String IS_LANGUAGE_CHANGED = "persist.sys.is_language_changed";

    @Override
    public void onReceive(Context context, Intent intent) {
        Locale currentLanguage = Locale.getDefault();
        int isLanguageHasChanged = SystemProperties.getInt(IS_LANGUAGE_CHANGED,0);
        int isUserSetupComplete = Secure.getInt(context.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0);
        if ((!SettingsApplication.isLaunguageChanged && !SimCompleteBootReceiver.isLaunguageChanged) || isLanguageHasChanged == 1 || isUserSetupComplete == 0) {
            if (DEBUG) {
                Log.d(TAG,"LocaleChangeReceiver.java-onReceive-Sim not complete boot-return-isUserSetupComplete:"+isUserSetupComplete);
            }
            return;
        }

        if (mSubscriptionManager == null) {
            mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        }

        if(intent.getAction() != null && intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
            if (mSubscriptionManager != null) {
                int[] activesSubList = mSubscriptionManager.getActiveSubscriptionIdList();
                if (activesSubList.length == 0) {
                    if (DEBUG) {
                        Log.d(TAG,"LocaleChangeReceiver.java-onReceive-activesSubList == 0--currentLanguage:"+currentLanguage);
                    }
                    return;
                } else {
                    if (isUserSetupComplete == 0 && currentLanguage.toString().startsWith("en")) {
                        if (DEBUG) {
                            Log.d(TAG,"LocaleChangeReceiver.java-onReceive-setupwizard not completed~~~currentLanguage:"+currentLanguage+"   not set language");
                        }
                        return;
                    }
                }
                if (DEBUG) {
                    Log.d(TAG,"LocaleChangeReceiver.java-onReceive-activesSubList !=0:"+activesSubList.length+"     currentLanguage:"+currentLanguage +"    isUserSetupComplete:"+isUserSetupComplete);
                }
                for (int i = 0; i < activesSubList.length ; i++ ) {
                    int subId = activesSubList[i];
                    int slotIndex = mSubscriptionManager.getSlotIndex(subId);
                    TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    int simState = TelephonyManager.SIM_STATE_UNKNOWN;
                    String spn = "";
                    if (telephonyManager != null) {
                        simState = telephonyManager.getSimState(slotIndex);
                        if (simState == TelephonyManager.SIM_STATE_READY) {
                            spn = telephonyManager.getSimOperatorNameForPhone(slotIndex);
                        }
                    }
                    if (!"".equals(spn) || !spn.isEmpty()) {
                        Log.i(TAG,"Sim Operator Name is not empty, skip setting Sim Operator Name！");
                        return;
                    }
                    SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
                    String displayName;
                    int displayNameSource;
                    if (subInfo != null) {
                        displayName = subInfo.getDisplayName().toString();
                        displayNameSource = subInfo.getDisplayNameSource();
                        if (DEBUG) {
                            Log.d(TAG,"LocaleChangeReceiver.java-onReceive-activesSubList:"+activesSubList[i]+"    spn:"+spn+"    displayName:"+displayName +"    displayNameSource:"+displayNameSource+"    currentLanguage:"+currentLanguage);
                        }
                        if (SubscriptionManager.NAME_SOURCE_USER_INPUT != displayNameSource && displayName.startsWith("CARD ") && isLanguageHasChanged != 1) {
                            String finalyDisplayName = context.getResources().getString(com.android.internal.R.string.subscription_displayname) + Integer.toString(slotIndex + 1);
                            int result =  mSubscriptionManager.setDisplayName(finalyDisplayName,subId,SubscriptionManager.NAME_SOURCE_CARRIER);
                            if (DEBUG) {
                                Log.d(TAG,"LocaleChangeReceiver.java-onReceive-finalyDisplayName:"+finalyDisplayName+"    subInfo.getDisplayName():"+mSubscriptionManager.getActiveSubscriptionInfo(subId).getDisplayName().toString()+"    isLanguageHasChanged:"+isLanguageHasChanged +"    change language~~~~");
                            }
                            Locale oldLocale = Locale.getDefault();
                            Locale newLocal = new Locale("en_US");
                            LocaleList locales = context.getResources().getConfiguration().getLocales();
                            if ("en_US".equals(oldLocale.toString())) {
                                newLocal = new Locale("zh_CN_#Hans");
                            }
                            SystemProperties.set(IS_LANGUAGE_CHANGED,"1");
                            LocalePicker.updateLocale(newLocal);
                            //LocalePicker.updateLocale(oldLocale);
                            LocalePicker.updateLocales(locales);
                        }
                    }
                }
            }
        }
    }
}
