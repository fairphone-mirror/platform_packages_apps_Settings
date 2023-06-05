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

import androidx.annotation.Nullable;

import com.anc.faceid.bean.AncFaceIdConfig;
import com.anc.faceid.bean.AncFaceIdUnlockInfo;
import com.anc.faceid.bean.AncPowerMode;
import com.android.settings.anc.LiteManager;
import com.android.settings.anc.camera.CameraFactory;
import com.android.settings.anc.camera.CameraWrapper;
import com.android.settings.anc.util.Constants;

public class UnlockActivity extends Activity implements CameraWrapper.IPreviewCallback {
    private static final String TAG = "UnlockActivity";
    private CameraWrapper mCameraWrapper;
    private LiteManager mLiteManager;
    // count ignored frames
    private int mFrameOffset = 0;
    private int failTimes = 0;

    private final CameraWrapper.CameraOpenCallback mCameraOpenListener = new CameraWrapper.CameraOpenCallback() {
        @Override
        public void onOpenSuccess() {
            Log.d(TAG, "onOpenSuccess");
            // set orientation
            mCameraWrapper.setDisplayOrientation(Constants.ORIENTATION_90);
            mCameraWrapper.startPreview(null);
            startUnlock();
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "onDisconnected");
        }

        @Override
        public void onOpenFailed() {
            Log.d(TAG, "onOpenFailed");
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        //setShowWhenLocked(true);
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
        registerReceiver(mBroadcastReceiver, filter);
        LiteManager.getInstance().initLite(this, new LiteManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                mCameraWrapper = CameraFactory.getCamera();
                mLiteManager = LiteManager.getInstance();
                AncFaceIdConfig config = LiteManager.getInstance().getConfig();
                config.rectTop = 0;
                config.rectLeft = 0;
                config.rectRight = 480;
                config.rectBottom = 640;
                LiteManager.getInstance().setConfig(config);
                openCamera();
            }

            @Override
            public void onFailed(int resultCode, Object object) {

            }

            @Override
            public void onError(String errorMsg) {

            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        startUnlock();
    }

    @Override
    public void onPreviewFrame(final byte[] bytes) {
        Log.d(TAG, "onPreviewFrame()... ");
        if (++mFrameOffset < Constants.UNLOCK_IGNORED_AHEAD_FRAME ||
                !LiteManager.getInstance().canCompare() || failTimes >= 3) {
            Log.d(TAG, "not to compare too many failTimes:" + failTimes);
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
        Log.d(TAG, "onDestroy");
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
//            mLiteManager.release();
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive action:" + action);
            if (Intent.ACTION_USER_PRESENT.equals(action) ||
                    Intent.ACTION_SCREEN_OFF.equals(action)) {
                UnlockActivity.this.finish();
            }
        }
    };

    // open Camera
    public void openCamera() {
        Log.d(TAG, "openCamera()...");
        // open camera and enable detect
        mCameraWrapper.openCamera(false, this, mCameraOpenListener);
        mFrameOffset = 0;
    }

    private void startUnlock() {
        if (mLiteManager == null) {
            return;
        }
        Log.d(TAG, "startUnlock()...");
        mLiteManager.prepare(AncPowerMode.ANC_UNLOCK_POWER_HIGH);

        LiteManager.getInstance().setCompareTimeout(Constants.UNLOCK_TIMEOUT, mTimeoutCallback);

        mCameraWrapper.startDetect(this);

    }

    private void stopUnlock() {
        Log.d(TAG, "stopUnlock()...");
        mCameraWrapper.stopDetect();

        mLiteManager.reset();
    }

    private final LiteManager.TimeoutCallback mTimeoutCallback = info -> {
        Log.d(TAG, "onTimeout()...");
        stopUnlock();
        failTimes++;
        Intent intent = new Intent("intent.action.faceunlock");
        intent.putExtra("faceunlock_status", failTimes);
        UnlockActivity.this.sendBroadcast(intent);
    };

    private final LiteManager.Callback mCallBack = new LiteManager.Callback() {
        @Override
        public void onSuccess(Object object) {
            // stop unlock firstly
            stopUnlock();
            AncFaceIdUnlockInfo info = (AncFaceIdUnlockInfo) object;
            Log.d(TAG, "onSuccess()..." + info.compareScore + " feature id: " + info.faceId);
            Log.d(TAG, "info.unlockSpendTime:" + info.unlockSpendTime + ",info.detectSpendTime:" +
                    info.detectSpendTime + ",info.liveSpendTime:" + info.liveSpendTime + ",info.featureSpendTime:" + info.featureSpendTime);
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
            Log.d(TAG, "onError()...errorMsg:" + errorMsg);
        }
    };
}