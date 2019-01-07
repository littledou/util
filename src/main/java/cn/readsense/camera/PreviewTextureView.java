package cn.readsense.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;

import cn.sense.icount.github.util.DeviceUtils;

public class PreviewTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    CameraController cameraController;
    Context context;
    SurfaceTexture surfaceTexture;
    int preview_width;
    int preview_height;

    public PreviewTextureView(Context context, CameraController cameraController, int PREVIEW_WIDTH, int PREVIEW_HEIGHT) {
        super(context);
        this.cameraController = cameraController;
        this.context = context;
        setSurfaceTextureListener(this);
        preview_width = PREVIEW_WIDTH;
        preview_height = PREVIEW_HEIGHT;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        surfaceTexture = surface;
        cameraController.setPreviewTexture(surface);
        cameraController.startPreview();
        configureTransform(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        cameraController.stopPreview();
        cameraController.releaseCamera();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = (Activity) context;
        if (null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, preview_height, preview_width);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        final String model = DeviceUtils.getModel();

        if (model.equals("rk3399-mid")) {
            bufferRect = new RectF(0, 0, preview_width, preview_height);
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / preview_height,
                    (float) viewWidth / preview_width);
            matrix.postScale(scale, scale, centerX, centerY);
        } else {
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / preview_height,
                        (float) viewWidth / preview_width);
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }

        }
        setTransform(matrix);
    }
}
