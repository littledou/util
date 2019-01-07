package cn.readsense.camera.glyuv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class RenderYUVGLSurfaceView extends GLSurfaceView {


    private Context context;
    GLFrameRender glFrameRender;

    public RenderYUVGLSurfaceView(Context context) {
        this(context, null);
    }

    public RenderYUVGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    private void init() {
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setEGLContextClientVersion(2);

        glFrameRender = new GLFrameRender(this);
        setRenderer(glFrameRender);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }


    public void drawFrame(int w, int h, byte[] y, byte[] u, byte[] v) {
        glFrameRender.update(w, h);
        glFrameRender.update(y, u, v);
    }

}
