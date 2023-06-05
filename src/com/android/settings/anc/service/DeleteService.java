package com.android.settings.anc.service;

import android.app.IntentService;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.anc.faceid.bean.AncFaceIdStatus;
import com.android.settings.anc.LiteManager;
import com.android.settings.anc.util.Constants;

import java.io.File;


public class DeleteService extends IntentService {
    private final static String TAG = "DeleteService";

    public DeleteService() {
        super("DeleteService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG,"onHandleIntent");
        if (intent != null) {
            final int mainId = Settings.System.getInt(getContentResolver(), "enroll_main_face_id", 0);
            final int secondId = Settings.System.getInt(getContentResolver(), "enroll_second_face_id", 0);
            if (mainId > 0 || secondId > 0) {
                LiteManager.getInstance().initLite(getApplicationContext(), new LiteManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (mainId > 0) {
                            boolean success1 = deleteFeatureFace(mainId);
                            if (success1) {
                                Settings.System.putInt(getContentResolver(), "enroll_main_face_id", 0);
                            }
                        }
                        if (secondId > 0) {
                            boolean success2 = deleteFeatureFace(secondId);
                            if (success2) {
                                Settings.System.putInt(getContentResolver(), "enroll_second_face_id", 0);
                            }
                        }
                    }

                    @Override
                    public void onFailed(int resultCode, Object object) {

                    }

                    @Override
                    public void onError(String errorMsg) {

                    }
                });
            }
        }
    }

    private boolean deleteFeatureFace(int faceId) {
        Log.d(TAG, "deleteFeatureFace()...");
        int ret = LiteManager.getInstance().deleteFeature(faceId);
        Log.d(TAG, "ret:" + ret);
        boolean success = false;
        if (ret == AncFaceIdStatus.ANC_UNLOCK_OK.toInt()) {
            String fileName = String.format("%s%s", Constants.UNLOCK_FACE_FEATURE_NAME, faceId) + ".png";
            success = deleteImageFile(fileName);
        }
        return success;
    }

    private boolean deleteImageFile(String name) {
        boolean success = false;
        File imageDir = getExternalFilesDir(String.format("%s/%s",
                Constants.UNLOCK_FACE_FOLDER_PATH, Constants.UNLOCK_FACE_FEATURE_PATH));
        if (!imageDir.exists()) {
            Log.e(TAG, "deleteImageFile()...Invalid Folder.");
            return false;
        }
        try {
            File faceFile = new File(imageDir, name);
            if (faceFile.exists()) {
                success = faceFile.delete();
            }
            Log.d(TAG, "delete:" + faceFile.getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return success;
    }
}