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

import androidx.annotation.Nullable;

import com.anc.faceid.bean.AncFaceIdConfig;
import com.anc.faceid.bean.AncFaceIdUnlockInfo;
import com.anc.faceid.bean.AncPowerMode;
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
        if (!LiteManager.getInstance().canCompare() || (!isNoLimit && failTimes >= 3)) {
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
            mLiteManager.commitSave();
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
        }

        @Override
        public void onError(String errorMsg) {
        }
    };
}