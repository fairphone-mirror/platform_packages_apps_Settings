package com.android.settings.anc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.StatFs;

import androidx.annotation.NonNull;

import android.provider.Settings;
import android.util.Log;

import com.anc.faceid.api.AncFaceIdApi;
import com.anc.faceid.bean.AncFaceIdConfig;
import com.anc.faceid.bean.AncFaceIdStatus;
import com.anc.faceid.bean.AncFaceIdUnlockInfo;
import com.anc.faceid.bean.AncPowerMode;
import com.anc.faceid.utils.AncConstants;
import com.android.settings.anc.util.ConfigInfoManager;
import com.android.settings.anc.util.CommonUtil;
import com.android.settings.anc.util.Constants;
import com.android.settings.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Single Instance Class, access this instance by calling getInstance()
 * <p>
 * (1)LiteManager start worker thread for processing initLite/authFaceId/enrollFaceId/restoreFaceId
 * (2)The key code:
 * initLite    -> handleInit
 * authFaceId     -> handleCompare
 * enrollFaceId -> handleSaveFeature
 * (3)LiteManager maintain State for authFaceId and save feature.
 * Caller need call canCompare/canSaveFeature to check before executing authFaceId/enrollFaceId
 * <p>
 * (4)Every success or timeout of authFaceId/enrollFaceId, need call reset() for maintaining correct state
 */
public class LiteManager {
    private static final String TAG = "LiteManager";

    // only one instance in one process
    private static LiteManager sInstance;
    private Context mContext;

    // notify caller in UI Thread
    private final static int EVENT_INIT = 1;
    private final static int EVENT_SAVEFEATURE = 2;
    private final static int EVENT_COMPARE = 3;
    private final static int EVENT_COMPARE_TIMEOUT = 4;
    private final static int EVENT_SAVEFEATURE_TIMEOUT = 5;
    private final static int EVENT_RESTOREFEATURE = 6;
    private Handler mUIHandler;
    private Handler.Callback mUIHanlderCallback;

    // process enroll logic in worker thread
    private HandlerThread mHandlerThread;
    private Handler mHandler;


    // State of Feature Save
    private AtomicInteger mFeatureSaveState = new AtomicInteger(State.IDLE);
    // State of Compare
    private AtomicInteger mCompareState = new AtomicInteger(State.IDLE);
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd-HH_mm_ss");


    // Timeout of Feature Save
    private long mFeatureSaveTimeout = 0;  // ms
    private TimeoutCallback mFeatureSaveTimeoutCallback;
    // Timeout of Compare
    private long mCompareTimeout = 0;   // ms
    private TimeoutCallback mCompareTimeoutCallback;

    private byte[] mArrayFeature = new byte[AncConstants.FEATURE_SIZE];
    private byte[] mArrayImage = new byte[AncConstants.IMAGE_SIZE];
    private AncFaceIdUnlockInfo mUnlockInfo = new AncFaceIdUnlockInfo();

    private HashMap<String, Integer> mLiveTypeMap = new HashMap<>();
    private HashMap<String, Integer> mExtractMap = new HashMap<>();

    protected class CachedNV21 {
        byte[] data;
        int w;
        int h;
        String path;
        String name;
    }

    /**
     * get Instance of LiteManager
     *
     * @return Single Instance of LiteManager
     */
    public static LiteManager getInstance() {
        if (null == sInstance) {
            synchronized (LiteManager.class) {
                if (null == sInstance) {
                    sInstance = new LiteManager();
                }
            }
        }
        return sInstance;
    }

    private LiteManager() {
        mLiveTypeMap.put("NONE", 0);
        mLiveTypeMap.put("CPU", 1);
        mLiveTypeMap.put("SNPE", 2);
        mLiveTypeMap.put("OPENCL", 3);
        mLiveTypeMap.put("NPU", 4);
        mLiveTypeMap.put("DSP", 5);

        mExtractMap.put("SINGLE_CORE_NORMAL", 0);
        mExtractMap.put("DOUBLE_CORE_NORMAL", 1);
        mExtractMap.put("OPENCL", 2);
        mExtractMap.put("DSP", 3);
        mExtractMap.put("APU", 4);
    }

