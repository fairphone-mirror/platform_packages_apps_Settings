package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.qualcomm.qcrilhook.QcRilHookCallback;
import com.qualcomm.sysrilcmd.ISysRilCmd;
import com.qualcomm.sysrilcmd.SysRilCmd;

public class BackupImsProfile extends BroadcastReceiver {
    private static final String TAG = "BackupImsProfile";
    public final static String INTENT_SIM_STATE_CHANGED_STR = "android.intent.action.SIM_STATE_CHANGED";

    private boolean mRilHookReady;

    private boolean mEnabled;

    private Context context;



    private static final int MESSAGE_IMS_SWITCH = 1000;
    private static final int MESSAGE_IMS_SWITCH_COMPLETE = 1001;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

                case MESSAGE_IMS_SWITCH:
                    Log.d(TAG, " receive MESSAGE_IMS_SWITCH");

                    if (mRilHookReady) {
                        try {
                            mEnabled = (mSysRil.getInt8Val(ISysRilCmd.RIL_SUB_CMD_INT8_IMS_ENABLE) == 1);
                            Message msg1 = mHandler.obtainMessage(MESSAGE_IMS_SWITCH_COMPLETE);
                            mHandler.sendMessageDelayed(msg1, 1000);
                        } catch (Exception e) {
                            mEnabled = false;
                            Log.e(TAG, "SysRilCmd IOException" + e.getMessage());
                        }

                    } else {
                        Message msg2 = mHandler.obtainMessage(MESSAGE_IMS_SWITCH);
                        mHandler.sendMessageDelayed(msg2, 5000);
                    }
                break;

                case MESSAGE_IMS_SWITCH_COMPLETE:
                    Log.d(TAG, " receive MESSAGE_IMS_SWITCH_COMPLETE,mEnabled = " + mEnabled);
                    Settings.Global.putInt(context.getContentResolver(), "nv_ims_enable",
                            mEnabled ? 1 : 0);
                    mSysRil.SysRilDispose();
                    break;
            }
        }
    };

    private QcRilHookCallback mQcrilHookCb = new QcRilHookCallback() {
        public void onQcRilHookReady() {
            Log.d(TAG, " onQcRilHookReady");
            mRilHookReady = true;
        }

        @Override
        public void onQcRilHookDisconnected() {
            Log.d(TAG, " onQcRilHookDisconnected");
        }
    };
    private SysRilCmd  mSysRil = new SysRilCmd(context, mQcrilHookCb);



    @Override
    public void onReceive(Context context, Intent intent) {
        context = context;
        Settings.Global.putInt(context.getContentResolver(), "nv_ims_enable", 0);

        Message msg = mHandler.obtainMessage(MESSAGE_IMS_SWITCH);
        mHandler.sendMessageDelayed(msg, 1000);


    }
}