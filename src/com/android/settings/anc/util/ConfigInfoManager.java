package com.android.settings.anc.util;

import android.content.Context;
import com.anc.faceid.bean.AncFaceIdConfig;
import com.anc.faceid.utils.AncConstants;
import com.android.settings.anc.LiteManager;

public class ConfigInfoManager {
    private static ConfigInfoManager sInstance;

    public static ConfigInfoManager getInstance() {
        if (sInstance == null) {
            sInstance = new ConfigInfoManager();
        }
        return sInstance;
    }

    private ConfigInfoManager() {

    }

    public AncFaceIdConfig genCustomConfig(Context context) {
        AncFaceIdConfig config = LiteManager.getInstance().getConfig();
        config.storeDebugImgMode = 0;
        config.eyeOcclusion = true;
        config.eyeStatus = true;
        config.noseOcclusion = true;
        config.mouthOcclusion = true;
        config.light = true;
        config.blurness = true;
        config.compareBlurness = false;
        config.faceIntact = true;
        config.bigCpuCore = AncConstants.ANC_UNLOCK_BIG_CPU_CORE_HIGH;
        config.respirator = true;

        config.yawLeftThreshold = -8;
        config.yawRightThreshold = 8;
        config.pitchTopThreshold = -13;
        config.pitchDownThreshold = 13;

        config.CompareYawLeftThreshold = -45;
        config.CompareYawRightThreshold = 45;
        config.ComparePitchTopThreshold = -45;
        config.ComparePitchDownThreshold = 45;

        return config;
    }
}
