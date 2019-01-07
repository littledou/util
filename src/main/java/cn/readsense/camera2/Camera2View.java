package cn.readsense.camera2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.RelativeLayout;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import cn.sense.icount.github.util.DeviceUtils;

@RequiresApi (api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2View extends RelativeLayout {
    private static final String TAG = "Camera2View";

    private Context context;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;


    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private TextureView mTextureView;
    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;


    private int PREVIEWWIDTH;
    private int PREVIEWHEIGHT;
    private String FACING;
    private int camera_facing;


    int data_real_width = 0;
    int data_real_height = 0;

    public Camera2View(Context context) {
        this(context, null);
    }

    public Camera2View(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        mTextureView = new TextureView(context);
        addView(mTextureView);
    }

    public void showCameraView(int width, int height, int camera_facing) {
        PREVIEWWIDTH = width;
        PREVIEWHEIGHT = height;

        mPreviewSize = new Size(PREVIEWWIDTH, PREVIEWHEIGHT);

        this.camera_facing = camera_facing;
        if (!Camera2Util.hasFacing(context, camera_facing))
            throw new Error("device not found any camera device!");
        if (!Camera2Util.hasPreviewSize(context, width, height, camera_facing))
            throw new Error("device not found previewSize: " + width + "*" + height);
    }

    public void pauseCamera2() {
        closeCamera();
        stopBackgroundThread();
    }

    public void resumeCamera2() {
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }


    private void openCamera(int width, int height) {
        configureTransform(width, height);
        setUpCameraOutputs();
        Activity activity = (Activity) context;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw new Error("CAMERA permission not granted");
            }
            assert manager != null;
            manager.openCamera(FACING, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }


    private void setUpCameraOutputs() {
        Activity activity = (Activity) context;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            if (manager != null) {
                for (String cameraId : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == camera_facing) {
                        FACING = cameraId;
                        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                        if (map == null) {
                            throw new Error("Camera StreamConfigurationMap is null, no prewsize");
                        }

                        mImageReader = ImageReader.newInstance(PREVIEWWIDTH, PREVIEWHEIGHT,
                                PixelFormat.RGBA_8888, /*maxImages*/2);
                        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                Log.d(TAG, "onImageAvailable frameRate: " + frameRate);

                                if (System.currentTimeMillis() - time > 1000) {
                                    frameRate = frameCount;
                                    frameCount = 0;
                                    time = System.currentTimeMillis();
                                }
                                frameCount++;

                                Image image = null;
                                try {
                                    image = reader.acquireLatestImage();
                                    Image.Plane[] planes = image.getPlanes();
                                    int width = image.getWidth();//设置的宽
                                    int height = image.getHeight();//设置的高
                                    int pixelStride = planes[0].getPixelStride();//像素个数，RGBA为4
                                    int rowStride = planes[0].getRowStride();//这里除pixelStride就是真实宽度
                                    int rowPadding = rowStride - pixelStride * width;//计算多余宽度

                                    byte[] data = new byte[rowStride * height];//创建byte
                                    ByteBuffer buffer = planes[0].getBuffer();//获得buffer
                                    buffer.get(data);//将buffer数据写入byte中

                                    //到这里为止就拿到了图像数据，你可以转换为yuv420，或者录制成H264

                                    //这里我提供一段转换为Bitmap的代码

                                    //这是最终数据，通过循环将内存对齐多余的部分删除掉
                                    // 正常ARGB的数据应该是width*height*4，但是因为是int所以不用乘4
                                    int[] pixelData = new int[width * height];

                                    int offset = 0;
                                    int index = 0;
                                    for (int i = 0; i < height; ++i) {
                                        for (int j = 0; j < width; ++j) {
                                            int pixel = 0;
                                            pixel |= (data[offset] & 0xff) << 16;     // R
                                            pixel |= (data[offset + 1] & 0xff) << 8;  // G
                                            pixel |= (data[offset + 2] & 0xff);       // B
                                            pixel |= (data[offset + 3] & 0xff) << 24; // A
                                            pixelData[index++] = pixel;
                                            offset += pixelStride;
                                        }
                                        offset += rowPadding;
                                    }

//                                    Bitmap bitmap = Bitmap.createBitmap(pixelData,
//                                            width, height,
//                                            Bitmap.Config.ARGB_8888);//创建bitmap

//                                    BitmapUtil.saveBitmap(bitmap, "/sdcard/test.jpg");

//                                    final Image.Plane[] planes = image.getPlanes();
//                                    Camera2Util.fillBytes(planes, yuvBytes);
//                                    yRowStride = planes[0].getRowStride();
//                                    final int uvRowStride = planes[1].getRowStride();
//                                    final int uvPixelStride = planes[1].getPixelStride();
//
//                                    final int iw = PREVIEWWIDTH;
//                                    final int ih = PREVIEWHEIGHT;
//                                    byte[] y = yuvBytes[0];
//                                    byte[] u = yuvBytes[1];
//                                    byte[] v = yuvBytes[2];
//
//                                    byte[] yuv = new byte[iw * ih * 2];
//
//                                    System.arraycopy(y, 0, yuv, 0, iw * ih);
//                                    for (int i = 0; i < iw * ih / 4; i++) {
//                                        yuv[iw * ih + i * 2] = v[i];
//                                        yuv[iw * ih + i * 2 + 1] = u[i];
//                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    if (image != null)
                                        image.close();
                                }


                            }
                        }, mBackgroundHandler);
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    int frameCount = 0;
    int frameRate = 0;
    long time = 0;


    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = (Activity) context;
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        final String model = DeviceUtils.getModel();

        data_real_width = viewWidth;
        data_real_height = viewHeight;

        if (model.equals("rk3399-mid")) {
            bufferRect = new RectF(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
        } else {
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / mPreviewSize.getHeight(),
                        (float) viewWidth / mPreviewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }

        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = (Activity) context;
            if (null != activity) {
                activity.finish();
            }
        }

    };


    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground-" + System.currentTimeMillis());
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

}
