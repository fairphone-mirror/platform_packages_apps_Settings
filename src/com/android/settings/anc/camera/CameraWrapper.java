package com.android.settings.anc.camera;

import android.content.Context;
import android.view.SurfaceHolder;

import com.android.settings.anc.util.Constants;

/**
 * CameraWrapper support Camera and Camera2(SDK_INT >= LOLLIPOP).
 * For improving performance of starting Camera, open Camera async.
 *
 * CameraWrapper encapsulate all apis of using Camera as listed below. The steps of using Camera:
 *
 * 1.openCamera:
 *   call openCamera async and get result by callback.
 *
 * 2.setDisplayOrientation:
 *   call setDisplayOrientation for right display. If failed to open camera, the new orientation can't
 *   be set successfully.
 *
 * 3.startPreview:
 *   call startPreview for previewing frame data once SurfaceHolder is created completely.
 *   If failed to open camera, this action of starting Preview is invalid.
 *
 * 4.startDetect:
 *   call startDetect for processing frame data
 *
 * 5.stopDetect:
 *   call stopDetect for stopping frame data. You can call startDetect again for continuing to process.
 *
 * 6.stopPreview:
 *   call stopPreview for stopping previewing. You can call startPreview again for continuing to preview
 *
 * 7.closeCamera:
 *   call closeCamera to release related resource of Camera and thread.
 *   You must call this function for avoiding resource leak.
 */

public abstract class CameraWrapper {

    /**
     * default params of preview
     */
    protected int mWidth = Constants.FRAME_IMAGE_WIDTH;
    protected int mHeight = Constants.FRAME_IMAGE_HEIGHT;
    protected int mAngle = Constants.ORIENTATION_90;

    /**
     * Open Camera
     */
    public void openCamera(boolean isBackCamera, Context context,
                           CameraOpenCallback callback) {}

    /**
     * Start preview
     * @param surfaceHolder
     */
    public void startPreview(SurfaceHolder surfaceHolder) {}

    /**
     * Set Orientation of Display
     * @param angle 0~360
     */
    public void setDisplayOrientation(int angle) {}

    /**
     * Begin to detect and receive frame data of preview
     * @param callback receive frame data of preview
     */
    public void startDetect(IPreviewCallback callback) {}

    /**
     * Stop detecting
     */
    public void stopDetect() {}

    /**
     * Stop preview
     */
    public void stopPreview() {}


    /**
     * Close camera
     */
    public void closeCamera() {}

    /**
     * get width
     * @return
     */
    public int getWidth() { return mWidth; }

    /**
     * get Angle
     * @return
     */
    public int getAngle() { return mAngle; }

    /**
     * get height
     * @return
     */
    public int getHeight() { return mHeight; }

    /**
     * callback for frame data of preview
     */
    public interface IPreviewCallback {
        void onPreviewFrame(byte[] bytes);
    }

    /**
     * Result of opening Camera
     */
    public static class Result {
        public final static int RESULT_SUCCESS = 1;
        public final static int RESULT_DISCONNECTED = 2;
        public final static int RESULT_FAILED = 3;
    }
    /**
     * callback result for opening camera
     */
    public interface CameraOpenCallback {
        /**
         * open successfully
         */
        void onOpenSuccess();

        /**
         * Camera is disconnected
         */
        void onDisconnected();

        /**
         * Failed to open Camera
         */
        void onOpenFailed();
    }

}

