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

package com.android.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

//Inherit ScrollView, do not intercept multi-touch operations.
public class NonInterceptingScrollView extends ScrollView {
    public NonInterceptingScrollView(Context context) {
        super(context);
    }

    public NonInterceptingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NonInterceptingScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //If it is a multi-touch operation (zooming), then do not intercept the event.
        if (ev.getPointerCount() > 1) {
            return false;
        }
        //Single finger operation (scrolling) is handled by ScrollView.
        return super.onInterceptTouchEvent(ev);
    }
}
