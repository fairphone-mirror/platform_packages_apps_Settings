package com.android.settings.anc.enroll;

import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_BLUR;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_DOWN;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_MULTI;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_NOT_COMPLETE;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_NOT_FOUND;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_OFFSET_BOTTOM;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_OFFSET_LEFT;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_OFFSET_RIGHT;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_OFFSET_TOP;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_RISE;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_SCALE_TOO_LARGE;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_FACE_SCALE_TOO_SMALL;
import static com.anc.faceid.bean.AncFaceIdStatus.ANC_UNLOCK_KEEP;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.ComponentName;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.anc.faceid.bean.AncFaceIdConfig;
import com.anc.faceid.bean.AncFaceIdStatus;
import com.anc.faceid.bean.AncPowerMode;
import com.android.settings.R;
import com.android.settings.anc.LiteManager;
import com.android.settings.anc.AncSettings;
import com.android.settings.anc.camera.CameraFactory;
import com.android.settings.anc.camera.CameraWrapper;
import com.android.settings.anc.util.BottomDialog;
import com.android.settings.anc.util.Constants;
import com.android.settings.anc.util.RoundClipView;
import com.android.settings.anc.lifecycle.ActivityManager;

public class EnrollActivity extends Activity implements CameraWrapper.IPreviewCallback {

    private static final String TAG = "EnrollActivity";

    private TextView mTxtFailedMsg;
    private SurfaceView mSurface;
    private SurfaceHolder mSurfaceHolder;
    // used to save feature
    private LiteManager mLiteManager;

    // camera
    private CameraWrapper mCameraWrapper;
    private boolean mIsCameraOpened;
    private RoundClipView mRoundClipView;
    private View mEnrollDone;
    private Button mBtDone;
    private ImageView mIvSuccess;
    private Handler mHandler;
    private Activity mActivity;
    private boolean stopDetect = false;
    private long mTextUpdateTime = 0L;
    private int mLastStatus = -1;
    private final int TEXT_UPDATE_DELAY_TIME = 500;
    private final int RETRY_DIALOG_TIME = 10000;
    private final String SYSTEM_RECENT_KEY = "recentapps";
    private MyReceiver mReceiver;
    private boolean isEnrollSuccess = false;
    private boolean mOperateRecent = false;

    private final LiteManager.Callback mCallBack = new LiteManager.Callback() {
        @Override
        public void onSuccess(Object object) {
            mHandler.removeCallbacks(mFaceTextRunnable);
            mHandler.removeCallbacks(mRunnable);
            mCameraWrapper.stopDetect();
            mHandler.postDelayed(() -> {
                mEnrollDone.setVisibility(View.VISIBLE);
                isEnrollSuccess = true;
                mSurface.setVisibility(View.INVISIBLE);
                AnimatedVectorDrawable animatedVectorDrawable = (AnimatedVectorDrawable) getDrawable(R.drawable.enroll_finished);
                if (animatedVectorDrawable != null) {
                    mIvSuccess.setImageDrawable(animatedVectorDrawable);
                    animatedVectorDrawable.start();
                }
            }, 300);
        }

        @Override
        public void onFailed(int resultCode, Object object) {
            AncFaceIdStatus status = AncFaceIdStatus.valueOf(resultCode);
            if (AncFaceIdStatus.ANC_UNLOCK_LIVENESS_FAILURE.toInt() == resultCode
                    || AncFaceIdStatus.ANC_UNLOCK_SAVE_FEATURES_MAX.toInt() == resultCode) {
                mCameraWrapper.closeCamera();
            } else {
                int curStatus = -1;
                if (status == ANC_UNLOCK_FACE_BLUR || status == ANC_UNLOCK_FACE_DOWN
                        || status == ANC_UNLOCK_FACE_RISE
                        || status == ANC_UNLOCK_FACE_SCALE_TOO_SMALL
                        || status == ANC_UNLOCK_FACE_SCALE_TOO_LARGE
                        || status == ANC_UNLOCK_FACE_MULTI
                        || status == ANC_UNLOCK_FACE_OFFSET_BOTTOM
                        || status == ANC_UNLOCK_FACE_OFFSET_LEFT
                        || status == ANC_UNLOCK_FACE_OFFSET_RIGHT
                        || status == ANC_UNLOCK_FACE_OFFSET_TOP
                        || status == ANC_UNLOCK_FACE_NOT_COMPLETE) {
                    curStatus = 1;
                } else if (status == ANC_UNLOCK_KEEP) {
                    curStatus = 2;
                } else if (status == ANC_UNLOCK_FACE_NOT_FOUND) {
                    curStatus = 0;
                }
                if (mLastStatus != curStatus) {
                    if (System.currentTimeMillis() - mTextUpdateTime > TEXT_UPDATE_DELAY_TIME) {
                        mHandler.removeCallbacks(mFaceTextRunnable);
                        if (curStatus == 0) {
                            mTxtFailedMsg.setText(getString(R.string.cannot_detect_face));
                        } else {
                            mTxtFailedMsg.setText(getString(R.string.scanning_face));
                        }
                        mLastStatus = curStatus;
                        mTextUpdateTime = System.currentTimeMillis();
                    } else {
                        mLastStatus = curStatus;
                        mHandler.postDelayed(mFaceTextRunnable, TEXT_UPDATE_DELAY_TIME);
                    }
                }
            }
        }

        @Override
        public void onError(String errorMsg) {
        }
    };