    /**
     * AncFaceIdApi Init
     *
     * @param context  must be set to nonnull and Application context is preferred.
     * @param callBack
     */
    public void initLite(@NonNull final Context context, final Callback callBack) {
        // check if params is invalid
        if (null == context) {
            Log.e(TAG, "context is null");
            callBack.onError("context is null");
            return;
        }

        // init
        initManager(context);

        // process init in worker thread/looper
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int result = handleInit();

                // notify caller in UI thread
                sendResultMessage(result, callBack, EVENT_INIT);
            }
        });
    }

    /**
     * 调用解锁或者feature录入时需要调用prepare接口
     *
     * @param mode 功耗模式
     * @return 成功则返回ANC_UNLOCK_OK
     */
    public AncFaceIdStatus prepare(AncPowerMode mode) {
        return AncFaceIdApi.getInstance().prepare(mode);
    }

    /**
     * 结束一次解锁或者底库录入行为后（如解锁成功或者超时等）调用此接口复位sdk状态
     *
     * @return 成功则返回ANC_UNLOCK_OK
     */
    public AncFaceIdStatus reset() {
        // reset state
        resetManager();
        mLastResult = -1;
        return AncFaceIdApi.getInstance().reset();
    }

    public void setConfig(AncFaceIdConfig config) {
        AncFaceIdApi.getInstance().setConfig(config);
    }

    public AncFaceIdConfig getConfig() {
        return AncFaceIdApi.getInstance().getConfig();
    }

    /**
     * 释放所有已加载模型，并释放人脸解锁句柄
     * 释放LiteManager相关资源、
     */
    public void release() {
        // unInit LiteManager Inner
        releaseManager();

        // 释放SDK相关资源
        AncFaceIdApi.getInstance().unInit();

    }

    /**
     * save Feature
     * 调用saveFeature函数前，需要调用canCompare判断当前状态
     *
     * @param imageData NV21 当前帧的图像数据。
     * @param width     当前帧的图像的宽度640，如果当前帧为2pd则传-1
     * @param height    当前帧的图像的高度480，如果当前帧为2pd则传-1
     * @param angle     当前帧的图像的偏转角度
     * @param callBack
     * @parm isMain     是否录入主人像
     */
    public void saveFeature(final byte[] imageData, final int width, final int height, final int angle, final boolean isMain,
                            final Callback callBack) {
        // check if params is invalid
        if (null == imageData || angle < 0) {
            Log.e(TAG, "Invalid params.");

            if (null != callBack) {
                callBack.onError("Invalid params");
            }
            return;
        }

        // If set timeout callback, post delayed event
        if (!mUIHandler.hasMessages(EVENT_SAVEFEATURE_TIMEOUT) && null != mFeatureSaveTimeoutCallback) {
            mUIHandler.sendEmptyMessageDelayed(EVENT_SAVEFEATURE_TIMEOUT, mFeatureSaveTimeout);
        }

        // process init in worker thread/looper
        setFeatureSaveState(State.RUNNING);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int result = handleSaveFeature(imageData, width, height, angle, isMain);

                // notify caller in UI thread
                sendResultMessage(result, callBack, EVENT_SAVEFEATURE);
            }
        });
    }

    /**
     * compare
     * 调用compare函数前，需要调用canCompare判断当前状态
     *
     * @param imageData NV21 当前帧的图像数据。
     * @param width     当前帧的图像的宽度640，如果当前帧为2pd则传-1
     * @param height    当前帧的图像的高度480，如果当前帧为2pd则传-1
     * @param angle     当前帧的图像的偏转角度
     * @param callBack  通过Obejct参数返回比对的时间
     */
    public void compare(final byte[] imageData, final int width, final int height, final int angle,
                        final Callback callBack) {
        // check if params is invalid
        if (null == imageData || angle < 0) {
            Log.e(TAG, "Invalid params.");

            if (null != callBack) {
                callBack.onError("Invalid params.");
            }
            return;
        }

        // If set timeout callback, post delayed event
        if (!mUIHandler.hasMessages(EVENT_COMPARE_TIMEOUT) && null != mCompareTimeoutCallback) {
            mUIHandler.sendEmptyMessageDelayed(EVENT_COMPARE_TIMEOUT, mCompareTimeout);
        }

        // process init in worker thread/looper
        setCompareState(State.RUNNING);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int result = handleCompare(imageData, width, height, angle);

                // notify caller in UI thread
                sendResultMessage(result, callBack, EVENT_COMPARE);
            }
        });
    }

    /**
     * 从保存的图片中恢复底库，一般在sdk版本升级时调用以兼容新版本
     *
     * @return 成功则返回ANC_UNLOCK_OK
     */
    public void restoreFeature(final Callback callBack) {
        // process init in worker thread/looper
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int result = AncFaceIdApi.getInstance().restoreFaceId().toInt();

                // notify caller in UI thread
                sendResultMessage(result, callBack, EVENT_RESTOREFEATURE);
            }
        });
    }

    /**
     * 获取有效的feature数量
     *
     * @return 有效的feature数量
     */
    public int getFeatureCount() {
        return AncFaceIdApi.getInstance().getFaceIdCount();
    }

    public boolean checkFaceId(int id){
        AncFaceIdStatus status = AncFaceIdApi.getInstance().checkFaceId(id);
        return status == AncFaceIdStatus.ANC_UNLOCK_OK;
    }

    /**
     * 删除底库文件（多底库版本调用,单底库版本调用不能保证正确性，建议不在单底库版本中调用）
     *
     * @param id 目标feature的id
     * @return -1                            删除失败
     * 0                             删除成功
     */
    public int deleteFeature(int id) {
        return AncFaceIdApi.getInstance().deleteFaceId(id).toInt();
    }

    /**
     * Set Timeout for Compare
     *
     * @param timeout  ms
     * @param callBack if callback = null, unset time out callback
     * @return true: set successfully, false: invalid params
     */
    public boolean setCompareTimeout(int timeout, TimeoutCallback callBack) {
        // set timeout params
        mCompareTimeout = timeout;
        mCompareTimeoutCallback = callBack;

        return true;
    }

    /**
     * Set Timeout for saving Feature.
     *
     * @param timeout  ms
     * @param callBack if callback = null, unset time out callback
     * @return true: set successfully, false: invalid params
     */
    public boolean setFeatureSaveTimeout(int timeout, @NonNull TimeoutCallback callBack) {
        // set timeout params
        mFeatureSaveTimeout = timeout;
        mFeatureSaveTimeoutCallback = callBack;

        return true;
    }

    /**
     * check if continue to save feature
     *
     * @return true: continue to call enrollFaceId
     */
    public boolean canSaveFeature() {
        return (State.IDLE == mFeatureSaveState.get() ||
                State.FAILED == mFeatureSaveState.get());
    }

    /**
     * check if continue to authFaceId.
     *
     * @return true: continue to call authFaceId
     */
    public boolean canCompare() {
        return (State.IDLE == mCompareState.get() ||
                State.FAILED == mCompareState.get());
    }

    // loadModel AncFaceIdApi SDK
    private int handleInit() {

        //(1)get folder for save files of face unlock
        File dir = mContext.getExternalFilesDir(Constants.UNLOCK_FACE_FOLDER_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        AncFaceIdApi ancFaceIdApi = AncFaceIdApi.getInstance();
        //(2)loadModel
        //ancFaceIdApi.unInit();
        File model_dir = new File(dir, "model");
        if (model_dir.listFiles() == null) {
            File SDcard_path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(SDcard_path.getPath());
            long avaliableBlocks = stat.getAvailableBlocks();
            if (avaliableBlocks < 10240) //40M
                return AncFaceIdStatus.ANC_UNLOCK_UNAVAILABLE_MEMORY.toInt();
        }

        ancFaceIdApi.init(dir.getAbsolutePath(), null);
        ancFaceIdApi.setLogLevel(1);
        //(3)customConfig
        AncFaceIdConfig config = ConfigInfoManager.getInstance().genCustomConfig(mContext);

        String targetModel = CommonUtil.readModelInfo(mContext, R.raw.model_config).get(2);//BuildConfig.ANC_MODEL_FILE;
        String[] partModel = targetModel.split("-");
        String liveTypeModel = partModel[3].split("_")[1];
        String extractMode = partModel[partModel.length - 1].replace("recognize_", "").replaceAll(".pack", "");

        config.compDeviceType = mLiveTypeMap.get(liveTypeModel);
        config.extractConfig = mExtractMap.get(extractMode);

        // set opencl cache path
        if (config.extractConfig == AncConstants.ANC_UNLOCK_EXTRACT_OPENCL ||
                config.compDeviceType == AncConstants.ANC_UNLOCK_COMP_DEVICE_OPENCL) {
            config.openclCachePath = dir.getAbsolutePath();
        }
        if (config.extractConfig == AncConstants.ANC_UNLOCK_EXTRACT_DSP) {
//            config.nativeLibraryPath = mContext.getApplicationContext().getApplicationInfo().nativeLibraryDir;
            File file = new File(Environment.getExternalStorageDirectory().getPath(), Constants.DSP_LIB_PATH);
            config.nativeLibraryPath = file.getAbsolutePath();
        }
        if (config.compDeviceType == AncConstants.ANC_UNLOCK_COMP_DEVICE_SNPE) {
            config.snpeCachePath = dir.getAbsolutePath();
        }

        //config.storeDebugImgMode = 1;
        config.saveImagePath = dir.getAbsolutePath();
        config.bigCpuCore = AncConstants.ANC_UNLOCK_BIG_CPU_CORE_HIGH;

        config.enrollMinRectWidth = 120;
        config.enrollMinRectHeigh = 160;
        config.enrollMaxRectWidth = 480;
        config.enrollMaxRectHeigh = 640;

        config.authMinRectWidth = 60;
        config.authMinRectHeigh = 80;
        config.authMaxRectWidth = 480;
        config.authMaxRectHeigh = 640;

        ancFaceIdApi.setConfig(config);

        //(4)save model to local storage
        String modelPath = CommonUtil.saveRaw(mContext, R.raw.anc_model_file,
                "model", "anc_model_file");
        if (modelPath.equals(CommonUtil.Memory_flag)) {
            return AncFaceIdStatus.ANC_UNLOCK_UNAVAILABLE_MEMORY.toInt();
        }

        //(5)loadModel All
        final int result = ancFaceIdApi.loadModel(modelPath).toInt();
        if(ancFaceIdApi.checkFeatureUpdate() == AncFaceIdStatus.ANC_UNLOCK_NEED_RESTORE_FEATURE) {
            restoreFeature(new Callback() {
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
        }
        return result;
    }

    // feature save
    private int handleSaveFeature(final byte[] imageData, final int width, final int height, final int angle, final boolean isMain) {

        //(1)Check PD data or not
        byte[] data = imageData;
        int curWidth = width;
        int curHeight = height;

        //(2)feature saving
        int[] id = new int[1];
        AncFaceIdStatus result = AncFaceIdApi.getInstance().enrollFaceId(data, curWidth, curHeight, angle,
                mArrayFeature, mArrayImage, id);

        //(3)save feature image for Demo Apk
        if (result == AncFaceIdStatus.ANC_UNLOCK_OK) {
            byte[] yuvData = data;

            Bitmap bitmap = CommonUtil.getBitMap(yuvData, new Rect(0, 0,
                    Constants.FRAME_IMAGE_WIDTH, Constants.FRAME_IMAGE_HEIGHT), angle, false);
            String fileName = String.format("%s%s", Constants.UNLOCK_FACE_FEATURE_NAME,
                    id[0]);
            if (isMain) {
                Settings.System.putInt(mContext.getContentResolver(), "enroll_main_face_id", id[0]);
            } else {
                Settings.System.putInt(mContext.getContentResolver(), "enroll_second_face_id", id[0]);
            }
            CommonUtil.saveBitmap(mContext, bitmap,
                    Constants.UNLOCK_FACE_FEATURE_PATH, fileName);
        }

        if (result == AncFaceIdStatus.ANC_UNLOCK_OK) {
            addSavedNV21(data, width, height, "camera", "savedYUV" + id[0]);
            commitSave();
        }

        return result.toInt();
    }

    // authFaceId
    private int handleCompare(final byte[] imageData, final int width, final int height, final int angle) {
        //(1)Check PD data or not
        byte[] data = imageData;
        int curWidth = width;
        int curHeight = height;

        //(2)authFaceId
        long start = System.currentTimeMillis();
        // reset Report info

        int result = AncFaceIdApi.getInstance().authFaceId(data, curWidth, curHeight, angle, mUnlockInfo).toInt();


        String fileName = simpleDateFormat.format(new Date()) + "." + (System
                .currentTimeMillis() % 1000);
        if (mLastResult != result) {
            mLastResult = result;
            saveComparePic(width, height, data, result, fileName);
        }

        return result;
    }

    int mLastResult = -1;

    private void saveComparePic(int width, int height, byte[] data, int result, String fileName) {
        AncFaceIdStatus status = AncFaceIdStatus.valueOf(result);
        if (status.equals(AncFaceIdStatus.ANC_UNLOCK_OK)) {
            addSavedNV21(data, width, height, "test_pass", fileName);
        } else {
            switch (status) {
                case ANC_UNLOCK_LIVENESS_FAILURE:
                    String path;
                    path = "liveFailed";
                    addSavedNV21(data, width, height, path, fileName);
                    break;
                case ANC_UNLOCK_COMPARE_FAILURE:
                    path = "compareFailed";
                    addSavedNV21(data, width, height, path, fileName);
                    break;
                case ANC_UNLOCK_FACE_OFFSET_BOTTOM:
                    break;
                case ANC_UNLOCK_FACE_OFFSET_LEFT:
                    break;
                case ANC_UNLOCK_FACE_OFFSET_RIGHT:
                    break;
                case ANC_UNLOCK_FACE_OFFSET_TOP:
                    break;
                case ANC_UNLOCK_FACE_BAD_QUALITY:
                    break;
                case ANC_UNLOCK_FACE_SCALE_TOO_LARGE:
                    break;
                case ANC_UNLOCK_FACE_SCALE_TOO_SMALL:
                    break;
                case ANC_UNLOCK_FAILURE:
                    break;
                case ANC_UNLOCK_FACE_NOT_FOUND:
                    path = "faceNotFound";
                    addSavedNV21(data, width, height, path, fileName);
                    break;
                case ANC_UNLOCK_ATTR_BLUR:
                    break;
                case ANC_UNLOCK_ATTR_EYE_OCCLUSION:
                    break;
                case ANC_UNLOCK_ATTR_EYE_CLOSE:
                    path = "eyeclose";
                    addSavedNV21(data, width, height, path, fileName);
                    break;
                case ANC_UNLOCK_ATTR_MOUTH_OCCLUSION:
                    break;
                default:
            }
        }
    }

    // LiteSDK inner loadModel
    private void initManager(final Context context) {
        // create worker thread
        if (null == mHandlerThread) {
            mHandlerThread = new HandlerThread(TAG);
            mHandlerThread.start();
        }
        if (null == mHandler) {
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        // set context
        mContext = context.getApplicationContext();
        //init UI handler
        initUIHandler(mContext);
    }

    //init UI Handler
    private void initUIHandler(final Context context) {
        mUIHanlderCallback = new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what) {
                    case EVENT_INIT:
                        callbackToCaller(message.arg1, null, (Callback) message.obj);
                        break;
                    case EVENT_SAVEFEATURE:
                        // notify time out to caller when current state is TIMEOUT
                        // and enrollFaceId is failed.
                        if (AncFaceIdStatus.ANC_UNLOCK_OK.toInt() != message.arg1 &&
                                State.TIMEOUT == mFeatureSaveState.get() &&
                                null != mFeatureSaveTimeoutCallback) {
                            mFeatureSaveTimeoutCallback.onTimeout(mUnlockInfo);
                        } else {
                            // set state of save feature
                            setFeatureSaveState(AncFaceIdStatus.ANC_UNLOCK_OK.toInt() == message.arg1
                                    ? State.SUCCESS : State.FAILED);

                            // remove timout event
                            if (AncFaceIdStatus.ANC_UNLOCK_OK.toInt() == message.arg1) {
                                removeUIHandlerEvent(EVENT_SAVEFEATURE_TIMEOUT);
                            }

                            // notify user
                            callbackToCaller(message.arg1, null, (Callback) message.obj);
                        }
                        break;
                    case EVENT_COMPARE:
                        // notify time out to caller when current state is TIMEOUT
                        // and authFaceId is failed.
                        if (AncFaceIdStatus.ANC_UNLOCK_OK.toInt() != message.arg1 &&
                                State.TIMEOUT == mCompareState.get() &&
                                null != mCompareTimeoutCallback) {
                            mCompareTimeoutCallback.onTimeout(mUnlockInfo);
                        } else {
                            setCompareState(AncFaceIdStatus.ANC_UNLOCK_OK.toInt() == message.arg1
                                    ? State.SUCCESS : State.FAILED);

                            // remove timout event
                            if (AncFaceIdStatus.ANC_UNLOCK_OK.toInt() == message.arg1) {
                                removeUIHandlerEvent(EVENT_COMPARE_TIMEOUT);
                            }

                            // notify user
                            callbackToCaller(message.arg1, mUnlockInfo, (Callback) message.obj);
                        }
                        break;
                    case EVENT_COMPARE_TIMEOUT:
                        int lastCompareState = mCompareState.get();
                        // set state of authFaceId
                        setCompareState(State.TIMEOUT);

                        // authFaceId isn't executing, notify caller quickly
                        if (State.FAILED == lastCompareState && null != mCompareTimeoutCallback) {
                            mCompareTimeoutCallback.onTimeout(mUnlockInfo);
                        }
                        break;
                    case EVENT_SAVEFEATURE_TIMEOUT:
                        int lastFeatureSaveState = mFeatureSaveState.get();
                        // set state of save feature
                        setFeatureSaveState(State.TIMEOUT);

                        if (State.FAILED == lastFeatureSaveState && null != mFeatureSaveTimeoutCallback) {
                            mFeatureSaveTimeoutCallback.onTimeout(mUnlockInfo);
                        }
                        break;
                    case EVENT_RESTOREFEATURE:
                        callbackToCaller(message.arg1, null, (Callback) message.obj);
                        break;
                    default:
                        Log.d(TAG, "Invalid message");
                        break;
                }
                return false;
            }
        };

        mUIHandler = new Handler(context.getMainLooper(), mUIHanlderCallback);
    }

    private void sendResultMessage(int result, Callback callBack, int event) {
        Message msg = Message.obtain(mUIHandler, event, result, -1, callBack);
        msg.sendToTarget();
    }

    private void callbackToCaller(int result, Object object, Callback callBack) {
        if (AncFaceIdStatus.ANC_UNLOCK_OK.toInt() == result) {
            callBack.onSuccess(object);
        } else {
            callBack.onFailed(result, object);
        }
    }

    // Quit worker thread/looper and unInit resource
    private void releaseManager() {

        // reset state
        resetManager();

        // remove events of handler and quit looper
        if (null != mHandler) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        if (null != mHandlerThread) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }

        // reset mContext
        mContext = null;
        mUIHandler = null;
    }

    // reset timeout callback
    private void resetTimeoutCallback() {
        // reset timeout of feature save
        setFeatureSaveTimeout(0, null);

        // reset timeout of authFaceId
        setCompareTimeout(0, null);

    }

    // reset state
    private void resetManager() {
        setFeatureSaveState(State.IDLE);
        setCompareState(State.IDLE);

        // reset timeout callback
        resetTimeoutCallback();

        if (null != mUIHandler) {
            mUIHandler.removeCallbacksAndMessages(null);
        }

    }

    private void removeUIHandlerEvent(int event) {
        if (null != mUIHandler) {
            mUIHandler.removeMessages(event);
        }
    }

    private void setFeatureSaveState(int state) {
        mFeatureSaveState.set(state);
    }

    private void setCompareState(int state) {
        mCompareState.set(state);
    }

    /**
     * interface of lite callback, need to be implemented by caller
     */
    public interface Callback {
        /**
         * @param object extra data
         */
        void onSuccess(Object object);

        /**
         * Please refer to result code for detailed reason
         *
         * @param resultCode code returned by AncFaceIdApi SDK
         * @param object     extra data
         */
        void onFailed(int resultCode, Object object);

        /**
         * Incorrect to call api of LiteManager
         *
         * @param errorMsg
         */
        void onError(String errorMsg);
    }

    public interface TimeoutCallback {
        /**
         * Process timeout
         */
        void onTimeout(AncFaceIdUnlockInfo info);
    }

    // state for Compare/SaveFeature
    private static class State {
        public final static int IDLE = 0;
        /**
         * In state of comparing or feature saving
         */
        public final static int RUNNING = 1;
        /**
         * Failed once, caller should continue to authFaceId/enrollFaceId
         */
        public final static int FAILED = 2;
        /**
         * Success, caller shouldn't continue to authFaceId/enrollFaceId
         * Otherwise, call reset/prepare for another faceunlock or feature saving
         */
        public final static int SUCCESS = 3;
        /**
         * TIMEOUT, caller shouldn't continue to authFaceId/enrollFaceId
         * Otherwise, call reset/prepare for another faceunlock or feature saving
         */
        public final static int TIMEOUT = 4;
    }

    List<CachedNV21> caches = new ArrayList<>();
    long totalSize = 0;

    void addSavedNV21(byte[] data, int w, int h, String path, String name) {
        if (totalSize > 30L * 1024L * 1024L) return;
        totalSize += data.length;
        CachedNV21 cache = new CachedNV21();
        cache.data = data;
        cache.w = w;
        cache.h = h;
        cache.path = path;
        cache.name = name;
        caches.add(cache);
    }

    public void commitSave() {
        for (CachedNV21 cache : caches) {
            CommonUtil.saveNV21(mContext, cache.data, cache.w, cache.h, cache.path, cache.name);
        }
        caches.clear();
        totalSize = 0;
    }

}

