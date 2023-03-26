/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.biometrics.face;

import android.content.Context;
import android.hardware.biometrics.BiometricAuthenticator;
import android.hardware.face.FaceManager;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.Utils;
import com.android.settings.biometrics.ParentalControlsUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Utilities for face details shared between Security Settings and Safety Center.
 */
public class FaceStatusUtils {

    private final int mUserId;
    private final Context mContext;
    private final FaceManager mFaceManager;

    public FaceStatusUtils(Context context, FaceManager faceManager, int userId) {
        mContext = context;
        mFaceManager = faceManager;
        mUserId = userId;
    }

    /**
     * Returns whether the face settings entity should be shown.
     */
    public boolean isAvailable() {
        //add by t2m yingyubin for FP5-186 20230325
        return isFaceUnlockSupported() ||
                (!Utils.isMultipleBiometricsSupported(mContext) && Utils.hasFaceHardware(mContext));
    }

    /**
     * Returns the {@link EnforcedAdmin} if parental consent is required to change face settings.
     *
     * @return null if face settings does not require a parental consent.
     */
    public EnforcedAdmin getDisablingAdmin() {
        return ParentalControlsUtils.parentConsentRequired(
                mContext, BiometricAuthenticator.TYPE_FACE);
    }

    /**
     * Returns the summary of face settings entity.
     */
    public String getSummary() {
        return mContext.getResources().getString(hasEnrolled()
                ? R.string.security_settings_face_preference_summary
                : R.string.security_settings_face_preference_summary_none);
    }

    /**
     * Returns the class name of the Settings page corresponding to face settings.
     */
    public String getSettingsClassName() {
        //add by t2m yingyubin for FP5-186 20230325
        if(isFaceUnlockSupported()){
            return FaceEnrollIntroductionInternal.class.getName();
        }
        //add by t2m yingyubin for FP5-186 20230325
        return hasEnrolled() ? Settings.FaceSettingsInternalActivity.class.getName()
                : FaceEnrollIntroductionInternal.class.getName();
    }

    /**
     * Returns whether at least one face template has been enrolled.
     */
    public boolean hasEnrolled() {
        //add by t2m yingyubin for FP5-186 20230325
        if(isFaceUnlockSupported()){
            boolean hasFaceEnrolled = android.provider.Settings.System.getInt(mContext.getContentResolver(),"enroll_main_face_id", 0) > 0
                    || android.provider.Settings.System.getInt(mContext.getContentResolver(), "enroll_second_face_id", 0) > 0;
            return hasFaceEnrolled;
        }
        //add by t2m yingyubin for FP5-186 20230325
        return mFaceManager.hasEnrolledTemplates(mUserId);
    }

    //add by t2m yingyubin for FP5-186 20230325
    private boolean isFaceUnlockSupported(){
        PackageManager packageManager =  mContext.getPackageManager();
        try{
            packageManager.getPackageInfo("com.fp.faceunlock",PackageManager.GET_ACTIVITIES);
            return true;
        }catch(NameNotFoundException e){
            return false;
        }
    }
    //add by t2m yingyubin for FP5-186 20230325
}
