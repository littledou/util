package cn.readsense.camera.effect;

import android.content.Context;

import cn.readsense.gl.GLSLFileUtils;

public class GrayEffect extends Effect {

    private static final String GRAY_EFFECT_VERTEX = "gray/vertexshader.glsl";
    private static final String GRAY_EFFECT_FRAGMENT = "gray/fragmentshader.glsl";

    public GrayEffect(Context context) {
        super();
        String vertexShader = GLSLFileUtils.getFileContextFromAssets(context, GRAY_EFFECT_VERTEX);
        String fragmentShader = GLSLFileUtils.getFileContextFromAssets(context, GRAY_EFFECT_FRAGMENT);
        super.setShader(vertexShader, fragmentShader);
    }
}
