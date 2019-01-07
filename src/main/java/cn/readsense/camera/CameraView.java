package cn.readsense.camera;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.Locale;

import cn.readsense.exception.CameraDisabledException;
import cn.readsense.exception.CameraNotSupportException;
import cn.readsense.exception.NoCameraException;
import cn.sense.icount.github.util.DLog;


/**
 * Created by dou on 2017/11/7.
 */

public class CameraView extends RelativeLayout {

    private static final String TAG = "CameraView";
    private static final int MAIN_RET = 0x101;
    private static final int THREAD_RET = 0x102;
    private Context context;
    private int oritationDisplay = -1;

    private int PREVIEW_WIDTH;
    private int PREVIEW_HEIGHT;
    private int FACING;

    private byte buffer[];
    private byte temp[];
    private boolean isBufferready = false;
    private boolean is_thread_run = true;
    private final Object Lock = new Object();
    private PreviewFrameCallback previewFrameCallback;
    CameraController cameraController;

    HandlerThread myHandlerThread;
    Handler handler;
    Handler handler_main;

    private SurfaceView draw_view;
    private PreviewSurfaceView previewSurfaceView;
    private PreviewTextureView previewTextureView;

    private Paint paint;


    public SurfaceView getDrawView() {
        return draw_view;
    }

    public void setDrawView() {
        is_thread_run = true;
        draw_view = new SurfaceView(context);
        draw_view.setZOrderOnTop(true);
        draw_view.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    public CameraView(Context context) {
        this(context, null);
    }


    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setBackgroundColor(Color.BLACK);
        cameraController = new CameraController();
        DLog.d("CameraView init");
    }


    int vw, vh;

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        vw = w;
        vh = h;

    }

    public void showCameraView(int width, int height, int facing) {

        try {
            cameraController.hasCameraDevice(context);

//            if (cameraController.hasCameraFacing(facing)) {
            PREVIEW_WIDTH = width;
            PREVIEW_HEIGHT = height;
            FACING = facing;

            setUpCamera();
//            } else {
//                Toast.makeText(context, String.format(Locale.CHINA, "device not found camera device, CamreaId: %d!", facing), Toast.LENGTH_SHORT).show();
//            }


        } catch (CameraNotSupportException e) {
            e.printStackTrace();
        } catch (NoCameraException e) {
            e.printStackTrace();
        } catch (CameraDisabledException e) {
            e.printStackTrace();
        }

    }

    int frameCount = 0;
    int frameRate = 0;
    long time = 0;

    private void setUpCamera() throws CameraNotSupportException {

        settingTextureView();

        try {

            cameraController.openCamera(FACING);

            Camera.Size prewSize = cameraController.getOptimalPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            if (prewSize != null) {
                PREVIEW_WIDTH = prewSize.width;
                PREVIEW_HEIGHT = prewSize.height;
                cameraController.setParamPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                try {
                    cameraController.setDisplayOrientation(context, oritationDisplay);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (previewFrameCallback != null)
                    cameraController.addPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {//数据预览回掉
                            Log.d("onPreviewFrame", "onPreviewFrame frameRate: " + frameRate);

                            if (System.currentTimeMillis() - time > 1000) {
                                frameRate = frameCount;
                                frameCount = 0;
                                time = System.currentTimeMillis();
                            }
                            frameCount++;
                            camera.addCallbackBuffer(data);
                            synchronized (Lock) {
                                System.arraycopy(data, 0, buffer, 0, data.length);
                                isBufferready = true;
                            }
                        }
                    });

                cameraController.setParamEnd();
            }

        } catch (Exception e) {

            Toast.makeText(context, String.format(Locale.CHINA, "open camera failed, CamreaId: %d!", FACING), Toast.LENGTH_SHORT).show();
            throw new CameraNotSupportException();
        }
    }


    private void settingSurfaceView() {
        if (getChildCount() != 0) removeAllViews();
        addCallback();
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        previewSurfaceView = new PreviewSurfaceView(context, cameraController);
        addView(previewSurfaceView, params);

        if (draw_view != null) {
            params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            addView(draw_view, params);
        }
    }

    private void settingTextureView() {
        if (getChildCount() != 0) removeAllViews();
        addCallback();
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        previewTextureView = new PreviewTextureView(context, cameraController, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        addView(previewTextureView, params);


        if (draw_view != null) {
            params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            addView(draw_view, params);
        }
    }

    private void addCallback() {

        if (previewFrameCallback != null) {
            buffer = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
            temp = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];

            handler_main = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (msg.what == MAIN_RET) {
                        if (previewFrameCallback != null)
                            previewFrameCallback.analyseDataEnd(msg.obj);
                    }
                }
            };

            //run data analyse
            myHandlerThread = new HandlerThread("handler-thread-" + System.currentTimeMillis());
            //开启一个线程
            myHandlerThread.start();
            //在这个线程中创建一个handler对象
            handler = new Handler(myHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (msg.what == THREAD_RET) {
                        //这个方法是运行在 handler-thread 线程中的 ，可以执行耗时操作
                        while (is_thread_run) {

                            if (!isBufferready) {
//                                try {
//                                    Thread.sleep(28);
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
                                continue;
                            }
                            synchronized (Lock) {
                                System.arraycopy(buffer, 0, temp, 0, buffer.length);
                                isBufferready = false;
                            }
                            if (previewFrameCallback != null) {
                                Object o = previewFrameCallback.analyseData(temp);
                                Message msg1 = new Message();
                                msg1.what = MAIN_RET;
                                msg1.obj = o;
                                handler_main.sendMessage(msg1);
                            }
                        }
                    }
                }
            };
            handler.sendEmptyMessage(THREAD_RET);

        }


    }

    public void releaseCamera() {
        is_thread_run = false;
        previewFrameCallback = null;
        if (handler != null)
            handler.removeMessages(0);
        if (handler_main != null)
            handler_main.removeMessages(0);
        if (myHandlerThread != null)
            myHandlerThread.quitSafely();
        cameraController.releaseCamera();
    }

    private void removeDataCallback() {
        this.previewFrameCallback = null;
        cameraController.removePreviewCallbackWithBuffer();
    }

    public void addPreviewFrameCallback(PreviewFrameCallback callback) {
        this.previewFrameCallback = callback;
    }

    public interface PreviewFrameCallback {
        Object analyseData(byte[] data);

        void analyseDataEnd(Object t);
    }

    public int moveCameraFacing() {
        removeDataCallback();
        FACING = (FACING == Camera.CameraInfo.CAMERA_FACING_BACK) ?
                Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

        cameraController.removePreviewCallbackWithBuffer();
        cameraController.stopPreview();
        cameraController.releaseCamera();
        removeAllViews();
        showCameraView(PREVIEW_WIDTH, PREVIEW_HEIGHT, FACING);
        return FACING;
    }

    public void setOritationDisplay(int oritationDisplay) {
        this.oritationDisplay = oritationDisplay;
    }
}
