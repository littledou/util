package cn.readsense.camera.gles

import cn.readsense.camera.util.OpenGlUtils


class FrameFilter {

    lateinit var mDrawStuff: DrawStuff
    var mTextureStuff: TextureStuff? = null

    fun init(isRecord: Boolean) {
        mDrawStuff = DrawStuff()
        mTextureStuff = TextureStuff()
        mDrawStuff.init(isRecord)
        mTextureStuff?.init()
    }


    fun release(doEglCleanup: Boolean) {
        if (mTextureStuff != null) {
            if (doEglCleanup) {
                mTextureStuff?.release()
            }
            mTextureStuff = null
        }
    }

    fun changeTexture(textureStuff: TextureStuff) {
        mTextureStuff?.release()
        mTextureStuff = textureStuff
    }

    fun drawFrame(textureId: Int, texMatrix: FloatArray) {
        mTextureStuff?.draw(OpenGlUtils.IDENTITY_MATRIX,
                mDrawStuff.mVertexArray!!,
                0,
                mDrawStuff.mVertexCount,
                mDrawStuff.mCoordsPerVertex,
                mDrawStuff.mVertexStride,
                texMatrix,
                mDrawStuff.mTexCoordArray!!,
                textureId,
                mDrawStuff.mTexCoordStride)
    }
}