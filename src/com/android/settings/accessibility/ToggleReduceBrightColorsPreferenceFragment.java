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

package com.android.settings.accessibility;

import static com.android.internal.accessibility.AccessibilityShortcutController.REDUCE_BRIGHT_COLORS_COMPONENT_NAME;
import static com.android.internal.accessibility.AccessibilityShortcutController.REDUCE_BRIGHT_COLORS_TILE_SERVICE_COMPONENT_NAME;
import static com.android.settings.accessibility.AccessibilityStatsLogUtils.logAccessibilityServiceEnabled;

import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.accessibility.AccessibilityUtil.QuickSettingsTooltipType;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SeekBarPreference;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

import android.provider.Settings.Secure;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.hardware.Sensor;
/** Settings for reducing brightness. */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class ToggleReduceBrightColorsPreferenceFragment extends ToggleFeaturePreferenceFragment {

    private static final String TAG = "ToggleReduceBrightColorsPreferenceFragment";
    private static final String ENABLE_REDUCE_BRIGHT_COLORS_KEY =
            Settings.Secure.ENABLE_REDUCE_BRIGHT_COLORS;
    private static final String KEY_INTENSITY = "rbc_intensity";
    private static final String KEY_PERSIST = "rbc_persist";
    private static final String REDUCE_BRIGHT_COLORS_ACTIVATED_KEY =
            Settings.Secure.REDUCE_BRIGHT_COLORS_ACTIVATED;

    private ReduceBrightColorsIntensityPreferenceController mRbcIntensityPreferenceController;
    private ReduceBrightColorsPersistencePreferenceController mRbcPersistencePreferenceController;
    private ColorDisplayManager mColorDisplayManager;

    private SensorManager mSensorManager;
    private final float CAN_ENTRY_EXTRA_DIM_VALUE = 80;
    private int mSmallLuxCounter = 0;
    private int mLageLuxCounter = 0 ;
    private final String ACCESSIBILITY_BUTTON_TARGETS_STRING = "com.android.server.accessibility/ReduceBrightColors";

    @Override
    protected void registerKeysToObserverCallback(
            AccessibilitySettingsContentObserver contentObserver) {
        super.registerKeysToObserverCallback(contentObserver);

        final List<String> enableServiceFeatureKeys = new ArrayList<>(/* initialCapacity= */ 1);
        enableServiceFeatureKeys.add(REDUCE_BRIGHT_COLORS_ACTIVATED_KEY);
        enableServiceFeatureKeys.add(ENABLE_REDUCE_BRIGHT_COLORS_KEY);
        contentObserver.registerKeysToObserverCallback(enableServiceFeatureKeys,
                key -> updateSwitchBarToggleSwitch());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mImageUri = new Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getPrefContext().getPackageName())
                .appendPath(String.valueOf(R.raw.a11y_extra_dim_banner))
                .build();
        mComponentName = REDUCE_BRIGHT_COLORS_COMPONENT_NAME;
        mPackageName = getText(R.string.reduce_bright_colors_preference_title);
        mHtmlDescription = getText(R.string.reduce_bright_colors_preference_subtitle);
        mTopIntroTitle = getText(R.string.reduce_bright_colors_preference_intro_text);
        mRbcIntensityPreferenceController =
                new ReduceBrightColorsIntensityPreferenceController(getContext(), KEY_INTENSITY);
        mRbcPersistencePreferenceController =
                new ReduceBrightColorsPersistencePreferenceController(getContext(), KEY_PERSIST);
        mRbcIntensityPreferenceController.displayPreference(getPreferenceScreen());
        mRbcPersistencePreferenceController.displayPreference(getPreferenceScreen());
        mColorDisplayManager = getContext().getSystemService(ColorDisplayManager.class);
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        // Parent sets the title when creating the view, so set it after calling super
        mToggleServiceSwitchPreference.setTitle(R.string.reduce_bright_colors_switch_title);
        updateGeneralCategoryOrder();
        updateFooterPreference();
        return view;
    }

    private final SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final float lux = event.values[0];
            if(lux <= CAN_ENTRY_EXTRA_DIM_VALUE){
                mSmallLuxCounter++;
                mLageLuxCounter = 0;
            }else {
                mSmallLuxCounter = 0;
                mLageLuxCounter++;
            }
            if(mLageLuxCounter == 10){
                mColorDisplayManager.setReduceBrightColorsActivated(false);
                Secure.putInt(getContext().getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,0);
                Secure.putString(getContext().getContentResolver(),Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,"");
                Secure.putString(getContext().getContentResolver(),Secure.ACCESSIBILITY_BUTTON_TARGETS,getButtonTargetsString());
            }
            if(mSmallLuxCounter == 10){
                Secure.putInt(getContext().getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,1);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used.
        }
    };

    private String getButtonTargetsString() {
        String mCurrentAccessibilityButtonTargets = Settings.Secure.getString(getContext().getContentResolver(),Secure.ACCESSIBILITY_BUTTON_TARGETS);
        String[] strings = mCurrentAccessibilityButtonTargets.split(":");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (!ACCESSIBILITY_BUTTON_TARGETS_STRING.equals(strings[i])){
                if (i == 0) {
                    result.append(strings[i]);
                } else {
                    result.append(":");
                    result.append(strings[i]);
                }
            }
        }
        return result.toString();
    }

    private void updateGeneralCategoryOrder() {
        final PreferenceCategory generalCategory = findPreference(KEY_GENERAL_CATEGORY);
        final SeekBarPreference intensity = findPreference(KEY_INTENSITY);
        getPreferenceScreen().removePreference(intensity);
        intensity.setOrder(mShortcutPreference.getOrder() - 2);
        generalCategory.addPreference(intensity);
        final SwitchPreference persist = findPreference(KEY_PERSIST);
        getPreferenceScreen().removePreference(persist);
        persist.setOrder(mShortcutPreference.getOrder() - 1);
        generalCategory.addPreference(persist);
    }

    private void updateFooterPreference() {
        final String title = getPrefContext().getString(R.string.reduce_bright_colors_about_title);
        mFooterPreferenceController.setIntroductionTitle(title);
        mFooterPreferenceController.displayPreference(getPreferenceScreen());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSwitchBarToggleSwitch();
        mSensorManager.registerListener(mLightSensorListener,mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT_BACK),
                  SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(mLightSensorListener);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.REDUCE_BRIGHT_COLORS_SETTINGS;
    }

    @Override
    public int getHelpResource() {
        // TODO(b/170973645): Link to help support page
        return 0;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.reduce_bright_colors_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected void onPreferenceToggled(String preferenceKey, boolean enabled) {
        if (enabled) {
            showQuickSettingsTooltipIfNeeded(QuickSettingsTooltipType.GUIDE_TO_DIRECT_USE);
        }
        logAccessibilityServiceEnabled(mComponentName, enabled);
        mColorDisplayManager.setReduceBrightColorsActivated(enabled);
    }

    @Override
    protected void onRemoveSwitchPreferenceToggleSwitch() {
        super.onRemoveSwitchPreferenceToggleSwitch();
        mToggleServiceSwitchPreference.setOnPreferenceClickListener(
                /* onPreferenceClickListener= */ null);
    }

    @Override
    protected void updateToggleServiceTitle(SettingsMainSwitchPreference switchPreference) {
        switchPreference.setTitle(R.string.reduce_bright_colors_preference_title);
    }

    @Override
    protected CharSequence getShortcutTitle() {
        return getText(R.string.reduce_bright_colors_shortcut_title);
    }

    @Override
    int getUserShortcutTypes() {
        return AccessibilityUtil.getUserShortcutTypesFromSettings(getPrefContext(),
                mComponentName);
    }

    @Override
    ComponentName getTileComponentName() {
        return REDUCE_BRIGHT_COLORS_TILE_SERVICE_COMPONENT_NAME;
    }

    @Override
    CharSequence getTileTooltipContent(@QuickSettingsTooltipType int type) {
        return getText(type == QuickSettingsTooltipType.GUIDE_TO_EDIT
                ? R.string.accessibility_reduce_bright_colors_qs_tooltip_content
                : R.string.accessibility_reduce_bright_colors_auto_added_qs_tooltip_content);
    }

    @Override
    protected void updateSwitchBarToggleSwitch() {
        final boolean checked = mColorDisplayManager.isReduceBrightColorsActivated();
        mRbcIntensityPreferenceController.updateState(getPreferenceScreen()
                .findPreference(KEY_INTENSITY));
        mRbcPersistencePreferenceController.updateState(getPreferenceScreen()
                .findPreference(KEY_PERSIST));
        if (mToggleServiceSwitchPreference.isChecked() != checked) {
            mToggleServiceSwitchPreference.setChecked(checked);
        }
        boolean isEnableExtraDim = Secure.getInt(getContext().getContentResolver(),Secure.ENABLE_REDUCE_BRIGHT_COLORS,0) == 1;
        if(isEnableExtraDim){
            mToggleServiceSwitchPreference.setSwitchBarEnabled(true);
            mShortcutPreference.setEnabled(true);
        } else {
            mToggleServiceSwitchPreference.setSwitchBarEnabled(false);
            mShortcutPreference.setEnabled(false);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.reduce_bright_colors_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return ColorDisplayManager.isReduceBrightColorsAvailable(context);
                }
            };
}
