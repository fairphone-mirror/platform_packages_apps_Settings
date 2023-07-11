package com.android.settings.anc.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import com.anc.faceid.utils.AncImageHelper;

import java.util.ArrayList;
import java.util.List;

public class Camera2WrapperImpl extends CameraWrapper {
    private static final String TAG = "Camera2WrapperImpl";

    // event for Camera
    private final static int EVENT_OPEN_CAMERA = 1;
    private final static int EVENT_START_PREVIEW = 2;

    // worker thread for camera
    private Context mContext;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    // UI handler for callback
    private Handler mUIHandler;

    // camera info
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private boolean mIsBackCamera;
    private CameraOpenCallback mCameraOpenCallback;
    private CameraCaptureSession mCameraCaptureSession;


    // preview info
    private ImageReader mImageReader;
    private IPreviewCallback mPreviewCallback;
    private SurfaceHolder mSurfaceHolder;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    // Listener for frame data of preview
    private final ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            try {
                if (checkNotNull(mPreviewCallback) && image != null) {
                    byte[] yuvData = new byte[image.getHeight() * image.getWidth() * 3 / 2];
                    AncImageHelper.image2NV21(image, yuvData);
                    if(mPreviewCallback != null){
                        mPreviewCallback.onPreviewFrame(yuvData);
                    }
                }
            } catch (IllegalStateException e) {

            }
            // close
            if(image != null){
                image.close();
            }
        }
    };

    public Camera2WrapperImpl() {
    }

    /**
     * state of opening Camera
     */
    private final CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            callbackToCaller(Result.RESULT_SUCCESS);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
            callbackToCaller(Result.RESULT_DISCONNECTED);
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
            Log.d(TAG, "onError()...code:" + errorCode);
            if(mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            callbackToCaller(Result.RESULT_FAILED);
        }
    };

    /**
     * Open Camera asynchronously
     */
    @Override
    public void openCamera(boolean isBackCamera, Context context, CameraOpenCallback callback) {

        // init worker thread
        initEventLooper(context);

        // save params
        mContext = context;
        mIsBackCamera = isBackCamera;
        mCameraOpenCallback = callback;

        // obtain message
        Message msg = Message.obtain(mHandler, EVENT_OPEN_CAMERA);
        msg.sendToTarget();
    }

    /**
     * Start Preview
     *
     * @param surfaceHolder
     */
    @Override
    public void startPreview(SurfaceHolder surfaceHolder) {

        // save holder
        mSurfaceHolder = surfaceHolder;

        // Init ImageReader
        initImageReader();

        // obtain message
        Message msg = Message.obtain(mHandler, EVENT_START_PREVIEW);
        msg.sendToTarget();
    }

    /**
     * Begin to detect and receive frame data of preview
     *
     * @param callback receive frame data of preview
     */
    @Override
    public void startDetect(IPreviewCallback callback) {
        setPreviewCallback(callback);
    }

    /**
     * Stop detecting，
     */
    @Override
    public void stopDetect() {
        setPreviewCallback(null);
    }

    /**
     * Stop preview
     */
    @Override
    public void stopPreview() {
        if (checkNotNull(mCameraCaptureSession)) {
            try {
                mCameraCaptureSession.stopRepeating();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "exception when stop repeating");
            }
            mCameraCaptureSession = null;
        }

        // set Image Reader
        if (checkNotNull(mImageReader)) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /**
     * Close camera
     */
    @Override
    public void closeCamera() {
        stopPreview();

        if (checkNotNull(mCameraDevice)) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        // reset camera info
        resetPreviewInfo();

        // exit event looper
        exitEventLooper();
    }

    /**
     * Set Orientation of Display
     *
     * @param angle 0~360
     */
    @Override
    public void setDisplayOrientation(int angle) {
    }

    // init Image Reader
    private void initImageReader() {
        mImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YUV_420_888, 3);
        mImageReader.setOnImageAvailableListener(mImageAvailableListener, mHandler);
    }

    // init event thread
    private void initEventLooper(Context context) {
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), msg -> {
            switch (msg.what) {
                case EVENT_OPEN_CAMERA:
                    handleOpenCamera();
                    break;
                case EVENT_START_PREVIEW:
                    handleStartPreview();
                    break;
                default:
                    Log.d(TAG, "Unsupport event type");
                    break;
            }
            return false;
        });

        mUIHandler = new Handler(context.getMainLooper());
    }

    private void exitEventLooper() {
        if (checkNotNull(mUIHandler)) {
            mUIHandler.removeCallbacksAndMessages(null);
        }
        if (checkNotNull(mHandler)) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (checkNotNull(mHandlerThread)) {
            mHandlerThread.quit();
        }
        mHandlerThread = null;
        mHandler = null;
        mUIHandler = null;
    }

    /**
     * Open Camera
     */
    // handle for opening Camera
    private void handleOpenCamera() {

        mCameraManager = (CameraManager) mContext.getSystemService(Activity.CAMERA_SERVICE);
        if (!checkNotNull(mCameraManager)) {
            callbackToCaller(Result.RESULT_FAILED);
            return;
        }

        // select back or front camera
        mCameraId = selectBackOrFrontCamera();

        if (!checkNotNull(mCameraId)) {
            Log.e(TAG, "Camera id is invalid, return!");
            callbackToCaller(Result.RESULT_FAILED);
            return;
        }

        // Open Camera
        try {
            mCameraManager.openCamera(mCameraId, mCameraStateCallback, mHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open Camera");
            e.printStackTrace();
            callbackToCaller(Result.RESULT_FAILED);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to open Camera");
            e.printStackTrace();
            callbackToCaller(Result.RESULT_FAILED);
        }
    }

    /**
     * Start Preview
     */
    private void handleStartPreview() {

        // check if Camera already opened
        if (!checkNotNull(mCameraDevice)) {
            Log.e(TAG, "Invalid Camera device");
            return;
        }

        // Camera already opened and go to preview
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(mImageReader.getSurface());
        if (mSurfaceHolder != null) {
            surfaces.add(mSurfaceHolder.getSurface());
        }

        try {
            // create Capture request
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mImageReader.getSurface());
            if (mSurfaceHolder != null) {
                builder.addTarget(mSurfaceHolder.getSurface());
            }

            // create Capture Session
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (!checkNotNull(mCameraDevice)) {
                        return;
                    }
                    try {
                        // auto focus
                        builder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        mCameraCaptureSession = session;

                        // send request
                        session.setRepeatingRequest(builder.build(), null, mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "on configure failed");
                }
            }, mHandler);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to start preview!");
        }
    }

    private void setPreviewCallback(final IPreviewCallback previewCallback) {
        mPreviewCallback = previewCallback;
    }

    private void callbackToCaller(final int result) {
        if (checkNotNull(mUIHandler)) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (checkNotNull(mCameraOpenCallback)) {
                        switch (result) {
                            case Result.RESULT_SUCCESS:
                                mCameraOpenCallback.onOpenSuccess();
                                break;
                            case Result.RESULT_DISCONNECTED:
                                mCameraOpenCallback.onDisconnected();
                                break;
                            case Result.RESULT_FAILED:
                                mCameraOpenCallback.onOpenFailed();
                                break;
                            default:
                                break;
                        }
                    }
                }
            });
        }
    }

    // check if Camera is null
    private boolean checkNotNull(Object object) {
        return (null != object);
    }

    // select back or front Camera
    private String selectBackOrFrontCamera() {
        String cameraId = null;

        try {
            // get id list
            String[] idList = mCameraManager.getCameraIdList();
            for (String id : idList) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (mIsBackCamera && // back camera
                            CameraCharacteristics.LENS_FACING_BACK == facing) {
                        cameraId = id;
                    } else if (CameraCharacteristics.LENS_FACING_FRONT == facing) {
                        cameraId = id;
                        break;
                    }
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Invalid Camera Id");
        }

        return cameraId;
    }

    // reset preview info
    private void resetPreviewInfo() {
        mCameraId = null;
        mSurfaceHolder = null;
        mCameraOpenCallback = null;

        mPreviewCallback = null;
        mIsBackCamera = false;
    }
}

