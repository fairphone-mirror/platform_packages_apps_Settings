package com.android.settings.anc.unlock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.provider.Settings;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.anc.faceid.bean.AncFaceIdConfig;
import com.anc.faceid.bean.AncFaceIdUnlockInfo;
import com.anc.faceid.bean.AncPowerMode;
import com.anc.faceid.bean.AncFaceIdStatus;
import com.android.settings.anc.AncSettings;
import com.android.settings.anc.LiteManager;
import com.android.settings.anc.camera.CameraFactory;
import com.android.settings.anc.camera.CameraWrapper;
import com.android.settings.anc.util.Constants;
import com.android.settings.anc.lifecycle.ActivityManager;

public class UnlockActivity extends Activity implements CameraWrapper.IPreviewCallback {
    private static final String TAG = "UnlockActivity";
    private CameraWrapper mCameraWrapper;
    private LiteManager mLiteManager;
    // count ignored frames
    private int mFrameOffset = 0;
    private int failTimes = 0;
    private boolean isNoLimit;
    private long mLastFaceFailedTime;
    private static final int FACE_FAILED_INTERVAL_MS = 800; //ms

    private final CameraWrapper.CameraOpenCallback mCameraOpenListener = new CameraWrapper.CameraOpenCallback() {
        @Override
        public void onOpenSuccess() {
            // set orientation
            mCameraWrapper.setDisplayOrientation(Constants.ORIENTATION_90);
            mCameraWrapper.startPreview(null);
            startUnlock();
        }

        @Override
        public void onDisconnected() {
        }

        @Override
        public void onOpenFailed() {
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setGravity(Gravity.LEFT | Gravity.TOP);
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = 1;
        layoutParams.height = 1;
        layoutParams.type = WindowManager.LayoutParams.TYPE_STATUS_BAR;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        window.setAttributes(layoutParams);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
        LiteManager.getInstance().initLite(this, new LiteManager.Callback() {
            @Override
            public void onSuccess(Object object) {

            }

            @Override
            public void onFailed(int resultCode, Object object) {

            }

            @Override
            public void onError(String errorMsg) {

            }
        });
        mLiteManager = LiteManager.getInstance();
        AncFaceIdConfig config = LiteManager.getInstance().getConfig();
        config.rectTop = 0;
        config.rectLeft = 0;
        config.rectRight = 480;
        config.rectBottom = 640;
        LiteManager.getInstance().setConfig(config);
        mCameraWrapper = CameraFactory.getCamera();
        openCamera();
        isNoLimit = Settings.Global.getInt(getContentResolver(), "face_unlock_no_limit", 1) == 1;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(intent != null){
            boolean stopUnlock = intent.getBooleanExtra("stop_unlock",false);
            if(stopUnlock){
                finish();
                return;
            }
        }
        startUnlock();
    }

    @Override
    public void onPreviewFrame(final byte[] bytes) {
        if (++mFrameOffset < Constants.UNLOCK_IGNORED_AHEAD_FRAME ||
                !LiteManager.getInstance().canCompare() || (!isNoLimit && failTimes >= 3)) {
            return;
        }

        // compare
        LiteManager.getInstance().compare(bytes, mCameraWrapper.getWidth(),
                mCameraWrapper.getHeight(), mCameraWrapper.getAngle(),
                mCallBack);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraWrapper != null) {
            mCameraWrapper.stopPreview();
        }
        unregisterReceiver(mBroadcastReceiver);
        if (mCameraWrapper != null) {
            mCameraWrapper.closeCamera();
        }
        if (mLiteManager != null) {
            mLiteManager.reset();
            if(!ActivityManager.getInstance().containActivity(AncSettings.class.getSimpleName())){
                mLiteManager.release();
            }
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action) ||
                    Intent.ACTION_SCREEN_OFF.equals(action)) {
                UnlockActivity.this.finish();
            }
        }
    };

    // open Camera
    public void openCamera() {
        // open camera and enable detect
        mCameraWrapper.openCamera(false, this, mCameraOpenListener);
        mFrameOffset = 0;
    }

    private void startUnlock() {
        if (mLiteManager == null) {
            return;
        }
        mLiteManager.prepare(AncPowerMode.ANC_UNLOCK_POWER_HIGH);

        LiteManager.getInstance().setCompareTimeout(Constants.UNLOCK_TIMEOUT, mTimeoutCallback);

        mCameraWrapper.startDetect(this);

    }

    private void stopUnlock() {
        mCameraWrapper.stopDetect();

        mLiteManager.reset();
    }

    private final LiteManager.TimeoutCallback mTimeoutCallback = info -> {
        stopUnlock();
        failTimes++;
        Intent intent = new Intent("intent.action.faceunlock");
        if(isNoLimit) {
            intent.putExtra("faceunlock_status", 1);
        } else {
            intent.putExtra("faceunlock_status", failTimes);
        }
        Log.d(TAG,"face unlock failed");
        UnlockActivity.this.sendBroadcast(intent);
    };

    private final LiteManager.Callback mCallBack = new LiteManager.Callback() {
        @Override
        public void onSuccess(Object object) {
            // stop unlock firstly
            Log.d(TAG,"face unlock success");
            stopUnlock();
            sendFaceUnlockMsg(getString(com.android.settings.R.string.face_unlock_success));
            AncFaceIdUnlockInfo info = (AncFaceIdUnlockInfo) object;
            Intent intent = new Intent("intent.action.faceunlock");
            intent.putExtra("faceunlock_status", 0);
            UnlockActivity.this.sendBroadcast(intent);
            new Handler().postDelayed(() -> {
                UnlockActivity.this.finish();
            }, 500);

        }

        @Override
        public void onFailed(int resultCode, Object object) {
            Log.d(TAG, "onFailed()...resultCode:" + resultCode);
            if((SystemClock.uptimeMillis() - mLastFaceFailedTime) < FACE_FAILED_INTERVAL_MS) {
                return;
            }
            String acquiredStr = changeStatus(resultCode);
            if(acquiredStr != null) {
                sendFaceUnlockMsg(acquiredStr);
            }
        }

        @Override
        public void onError(String errorMsg) {
        }
    };

    private void sendFaceUnlockMsg(String msg) {
        Intent intent = new Intent("intent.action.faceunlock.acquired");
        intent.putExtra("faceunlock_acquired", msg);
        UnlockActivity.this.sendBroadcast(intent);
        mLastFaceFailedTime = SystemClock.uptimeMillis();
    }

    private String changeStatus(int code) {
        AncFaceIdStatus status = AncFaceIdStatus.valueOf(code);
        switch(status){
            case ANC_UNLOCK_FACE_NOT_FOUND:
                return getString(com.android.internal.R.string.face_acquired_not_detected);
            case ANC_UNLOCK_FACE_BAD_QUALITY:
                return getString(com.android.settings.R.string.face_acquired_bad_quality);
            case ANC_UNLOCK_HIGHLIGHT:
                return getString(com.android.internal.R.string.face_acquired_too_bright);
            case ANC_UNLOCK_DARKLIGHT:
                return getString(com.android.internal.R.string.face_acquired_too_dark);
            case ANC_UNLOCK_FACE_SCALE_TOO_LARGE:
                return getString(com.android.internal.R.string.face_acquired_too_close);
            case ANC_UNLOCK_FACE_SCALE_TOO_SMALL:
                return getString(com.android.internal.R.string.face_acquired_too_far);
            case ANC_UNLOCK_FACE_OFFSET_TOP:
                return getString(com.android.internal.R.string.face_acquired_too_low);
            case ANC_UNLOCK_FACE_OFFSET_BOTTOM:
                return getString(com.android.internal.R.string.face_acquired_too_high);
            case ANC_UNLOCK_FACE_OFFSET_RIGHT:
                return getString(com.android.internal.R.string.face_acquired_too_left);
            case ANC_UNLOCK_FACE_OFFSET_LEFT:
                return getString(com.android.internal.R.string.face_acquired_too_right);
            case ANC_UNLOCK_FACE_RISE:
            case ANC_UNLOCK_FACE_DOWN:
                return getString(com.android.internal.R.string.face_acquired_tilt_too_extreme);
            case ANC_UNLOCK_FACE_ROTATED_LEFT:
            case ANC_UNLOCK_FACE_ROTATED_RIGHT:
                return getString(com.android.internal.R.string.face_acquired_roll_too_extreme);
            case ANC_UNLOCK_ATTR_EYE_OCCLUSION:
                return getString(com.android.settings.R.string.face_acquired_eye_occlusion);
            case ANC_UNLOCK_ATTR_NOSE_OCCLUSION:
                return getString(com.android.settings.R.string.face_acquired_nose_occlusion);
            case ANC_UNLOCK_ATTR_MOUTH_OCCLUSION:
                return getString(com.android.settings.R.string.face_acquired_mouth_occlusion);
            case ANC_UNLOCK_FACE_BLUR:
                return getString(com.android.internal.R.string.face_acquired_sensor_dirty);
            case ANC_UNLOCK_COMPARE_FAILURE:
                return getString(com.android.settings.R.string.face_acquired_compare_failure);
            case ANC_UNLOCK_LIVENESS_FAILURE:
                return getString(com.android.settings.R.string.face_acquired_liveness_failure);
            case ANC_UNLOCK_ATTR_EYE_CLOSE:
                return getString(com.android.settings.R.string.face_acquired_eye_close);
            case ANC_UNLOCK_FACE_MULTI:
                return getString(com.android.settings.R.string.face_acquired_face_multi);
        }
        return null;
    }
}