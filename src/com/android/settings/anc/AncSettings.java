package com.android.settings.anc;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.app.Activity;
import android.view.Menu;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.anc.faceid.bean.AncFaceIdStatus;
import com.android.settings.R;
import com.android.settings.anc.intro.IntroFaceUnlockActivity;
import com.android.settings.anc.lifecycle.ActivityManager;
import com.android.settings.anc.util.BottomDialog;
import com.android.settings.anc.util.Constants;
import com.android.settings.anc.util.DialogUtil;
import com.android.settings.anc.BaseActivity;
import com.android.settings.password.ChooseLockSettingsHelper;

import java.io.File;

public class AncSettings extends BaseActivity {
    private static final String TAG = "AncSettings";
    private ConstraintLayout mSecondFaceLayout;
    private ImageView mMainDelete;
    private ImageView mSecondDelete;
    private DialogUtil mDialogUtil;
    private boolean hasMain = false;
    private boolean hasSecond = false;
    private boolean firstTime = true;
    private MyReceiver mReceiver;
    private KeyguardManager mKeyguardManager;
    private final String SYSTEM_HOME_KEY = "homekey";
    private final String SYSTEM_RECENT_KEY = "recentapps";
    private final String SYSTEM_POWER_KEY = "dream";
    private final int REQUEST_CONFIRM_CODE = 101;
    private Handler mHandler;
    private boolean mShowConfirm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.anc_face_settings);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }
        mDialogUtil = new DialogUtil(this);
        mHandler = new Handler();
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
        ConstraintLayout mMainFaceLayout = findViewById(R.id.main_face_layout);
        mSecondFaceLayout = findViewById(R.id.second_face_layout);
        mMainDelete = findViewById(R.id.main_delete_button);
        mSecondDelete = findViewById(R.id.second_delete_button);
        mMainFaceLayout.setOnClickListener(v->{
            Intent enrollIntent = new Intent(AncSettings.this, IntroFaceUnlockActivity.class);
            if (!hasMain) {
                enrollIntent.putExtra("enroll_main", true);
                startActivity(enrollIntent);
            }
        });
        mSecondFaceLayout.setOnClickListener(v->{
            Intent enrollIntent = new Intent(AncSettings.this, IntroFaceUnlockActivity.class);
            if (!hasMain && !hasSecond) {
                showEnrollMainFirst();
            } else if (!hasSecond) {
                enrollIntent.putExtra("enroll_main", false);
                startActivity(enrollIntent);
            }
        });
        mMainDelete.setOnClickListener(v->{
            if (hasMain) {
                mDialogUtil.showDialog(R.string.faceunlock_last_delete_title, R.string.confirm_remove_enroll_face, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int faceId = Settings.System.getInt(getContentResolver(), "enroll_main_face_id", 0);
                        if (faceId > 0) {
                            boolean success = deleteFeatureFace(faceId);
                            if (success) {
                                Settings.System.putInt(getContentResolver(), "enroll_main_face_id", 0);
                                updateButton();
                            }
                        }
                    }
                });
            }
        });
        mSecondDelete.setOnClickListener(v->{
            if (hasSecond) {
                mDialogUtil.showDialog(R.string.faceunlock_last_delete_title, R.string.confirm_remove_enroll_face, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int faceId = Settings.System.getInt(getContentResolver(), "enroll_second_face_id", 0);
                        if (faceId > 0) {
                            boolean success = deleteFeatureFace(faceId);
                            if (success) {
                                Settings.System.putInt(getContentResolver(), "enroll_second_face_id", 0);
                                updateButton();
                            }
                        }
                    }
                });
            }
        });
        mReceiver = new MyReceiver();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, intentFilter);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    }

    private boolean runKeyguardConfirmation(int request) {
        final ChooseLockSettingsHelper.Builder builder =
                new ChooseLockSettingsHelper.Builder(this);
        return builder.setRequestCode(request).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        updateButton();
        if (firstTime && !hasMain && !hasSecond) {
            Intent intent = new Intent(AncSettings.this, IntroFaceUnlockActivity.class);
            startActivity(intent);
        }
        firstTime = false;
        mHandler.postDelayed(() -> {
            Log.d(TAG, "isLock:" + mKeyguardManager.isKeyguardLocked() + ",isDeviceLocked:" + mKeyguardManager.isDeviceLocked());
            if (mShowConfirm && !mKeyguardManager.isKeyguardLocked()) {
                runKeyguardConfirmation(REQUEST_CONFIRM_CODE);
                mShowConfirm = false;
            }
        }, 200);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIRM_CODE) {
            if (resultCode == RESULT_OK) {

            } else if (resultCode == RESULT_CANCELED) {
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mHandler.removeCallbacksAndMessages(null);
        unregisterReceiver(mReceiver);
        mDialogUtil.onDestroy();
        LiteManager.getInstance().release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        mShowConfirm = false;
    }

    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ActivityManager.getInstance().getCurrentActivity() != AncSettings.this) {
                Log.d(TAG, "current activity is not AncSettings");
                return;
            }
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                Log.d(TAG, "reason:" + reason);
                if (reason == null) {
                    return;
                }
                if (reason.equals(SYSTEM_HOME_KEY)) {
                    mShowConfirm = true;
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mShowConfirm = true;
            }
        }
    }

    private void showEnrollMainFirst() {
        BottomDialog dialog = new BottomDialog(AncSettings.this, R.layout.dialog_enroll_main_first);
        dialog.setOnClickListener(R.id.bt_ok, v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updateButton() {
        hasMain = Settings.System.getInt(getContentResolver(), "enroll_main_face_id", 0) > 0;
        hasSecond = Settings.System.getInt(getContentResolver(), "enroll_second_face_id", 0) > 0;
        mMainDelete.setVisibility(hasMain ? View.VISIBLE : View.GONE);
        mSecondDelete.setVisibility(hasSecond ? View.VISIBLE : View.GONE);
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