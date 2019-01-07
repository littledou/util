package cn.readsense.controller.camera;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import cn.readsense.controller.BaseController;
import cn.readsense.exception.CameraDisabledException;
import cn.readsense.exception.NoCameraException;

public interface ICameraController extends BaseController {


    void openCamera(int cameraFacing) throws NoCameraException;

    void setParamPreviewSize(int width, int height);

    void setParamEnd();

    void setDisplayOrientation(Context context, int result);

    void setDisplayOrientation(Context context);

    void setPreviewDisplay(SurfaceHolder holder);

    void addPreviewCallback(Camera.PreviewCallback callback);

    void removePreviewCallback();

    void addPreviewCallbackWithBuffer(Camera.PreviewCallback callback);

    void removePreviewCallbackWithBuffer();

    void startPreview();

    void stopPreview();

    void releaseCamera();

    void hasCameraDevice(Context ctx) throws CameraDisabledException, NoCameraException;

    boolean hasCameraFacing(int facing);

    boolean hasSupportSize(int width, int height);

    void printSupportPreviewSize();

    void printSupportPictureSize();

    Camera.Size getOptimalPreviewSize(int width, int height);
}