    private final CameraWrapper.CameraOpenCallback mCameraOpenListener = new CameraWrapper.CameraOpenCallback() {
        @Override
        public void onOpenSuccess() {
            mIsCameraOpened = true;
            checkAndStartPreview();
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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.anc_enroll_layout);
        mTxtFailedMsg = findViewById(R.id.tv_face_failed_msg);
        mSurface = findViewById(R.id.surface_camera);
        mRoundClipView = findViewById(R.id.face_id_clip_view);
        mEnrollDone = findViewById(R.id.enroll_done);
        mBtDone = findViewById(R.id.bt_done);
        mIvSuccess = findViewById(R.id.iv_success);
        mBtDone.setOnClickListener(v -> {
            boolean isNolimit = Settings.Global.getInt(getContentResolver(), "face_unlock_no_limit", 1) == 1;
            if(isNolimit) {
                EnrollActivity.this.finish();
            } else {
                showAttentionDialog();
            }
        });
        LiteManager.getInstance().initLite(this, new LiteManager.Callback() {

            @Override
            public void onSuccess(Object object) {
                mLiteManager.prepare(AncPowerMode.ANC_UNLOCK_POWER_HIGH);
                // open camera and enable detect
                mCameraWrapper.openCamera(false, EnrollActivity.this, mCameraOpenListener);
            }

            @Override
            public void onFailed(int resultCode, Object object) {
                Log.d(TAG, "initLite onFailed :" + resultCode);
            }

            @Override
            public void onError(String errorMsg) {
            }
        });
        mActivity = this;
        mHandler = new Handler();
        initSurfaceHolder();
        createCamera();
        // get LiteManager
        mLiteManager = LiteManager.getInstance();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mReceiver = new MyReceiver();
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraWrapper.closeCamera();
        mLiteManager.reset();
        finish();
    }

