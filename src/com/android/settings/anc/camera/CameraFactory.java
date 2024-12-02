package com.android.settings.anc.camera;


public class CameraFactory {

    public static CameraWrapper getCamera() {
        CameraWrapper cameraWrapper = new Camera2WrapperImpl();
        return cameraWrapper;
    };
}
