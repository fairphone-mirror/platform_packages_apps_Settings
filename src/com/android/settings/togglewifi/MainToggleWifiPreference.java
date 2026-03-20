/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settings.togglewifi;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.RestrictedSwitchPreference;

public class MainToggleWifiPreference extends RestrictedSwitchPreference {
    private final String TAG = "MainToggleWifiPreference";

    public MainToggleWifiPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MainToggleWifiPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MainToggleWifiPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MainToggleWifiPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView textView = (TextView) holder.findViewById(android.R.id.title);
        if (textView != null) {
            RelativeLayout container = (RelativeLayout) textView.getParent();

            int paddingTop = container.getPaddingTop();
            int paddingBottom = container.getPaddingBottom();
            int paddingEnd = container.getPaddingEnd();
            int newPaddingStart = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8f,
                    getContext().getResources().getDisplayMetrics());

            container.setPaddingRelative(newPaddingStart, paddingTop, paddingEnd, paddingBottom);
        } else {
            Log.e(TAG, "MainToggleWifiPreference#onBindViewHolder text=null !");
        }
    }
}
