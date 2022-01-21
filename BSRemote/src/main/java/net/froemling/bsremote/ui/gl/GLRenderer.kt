package net.froemling.bsremote.ui.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import net.froemling.bsremote.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer internal constructor(private val context: Context) : GLSurfaceView.Renderer {
    private val mMVPMatrix = FloatArray(16)
    private val mProjMatrix = FloatArray(16)
    private val mVMatrix = FloatArray(16)

    @JvmField
    @Volatile
    var quitButtonX = 0f

    @JvmField
    @Volatile
    var quitButtonY = 0f

    @JvmField
    @Volatile
    var quitButtonWidth = 0f

    @JvmField
    @Volatile
    var quitButtonHeight = 0f

    @JvmField
    @Volatile
    var quitButtonPressed = false

    @JvmField
    @Volatile
    var prefsButtonX = 0f

    @JvmField
    @Volatile
    var prefsButtonY = 0f

    @JvmField
    @Volatile
    var prefsButtonWidth = 0f

    @JvmField
    @Volatile
    var prefsButtonHeight = 0f

    @JvmField
    @Volatile
    var prefsButtonPressed = false

    @JvmField
    @Volatile
    var startButtonX = 0f

    @JvmField
    @Volatile
    var startButtonY = 0f

    @JvmField
    @Volatile
    var startButtonWidth = 0f

    @JvmField
    @Volatile
    var startButtonHeight = 0f

    @JvmField
    @Volatile
    var startButtonPressed = false

    @JvmField
    @Volatile
    var bombButtonX = 0f

    @JvmField
    @Volatile
    var bombButtonY = 0f

    @JvmField
    @Volatile
    var bombButtonWidth = 0f

    @JvmField
    @Volatile
    var bombButtonHeight = 0f

    @JvmField
    @Volatile
    var punchButtonX = 0f

    @JvmField
    @Volatile
    var punchButtonY = 0f

    @JvmField
    @Volatile
    var punchButtonWidth = 0f

    @JvmField
    @Volatile
    var punchButtonHeight = 0f

    @JvmField
    @Volatile
    var throwButtonX = 0f

    @JvmField
    @Volatile
    var throwButtonY = 0f

    @JvmField
    @Volatile
    var throwButtonWidth = 0f

    @JvmField
    @Volatile
    var throwButtonHeight = 0f

    @JvmField
    @Volatile
    var jumpButtonX = 0f

    @JvmField
    @Volatile
    var jumpButtonY = 0f

    @JvmField
    @Volatile
    var jumpButtonWidth = 0f

    @JvmField
    @Volatile
    var jumpButtonHeight = 0f

    @JvmField
    @Volatile
    var joystickCenterX = 0f

    @JvmField
    @Volatile
    var joystickCenterY = 0f

    @JvmField
    @Volatile
    var joystickWidth = 0f

    @JvmField
    @Volatile
    var joystickHeight = 0f

    @JvmField
    @Volatile
    var joystickX = 0f

    @JvmField
    @Volatile
    var joystickY = 0f

    @JvmField
    @Volatile
    var jumpPressed = false

    @JvmField
    @Volatile
    var punchPressed = false

    @JvmField
    @Volatile
    var throwPressed = false

    @JvmField
    @Volatile
    var bombPressed = false

    @JvmField
    @Volatile
    var thumbPressed = false
    private var mSquare: Square? = null
    private var mSquareTex: SquareTex? = null
    private var bgTex = 0
    private var buttonBombTex = 0
    private var buttonBombPressedTex = 0
    private var buttonJumpTex = 0
    private var buttonJumpPressedTex = 0
    private var buttonPunchTex = 0
    private var buttonPunchPressedTex = 0
    private var buttonThrowTex = 0
    private var buttonThrowPressedTex = 0
    private var centerTex = 0
    private var thumbTex = 0
    private var thumbPressedTex = 0
    private var buttonLeaveTex = 0
    private var buttonSettingsTex = 0
    private var buttonStartTex = 0
    private var ratio = 1.0f

    // Will load a texture out of a drawable resource file, and return an
    // OpenGL texture ID:
    private fun loadTexture(resource: Int): Int {

        // In which ID will we be storing this texture?
        val id = newTextureID()

        // We need to flip the textures vertically:
        val flip = Matrix()
        flip.postScale(1f, -1f)

        // This will tell the BitmapFactory to not scale based on the device's
        // pixel density:
        // (Thanks to Matthew Marshall for this bit)
        val opts = BitmapFactory.Options()
        opts.inScaled = false

        // Load up, and flip the texture:
        val temp = BitmapFactory.decodeResource(context.resources, resource, opts)
        var bmp = Bitmap.createBitmap(temp, 0, 0, temp.width, temp.height, flip, true)
        temp.recycle()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)

        // Set all of our texture parameters:
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_REPEAT.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_REPEAT.toFloat())

        // Generate, and load up all of the mipmaps:
        var level = 0
        var height = bmp.height
        var width = bmp.width
        while (true) {

            // Push the bitmap onto the GPU:
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, level, bmp, 0)

            // We need to stop when the texture is 1x1:
            if (height == 1 && width == 1) {
                break
            }

            // Resize, and let's go again:
            width = width shr 1
            height = height shr 1
            if (width < 1) {
                width = 1
            }
            if (height < 1) {
                height = 1
            }
            val bmp2 = Bitmap.createScaledBitmap(bmp, width, height, true)
            bmp.recycle()
            bmp = bmp2
            level++
        }
        bmp.recycle()
        return id
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {

        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        mSquare = Square()
        mSquareTex = SquareTex()

        // load our textures
        bgTex = loadTexture(R.drawable.controllerbg)
        checkGlError("loadTexture")
        buttonBombTex = loadTexture(R.drawable.button_bomb)
        checkGlError("loadTexture")
        buttonBombPressedTex = loadTexture(R.drawable.button_bomb_pressed)
        checkGlError("loadTexture")
        buttonJumpTex = loadTexture(R.drawable.button_jump)
        checkGlError("loadTexture")
        buttonJumpPressedTex = loadTexture(R.drawable.button_jump_pressed)
        checkGlError("loadTexture")
        buttonPunchTex = loadTexture(R.drawable.button_punch)
        checkGlError("loadTexture")
        buttonPunchPressedTex = loadTexture(R.drawable.button_punch_pressed)
        checkGlError("loadTexture")
        buttonThrowTex = loadTexture(R.drawable.button_throw)
        checkGlError("loadTexture")
        buttonThrowPressedTex = loadTexture(R.drawable.button_throw_pressed)
        checkGlError("loadTexture")
        centerTex = loadTexture(R.drawable.center)
        checkGlError("loadTexture")
        thumbTex = loadTexture(R.drawable.thumb)
        checkGlError("loadTexture")
        thumbPressedTex = loadTexture(R.drawable.thumb_pressed)
        checkGlError("loadTexture")
        buttonStartTex = loadTexture(R.drawable.button_start)
        checkGlError("loadTexture")
        buttonLeaveTex = loadTexture(R.drawable.button_leave)
        checkGlError("loadTexture")
        buttonSettingsTex = loadTexture(R.drawable.button_settings)
        checkGlError("loadTexture")
    }

    private fun drawBG(tex: Int) {
        val m = FloatArray(16)
        android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.scaleM(m, 0, -2.0f, 2.0f, 1.0f)
        if (tex == -1) {
            mSquare!!.draw(m, 1.toFloat(), 1.toFloat(), 1.toFloat())
        } else {
            mSquareTex!!.draw(m, 1.toFloat(), 1.toFloat(), 1.toFloat(), bgTex)
        }
        checkGlError("draw")
    }

    private fun drawBox(
        x: Float,
        y: Float,
        sx: Float,
        sy: Float,
        r: Float,
        g: Float,
        b: Float,
        tex: Int,
    ) {
        // scale and translate
        val m = FloatArray(16)
        android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.scaleM(m, 0, 2.0f * sx, 2.0f * sy, 1.0f)
        m[3] += 1.0f - 2.0f * x
        m[7] += 1.0f / ratio - 2.0f * y
        val m2 = FloatArray(16)
        android.opengl.Matrix.multiplyMM(m2, 0, m, 0, mMVPMatrix, 0)
        if (tex == -1) {
            mSquare!!.draw(m2, r, g, b)
        } else {
            mSquareTex!!.draw(m2, r, g, b, tex)
        }
        checkGlError("draw")
    }

    override fun onDrawFrame(unused: GL10) {

        // Draw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Set the camera position (View matrix)
        android.opengl.Matrix.setLookAtM(mVMatrix, 0, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // Calculate the projection and view transformation
        android.opengl.Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
        drawBG(bgTex)

        // actual graphics
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val bs = 2.8f
        drawBox(
            bombButtonX,
            bombButtonY,
            bombButtonWidth * bs,
            bombButtonHeight * bs, 1f, 1f, 1f,
            if (bombPressed) buttonBombPressedTex else buttonBombTex)
        drawBox(
            punchButtonX,
            punchButtonY,
            punchButtonWidth * bs,
            punchButtonHeight * bs, 1f, 1f, 1f,
            if (punchPressed) buttonPunchPressedTex else buttonPunchTex)
        drawBox(
            jumpButtonX,
            jumpButtonY,
            jumpButtonWidth * bs,
            jumpButtonHeight * bs, 1f, 1f, 1f,
            if (jumpPressed) buttonJumpPressedTex else buttonJumpTex)
        drawBox(
            throwButtonX,
            throwButtonY,
            throwButtonWidth * bs,
            throwButtonHeight * bs, 1f, 1f, 1f,
            if (throwPressed) buttonThrowPressedTex else buttonThrowTex)
        val cs = 2.2f
        drawBox(
            joystickCenterX,
            joystickCenterY,
            joystickWidth * cs,
            joystickHeight * cs, 1f, 1f, 1f,
            centerTex)
        val ts = 0.9f
        drawBox(
            joystickX,
            joystickY,
            joystickWidth * ts,
            joystickHeight * ts, 1f, 1f, 1f,
            if (thumbPressed) thumbPressedTex else thumbTex)
        val tbsx = 1.1f
        val tbsy = 1.6f
        val tboy = 0.15f * quitButtonHeight * tbsy
        var b: Float
        b = if (quitButtonPressed) 2.0f else 1.0f
        drawBox(
            quitButtonX,
            quitButtonY + tboy,
            quitButtonWidth * tbsx,
            quitButtonHeight * tbsy,
            b,
            b,
            b,
            buttonLeaveTex)
        b = if (prefsButtonPressed) 2.0f else 1.0f
        drawBox(
            prefsButtonX,
            prefsButtonY + tboy,
            prefsButtonWidth * tbsx,
            prefsButtonHeight * tbsy,
            b,
            b,
            b,
            buttonSettingsTex)
        b = if (startButtonPressed) 2.0f else 1.0f
        drawBox(
            startButtonX,
            startButtonY + tboy,
            startButtonWidth * tbsx,
            startButtonHeight * tbsy,
            b,
            b,
            b,
            buttonStartTex)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        // Adjust the viewport based on geometry changes,
        // such as screen rotation
        GLES20.glViewport(0, 0, width, height)
        ratio = width.toFloat() / height
        // Log.v(TAG,"SETTING RATIO "+_ratio);
        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        // Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
        android.opengl.Matrix.orthoM(mProjMatrix, 0, -1f, 1f, -1 / ratio, 1 / ratio, -1f, 1f)
        // printMat(mProjMatrix,"ORTHO");
    }

    companion object {
        private const val TAG = "BSRemoteGLRenderer"

        // Get a new texture id:
        private fun newTextureID(): Int {
            val temp = IntArray(1)
            GLES20.glGenTextures(1, temp, 0)
            return temp[0]
        }

        fun loadShader(type: Int, shaderCode: String?): Int {

            // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
            // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
            val shader = GLES20.glCreateShader(type)

            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }

        /**
         * Utility method for debugging OpenGL calls. Provide the name of the call just after making it:
         *
         * <pre>
         * mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
         * MyGLRenderer.checkGlError("glGetUniformLocation");</pre>
         *
         *
         * If the operation is not successful, the check throws an error.
         *
         * @param glOperation - Name of the OpenGL call to check.
         */
        fun checkGlError(glOperation: String) {
            var error: Int
            if (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "$glOperation: glError $error")
                throw RuntimeException("$glOperation: glError $error")
            }
        }
    }
}