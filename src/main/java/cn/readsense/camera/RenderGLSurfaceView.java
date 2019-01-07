package cn.readsense.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.sense.icount.github.util.DLog;

public class RenderGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private CameraDrawer cameraDrawer;
    private CameraController cameraController;
    private Context context;

    private int view_width = 0;

    private int view_height = 0;

    public RenderGLSurfaceView(Context context) {
        this(context, null);
    }

    public RenderGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public void setCameraController(CameraController cameraController) {
        this.cameraController = cameraController;
        init();
    }

    private void init() {
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setEGLContextClientVersion(2);
        cameraDrawer = new CameraDrawer(context.getResources());
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        SurfaceHolder surfaceHolder = getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        cameraController.stopPreview();
        cameraController.releaseCamera();
        DLog.d("surfaceDestroyed");
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        cameraDrawer.onSurfaceCreated(gl, config);
        cameraDrawer.setDataSize(cameraController.getPreview_width(), cameraController.getPreview_height());
        cameraDrawer.setCameraId(cameraController.getFacing());

        cameraDrawer.getSurfaceTexture().setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                requestRender();
            }
        });

        cameraController.setPreviewTexture(cameraDrawer.getSurfaceTexture());

        cameraController.startPreview();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        view_width = width;
        view_height = height;
        cameraDrawer.onSurfaceChanged(gl, width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        cameraDrawer.onDrawFrame(gl);
        bitmapCallback(view_width, view_height);
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraController.stopPreview();
        cameraController.releaseCamera();
    }

    int[] iat;
    IntBuffer ib;
    int[] ia;
    Bitmap bitmap;

    private void bitmapCallback(int mWidth, int mHeight) {

        GLES20.glViewport(0, 0, mWidth, mHeight);


        long time = System.currentTimeMillis();
        if (iat == null)
            iat = new int[mWidth * mHeight];
        if (ib == null)
            ib = IntBuffer.allocate(mWidth * mHeight);
        DLog.d("allocate cost:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();
        GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ib);
        DLog.d("glReadPixels cost:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();
        ia = ib.array();

        // Convert upside down mirror-reversed image to right-side up normal
        // image.
        for (int i = 0; i < mHeight; i++) {
            System.arraycopy(ia, i * mWidth, iat, (mHeight - i - 1) * mWidth, mWidth);
        }
        DLog.d("arraycopy cost:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();
        if (bitmap == null)
            bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        DLog.d("createBitmap cost:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(iat));
        DLog.d("copyPixelsFromBuffer cost:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();

    }

}