    @Override
    public void finish(){
        if(isEnrollSuccess) {
            setResult(RESULT_OK);
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        mActivity = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                if (reason == null) {
                    return;
                }

                if (SYSTEM_RECENT_KEY.equals(reason) && !mOperateRecent) {
                    if(ActivityManager.getInstance().containActivity(AncSettings.class.getSimpleName())){
                        Intent faceIntent = new Intent()
                            .setComponent(new ComponentName("com.android.settings","com.android.settings.anc.TempActivity"));
                        startActivity(faceIntent);
                    }
                    mOperateRecent = true;
                    EnrollActivity.this.finish();
                }
            }
        }
    }

    @Override
    public void onPreviewFrame(final byte[] bytes) {
        // need to check if feature save is executing
        if (!stopDetect && mLiteManager.canSaveFeature()) {
            boolean isMain = Settings.System.getInt(EnrollActivity.this.getContentResolver(), "enroll_main_face_id", 0) == 0;
            mLiteManager.saveFeature(bytes, mCameraWrapper.getWidth(),
                    mCameraWrapper.getHeight(), mCameraWrapper.getAngle(),
                    isMain, mCallBack);
        }
    }

    private void initSurfaceHolder() {
        SurfaceHolder surfaceHolder = mSurface.getHolder();
        surfaceHolder.setKeepScreenOn(true);
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceHolder = holder;
                checkAndStartPreview();
                // adjust surface layout
                adjustSurfaceViewSize();

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });
    }

    private void adjustSurfaceViewSize() {
        ViewGroup.LayoutParams layoutParams = mSurface.getLayoutParams();
        if (null == layoutParams) {
            return;
        }

        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        int width = dm.widthPixels;

        layoutParams.width = width;
        layoutParams.height = width;

        mSurface.setLayoutParams(layoutParams);
        mSurface.getHolder().setFixedSize(layoutParams.width, layoutParams.height);
        mSurface.requestLayout();
        mSurface.invalidate();
        ViewGroup.LayoutParams layoutParams1 = mRoundClipView.getLayoutParams();
        layoutParams1.width = layoutParams.width;
        layoutParams1.height = mCameraWrapper.getWidth() * width / mCameraWrapper.getHeight() / 3 * 2;
        mRoundClipView.setCalWh(layoutParams1.width, layoutParams1.height);
        mRoundClipView.setLayoutParams(layoutParams1);
        mRoundClipView.setRect(100, 50, 380, 400);
        AncFaceIdConfig config = LiteManager.getInstance().getConfig();
        config.rectTop = 50;
        config.rectLeft = 50;
        config.rectRight = 430;
        config.rectBottom = 480;
        LiteManager.getInstance().setConfig(config);

    }

    private void createCamera() {
        mCameraWrapper = CameraFactory.getCamera();
    }

    private void checkAndStartPreview() {
        if (mIsCameraOpened && null != mSurfaceHolder) {
            // set orientation
            mCameraWrapper.setDisplayOrientation(Constants.ORIENTATION_90);

            //start preview
            mCameraWrapper.startPreview(mSurfaceHolder);
            mCameraWrapper.startDetect(this);
            mHandler.postDelayed(mRunnable, RETRY_DIALOG_TIME);
        }
    }

    private final Runnable mFaceTextRunnable = new Runnable() {
        @Override
        public void run() {
            long time = System.currentTimeMillis() - mTextUpdateTime;
            if (time < TEXT_UPDATE_DELAY_TIME) {
                mHandler.postDelayed(mFaceTextRunnable, time);
                return;
            }
            if (mLastStatus == 0) {
                mTxtFailedMsg.setText(getString(R.string.cannot_detect_face));
            } else {
                mTxtFailedMsg.setText(getString(R.string.scanning_face));
            }
            mTextUpdateTime = System.currentTimeMillis();
        }
    };
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (mActivity == null) {
                return;
            }
            stopDetect = true;
            AlertDialog dialog = new AlertDialog.Builder(mActivity, R.style.Theme_Dialog)
                    .setTitle(R.string.face_detect_timeout_dialog_title)
                    .setMessage(R.string.face_detect_timeout_dialog_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.face_detect_timeout_dialog_button_retry, (dialog2, which) -> {
                        dialog2.dismiss();
                        stopDetect = false;
                        mHandler.postDelayed(mRunnable, RETRY_DIALOG_TIME);
                    })
                    .setNegativeButton(R.string.cancel, (dialog1, which) -> {
                        dialog1.dismiss();
                        finish();
                    })
                    .create();
            dialog.show();
        }
    };

    private void showAttentionDialog() {
        BottomDialog dialog = new BottomDialog(EnrollActivity.this, R.layout.dialog_enroll_success_attention);
        dialog.setOnClickListener(R.id.bt_ok, v -> {
            dialog.dismiss();
            EnrollActivity.this.finish();
        });
        dialog.show();
    }
}
